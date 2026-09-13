-- Event-rejection notifications are created before any linked event exists.
-- Make notifications.event_id nullable to support that case.
ALTER TABLE notifications
    ALTER COLUMN event_id DROP NOT NULL;