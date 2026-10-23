package com.thedavelopers.monitorprobe;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Test-only entity mapped to an id-redacted table name ({@code user_profiles}). */
@Entity
@Table(name = "user_profiles")
public class RedactedProbe {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    protected RedactedProbe() {
    }

    public RedactedProbe(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
