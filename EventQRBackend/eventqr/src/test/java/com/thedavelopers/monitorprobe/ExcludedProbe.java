package com.thedavelopers.monitorprobe;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Test-only entity mapped to an EXCLUDED table name; it must never be reported. */
@Entity
@Table(name = "refresh_token")
public class ExcludedProbe {

    @Id
    @GeneratedValue
    private UUID id;

    private String tokenHash;

    protected ExcludedProbe() {
    }

    public ExcludedProbe(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
}
