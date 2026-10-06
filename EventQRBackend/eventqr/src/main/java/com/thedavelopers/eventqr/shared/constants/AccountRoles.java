package com.thedavelopers.eventqr.shared.constants;

public final class AccountRoles {
    private AccountRoles() {}

    public static int rank(AccountRole r) {
        if (r == null) return -1;
        return switch (r) {
            case ATTENDEE -> 0;
            case STAFF -> 1;
            case ORGANIZER -> 2;
            case ADMIN -> 3;
            case SUPER_ADMIN -> 4;
        };
    }

    public static boolean isAtLeast(AccountRole actual, AccountRole min) {
        int a = rank(actual), m = rank(min);
        return a >= 0 && a >= m;
    }

    public static boolean isAnyOf(AccountRole actual, AccountRole... allowed) {
        for (AccountRole a : allowed) if (actual == a) return true;
        return false;
    }
}
