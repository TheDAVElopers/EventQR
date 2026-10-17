-- Print IDs permission: the backend now enforces event_staff_assignments.can_print_id on the three print routes
-- (print-id, reprint-id, print-id-batch). Before that, any actively assigned staff member could print, because the
-- switch only persisted a flag that was never read. Most live active assignments therefore still carry
-- can_print_id = false while their holders have been printing all along.
--
-- This migration preserves today's behaviour for assignments that already exist: every active assignment is
-- backfilled to can_print_id = true, so nobody who could print before this change loses that ability on deploy.
-- New assignments are untouched: the organizer still sets can_print_id explicitly (default false, or overridden by
-- the "Print IDs" switch), and this migration only runs once.
--
-- Scope: only rows that exist at migration time AND are active. Inactive assignments (active = false) keep
-- can_print_id as-is; reactivating one later does not silently restore print rights, the organizer decides again.
--
-- Idempotent: the WHERE clause only matches rows still false, so re-running is a no-op. The migration is a single
-- UPDATE, no other column or business-logic change.
--
-- Impact on current production data (read-only count on the EventQR Supabase project): event_staff_assignments has
-- 20 rows, 19 active, of which 2 already have can_print_id = true and 17 are false. This migration updates those
-- exactly 17 active rows; the 1 inactive row is left untouched. The table is tiny, so the UPDATE is instantaneous.
--
-- Rollback (manual; Flyway Community has no undo):
--   UPDATE public.event_staff_assignments SET can_print_id = false WHERE active = true AND can_print_id = true;
--   DELETE FROM flyway_schema_history WHERE version = '44';
-- That cannot distinguish rows this migration set from rows the organizer had already enabled, so it is only a
-- rough revert. Prefer switching the flag per staff member through the organizer UI.

UPDATE public.event_staff_assignments
SET can_print_id = true
WHERE active = true
  AND can_print_id = false;
