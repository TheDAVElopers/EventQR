package com.thedavelopers.eventqr.features.idprinting.model.entity;

import java.util.UUID;

import com.thedavelopers.eventqr.shared.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "id_templates")
public class IdTemplate extends BaseEntity {

    @Column(nullable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 4000)
    private String templateJson;
}