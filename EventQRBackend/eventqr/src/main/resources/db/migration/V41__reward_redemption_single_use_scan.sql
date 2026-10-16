-- DB backstop for single-use redemption scans. RewardRedemptionService.redeem row-locks the scan log and rejects a
-- scan that already has a REDEEMED redemption. This index makes a second REDEEMED row for the same scan
-- impossible even if a code path skips that check. A violation surfaces as DataIntegrityViolationException -> 409.
-- REJECTED/PENDING rows are excluded on purpose: a business rejection (not enough points, out of stock) does not
-- consume the scan.
--
-- Data guard: the index cannot be built while a scan log is linked to more than one REDEEMED row. Production had 0
-- such groups when this was written. Other environments (local, dev) could have them from before the guard
-- existed. In that case the earliest REDEEMED row keeps the link, and each later duplicate is detached
-- (redemption_scan_log_id = NULL). The detached scan id is written into `reason` so the replay evidence is not
-- lost. Points, status and the ledger are untouched. Unlike V17, this does not skip the index on dirty data,
-- because a backstop that is silently missing protects nothing.
--
-- Idempotent: the cleanup matches nothing once the index exists, and the index uses IF NOT EXISTS.
-- The table is small, so a plain (non-CONCURRENT) build inside Flyway's transaction holds its SHARE lock only briefly.

DO $$
DECLARE
    detached integer;
BEGIN
    WITH ranked AS (
        SELECT id,
               redemption_scan_log_id,
               row_number() OVER (PARTITION BY redemption_scan_log_id
                                  ORDER BY coalesce(redeemed_at, created_at), created_at, id) AS rn
        FROM public.reward_redemptions
        WHERE status = 'REDEEMED'
          AND redemption_scan_log_id IS NOT NULL
    )
    UPDATE public.reward_redemptions r
    SET reason = left(
            coalesce(r.reason || ' ', '')
                || '[V41: detached duplicate use of redemption scan ' || ranked.redemption_scan_log_id::text || ']',
            2000),
        redemption_scan_log_id = NULL,
        updated_at = now()
    FROM ranked
    WHERE r.id = ranked.id
      AND ranked.rn > 1;

    GET DIAGNOSTICS detached = ROW_COUNT;
    IF detached > 0 THEN
        RAISE WARNING 'V41: detached % duplicate REDEEMED reward_redemptions row(s) from their redemption scan log', detached;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_reward_redemptions_scan_log_redeemed
    ON public.reward_redemptions (redemption_scan_log_id)
    WHERE status = 'REDEEMED' AND redemption_scan_log_id IS NOT NULL;

-- Rollback (manual; Flyway Community has no undo):
--   DROP INDEX IF EXISTS public.ux_reward_redemptions_scan_log_redeemed;
--   DELETE FROM flyway_schema_history WHERE version = '41';
-- Detached duplicates (if any) can be found with: SELECT * FROM reward_redemptions WHERE reason LIKE '%[V41:%';
