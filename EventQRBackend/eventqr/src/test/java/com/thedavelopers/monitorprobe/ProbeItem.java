package com.thedavelopers.monitorprobe;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Test-only entity, deliberately outside com.thedavelopers.eventqr so the application entity scan never sees it. (H2) with a "sensitive looking" value that must never appear in monitor events. */
@Entity
@Table(name = "monitor_probe_items")
public class ProbeItem {

    @Id
    @GeneratedValue
    private UUID id;

    private String secretValue;

    private String label;

    private String status;

    protected ProbeItem() {
    }

    public ProbeItem(String secretValue, String status) {
        this.secretValue = secretValue;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getSecretValue() {
        return secretValue;
    }

    public void setSecretValue(String secretValue) {
        this.secretValue = secretValue;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
