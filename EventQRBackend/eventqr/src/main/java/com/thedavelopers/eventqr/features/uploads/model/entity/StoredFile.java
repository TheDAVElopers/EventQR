package com.thedavelopers.eventqr.features.uploads.model.entity;

import java.time.Instant;
import java.util.UUID;

import com.thedavelopers.eventqr.shared.utils.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Basic;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "stored_files")
public class StoredFile extends BaseEntity {

    @Column(name = "owner_id")
    private UUID ownerId;

    private String purpose;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(name = "stored_at", nullable = false)
    private Instant storedAt;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;
}
