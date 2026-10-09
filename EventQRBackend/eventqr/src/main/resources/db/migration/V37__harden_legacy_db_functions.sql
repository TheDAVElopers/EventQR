-- Supabase security advisors: legacy RLS policies and helper functions were reachable by the PostgREST roles,
-- and three functions had a mutable search_path.
--
-- These functions exist on the production database (created before the V16 baseline) but NOT on a database
-- built from V16 onward, and the anon/authenticated roles exist only on Supabase. Every statement is therefore
-- guarded so this migration is a no-op wherever a function or role is missing (plain Postgres, tests, local).
--
-- Safe for the app: it connects as the database owner role and never calls these functions (no Java, client,
-- or PostgREST/RPC usage); revoking from anon/authenticated/PUBLIC leaves the owner's own EXECUTE in place.
-- The trigger functions keep their EXECUTE grants; only their search_path is pinned.

-- Legacy PostgREST policies (all for role authenticated): "test_select_all" exposed every event_requests row to
-- any authenticated Supabase user, and the admin_select_* policies call is_admin(). The app never uses
-- PostgREST/Supabase Auth, so they are dropped first. RLS stays enabled on these tables; with no policies it
-- denies anon/authenticated entirely, while the owner role the backend connects as is unaffected.
DO $$
DECLARE
    pol record;
BEGIN
    FOR pol IN
        SELECT * FROM (VALUES
            ('public.event_requests', 'test_select_all'),
            ('public.event_requests', 'admin_select_event_requests'),
            ('public.user_profiles',  'admin_select_user_profiles'),
            ('public.audit_logs',     'admin_select_audit_logs'),
            ('public.events',         'admin_select_events')
        ) AS p(table_name, policy_name)
    LOOP
        IF to_regclass(pol.table_name) IS NOT NULL THEN
            EXECUTE format('DROP POLICY IF EXISTS %I ON %s', pol.policy_name, pol.table_name);
        END IF;
    END LOOP;
END
$$;

DO $$
DECLARE
    fn   text;
    role_name text;
BEGIN
    FOREACH fn IN ARRAY ARRAY[
        'public.can_manage_event(uuid)',
        'public.can_operate_event(uuid)',
        'public.current_user_role()',
        'public.is_admin()',
        'public.is_event_organizer(uuid)',
        'public.is_event_staff(uuid)'
    ] LOOP
        IF to_regprocedure(fn) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format('REVOKE EXECUTE ON FUNCTION %s FROM PUBLIC', fn);
        FOREACH role_name IN ARRAY ARRAY['anon', 'authenticated'] LOOP
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name) THEN
                EXECUTE format('REVOKE EXECUTE ON FUNCTION %s FROM %I', fn, role_name);
            END IF;
        END LOOP;
    END LOOP;

    FOREACH fn IN ARRAY ARRAY[
        'public.normalize_transaction_rule_duplicate_settings()',
        'public.is_admin()',
        'public.set_updated_at()'
    ] LOOP
        IF to_regprocedure(fn) IS NOT NULL THEN
            EXECUTE format('ALTER FUNCTION %s SET search_path = public', fn);
        END IF;
    END LOOP;
END
$$;
