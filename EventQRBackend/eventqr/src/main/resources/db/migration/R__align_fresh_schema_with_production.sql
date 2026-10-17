-- FROZEN: do not edit or delete. Any change re-runs this file on production.
--
-- Removes the two objects that only an EMPTY database ever gets, so a fresh install and a
-- disaster-recovery restore end up shaped exactly like production.
--
-- (a) Why they exist only on a fresh database: V17 and V18 create each index/constraint only if the
--     rows already present would satisfy it ("skip if a legacy-drifted database would violate it").
--     Live databases fail that check, so production has never carried either object; an empty
--     database passes the check trivially and gets both, making a fresh build *stricter* than
--     production.
-- (b) Why they must go: the stricter fresh schema breaks reward redemption with SQLSTATE 23503 or
--     23505. point_transactions.source_transaction_id is a loose audit reference, not a
--     transaction_logs FK: RewardService.redeem / RewardRedemptionService.redeem store the
--     reward_redemptions id and RewardService.assignPoints / deductPoints store a synthetic UUID,
--     and a random UUID can never satisfy an FK. The redemption flow also writes an APPROVED
--     REWARD_REDEMPTION outcome row that shares (registration_id, scan_purpose_id) with the
--     APPROVED REWARD_REDEMPTION_SCAN it was recorded from, so a second APPROVED row per
--     (registration, purpose) is the normal case, not an anomaly. Removing both restores parity and
--     unblocks redemption on fresh installs.
-- (c) Why repeatable rather than versioned: a repeatable migration carries no version. It runs after every
--     versioned migration and re-runs whenever its checksum changes, so it cannot open a version gap. This file
--     was introduced while production was still at V41 (V42-V45 have since merged to main); a versioned file added
--     below the applied maximum would fail the next production deploy with "Detected resolved migration not applied
--     to database" under spring.flyway.out-of-order=false. The repeatable form avoids that trap permanently.
-- (d) Rollback (manual; Flyway Community has no undo):
--       ALTER TABLE public.point_transactions
--           ADD CONSTRAINT fk_point_transactions_source
--           FOREIGN KEY (source_transaction_id) REFERENCES public.transaction_logs(id);
--       CREATE UNIQUE INDEX uq_txn_logs_approved_reg_purpose
--           ON public.transaction_logs(registration_id, scan_purpose_id)
--           WHERE transaction_result = 'APPROVED';
-- (e) The two names are retired, never reused: an older production database still records them in its migration
--     history and may still carry the objects, so neither name may ever be reused for a new object.
--
-- Both statements are no-ops where the object does not exist, so live databases migrate unchanged.

ALTER TABLE public.point_transactions
    DROP CONSTRAINT IF EXISTS fk_point_transactions_source;

DROP INDEX IF EXISTS public.uq_txn_logs_approved_reg_purpose;
