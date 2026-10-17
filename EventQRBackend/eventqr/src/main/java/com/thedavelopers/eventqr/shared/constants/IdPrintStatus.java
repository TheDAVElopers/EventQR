package com.thedavelopers.eventqr.shared.constants;

/**
 * Lifecycle of an id_print_logs row (V42). A print request inserts PENDING; the client then confirms the outcome
 * of the print dialog as PRINTED, CANCELLED or FAILED. Only PRINTED counts as "this attendee has a printed ID".
 */
public enum IdPrintStatus {
    PENDING,
    PRINTED,
    CANCELLED,
    FAILED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
