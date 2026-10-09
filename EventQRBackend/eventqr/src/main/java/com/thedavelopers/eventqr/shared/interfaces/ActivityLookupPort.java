package com.thedavelopers.eventqr.shared.interfaces;

import java.util.UUID;

/**
 * Owned by registrations, implemented by the modules that record attendee activity (transactions: approved scans,
 * rewards: point transactions and redemptions). Lets registrations ask "has anything been recorded for this
 * attendee?" without depending on those modules' repositories.
 */
public interface ActivityLookupPort {

    boolean hasRecordedActivity(UUID eventId, UUID attendeeUserId, UUID registrationId);
}
