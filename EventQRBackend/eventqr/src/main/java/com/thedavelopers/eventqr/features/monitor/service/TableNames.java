package com.thedavelopers.eventqr.features.monitor.service;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Resolves the database table of a JPA entity class from its {@code @Table(name)} (cached). */
public final class TableNames {

    private static final Map<Class<?>, Optional<String>> CACHE = new ConcurrentHashMap<>();

    private TableNames() {
    }

    public static Optional<String> of(Class<?> entityClass) {
        if (entityClass == null) {
            return Optional.empty();
        }
        return CACHE.computeIfAbsent(entityClass, TableNames::resolve);
    }

    private static Optional<String> resolve(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            Table table = c.getAnnotation(Table.class);
            if (table != null && !table.name().isBlank()) {
                return Optional.of(table.name().replace("\"", "").toLowerCase(Locale.ROOT));
            }
        }
        // An entity without @Table maps to its snake_case name under Spring Boot's default naming strategy.
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(Entity.class)) {
                return Optional.of(c.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT));
            }
        }
        return Optional.empty();
    }
}
