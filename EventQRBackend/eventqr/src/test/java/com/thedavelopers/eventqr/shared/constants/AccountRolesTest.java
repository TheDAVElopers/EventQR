package com.thedavelopers.eventqr.shared.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccountRolesTest {

    @Test
    void rankOrdersRolesFromLowestToHighest() {
        assertTrue(AccountRoles.rank(AccountRole.ATTENDEE) < AccountRoles.rank(AccountRole.STAFF));
        assertTrue(AccountRoles.rank(AccountRole.STAFF) < AccountRoles.rank(AccountRole.ORGANIZER));
        assertTrue(AccountRoles.rank(AccountRole.ORGANIZER) < AccountRoles.rank(AccountRole.ADMIN));
        assertTrue(AccountRoles.rank(AccountRole.ADMIN) < AccountRoles.rank(AccountRole.SUPER_ADMIN));
    }

    @Test
    void rankDoesNotUseEnumDeclarationOrder() {
        // The enum declares ORGANIZER before STAFF; rank must still place STAFF below ORGANIZER.
        assertEquals(1, AccountRoles.rank(AccountRole.STAFF));
        assertEquals(2, AccountRoles.rank(AccountRole.ORGANIZER));
        assertTrue(AccountRoles.rank(AccountRole.STAFF) < AccountRoles.rank(AccountRole.ORGANIZER));
    }

    @Test
    void isAtLeastComparesRanks() {
        assertTrue(AccountRoles.isAtLeast(AccountRole.ADMIN, AccountRole.ADMIN));
        assertTrue(AccountRoles.isAtLeast(AccountRole.SUPER_ADMIN, AccountRole.ADMIN));
        assertTrue(AccountRoles.isAtLeast(AccountRole.ORGANIZER, AccountRole.STAFF));
        assertFalse(AccountRoles.isAtLeast(AccountRole.ORGANIZER, AccountRole.ADMIN));
        assertFalse(AccountRoles.isAtLeast(AccountRole.STAFF, AccountRole.ORGANIZER));
        assertFalse(AccountRoles.isAtLeast(AccountRole.ATTENDEE, AccountRole.STAFF));
    }

    @Test
    void isAtLeastRejectsUnknownRoles() {
        assertFalse(AccountRoles.isAtLeast(null, AccountRole.ATTENDEE));
        assertFalse(AccountRoles.isAtLeast(null, null));
    }

    @Test
    void isAnyOfMatchesExactRoles() {
        assertTrue(AccountRoles.isAnyOf(AccountRole.ORGANIZER, AccountRole.ORGANIZER, AccountRole.ADMIN));
        assertFalse(AccountRoles.isAnyOf(AccountRole.STAFF, AccountRole.ADMIN, AccountRole.SUPER_ADMIN));
        assertFalse(AccountRoles.isAnyOf(null, AccountRole.ADMIN));
    }
}
