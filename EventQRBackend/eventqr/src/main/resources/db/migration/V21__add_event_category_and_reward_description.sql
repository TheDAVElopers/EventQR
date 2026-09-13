-- V21__add_event_category_and_reward_description.sql
-- Cross-layer audit gap fix: expose `category` on events and `description` on rewards.
--
-- The deployed DB already carries both columns (events.category via a direct ALTER;
-- rewards.description pre-existed from ddl-auto=update drift). V16__baseline_schema.sql
-- predates both, so a fresh environment built only from these migrations would fail
-- spring.jpa.hibernate.ddl-auto=validate.
--
-- ADD COLUMN IF NOT EXISTS is idempotent, so it is safe to run both against the
-- deployed (already-migrated) DB and a freshly created one.

ALTER TABLE events  ADD COLUMN IF NOT EXISTS category    varchar(255);
ALTER TABLE rewards ADD COLUMN IF NOT EXISTS description varchar(2000);