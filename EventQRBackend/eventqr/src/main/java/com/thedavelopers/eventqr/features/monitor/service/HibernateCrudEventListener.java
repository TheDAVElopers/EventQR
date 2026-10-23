package com.thedavelopers.eventqr.features.monitor.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;

/**
 * Reports entity inserts, updates and deletes flushed by Hibernate to {@link CrudEventCapture}. Only the table,
 * the identifier and (for updates) the NAMES of changed properties are read; property values are never read
 * into an event. Registered by {@link HibernateCrudListenerRegistrar}; failures are swallowed (debug log).
 */
public class HibernateCrudEventListener
        implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    private static final long serialVersionUID = 1L;
    private static final int MAX_LISTED_PROPERTIES = 10;
    private static final Logger log = LoggerFactory.getLogger(HibernateCrudEventListener.class);

    private final transient CrudEventCapture capture;

    public HibernateCrudEventListener(CrudEventCapture capture) {
        this.capture = capture;
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        handle(CrudOperation.CREATE, event.getPersister(), event.getId(), "INSERT ", null);
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        try {
            if (!capture.isCapturing()) {
                return;
            }
            handle(CrudOperation.UPDATE, event.getPersister(), event.getId(), "UPDATE ", changedProperties(event));
        } catch (Exception e) {
            log.debug("monitor update listener failed", e);
        }
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        handle(CrudOperation.DELETE, event.getPersister(), event.getId(), "DELETE ", null);
    }

    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        // Fire at flush time; the transaction outcome is attached by CrudEventCapture's synchronization.
        return false;
    }

    private void handle(CrudOperation operation, EntityPersister persister, Object id, String verb,
                        String changed) {
        try {
            if (!capture.isCapturing()) {
                return;
            }
            String table = TableNames.of(persister.getMappedClass()).orElse(null);
            if (table == null) {
                return;
            }
            String description = verb + table + (changed == null || changed.isEmpty() ? "" : " (changed: " + changed + ")");
            capture.recordWrite(operation, table, id == null ? null : String.valueOf(id), description, 1);
        } catch (Exception e) {
            log.debug("monitor listener failed", e);
        }
    }

    /** Comma-separated names of changed properties (sensitive names masked); values are compared but never retained. */
    private static String changedProperties(PostUpdateEvent event) {
        String[] names = event.getPersister().getPropertyNames();
        int[] dirty = event.getDirtyProperties();
        Set<String> changed = new LinkedHashSet<>();
        if (dirty != null && dirty.length > 0) {
            for (int index : dirty) {
                if (index >= 0 && index < names.length) {
                    changed.add(SensitiveNames.display(names[index]));
                }
            }
        } else if (event.getOldState() != null && event.getState() != null) {
            Object[] before = event.getOldState();
            Object[] after = event.getState();
            for (int i = 0; i < names.length && i < before.length && i < after.length; i++) {
                if (!Objects.deepEquals(before[i], after[i])) {
                    changed.add(SensitiveNames.display(names[i]));
                }
            }
        }
        List<String> shown = new ArrayList<>(changed);
        if (shown.size() > MAX_LISTED_PROPERTIES) {
            int more = shown.size() - MAX_LISTED_PROPERTIES;
            shown = new ArrayList<>(shown.subList(0, MAX_LISTED_PROPERTIES));
            shown.add("+" + more + " more");
        }
        return String.join(", ", shown);
    }
}
