package com.thedavelopers.eventqr.features.monitor.service;

import java.time.Instant;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;
import com.thedavelopers.eventqr.features.monitor.model.CrudResult;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;

/**
 * Appends {@link HibernateCrudEventListener} to the Hibernate SessionFactory's post-insert/update/delete
 * listener groups once the EntityManagerFactory exists. Appending keeps Hibernate's own listeners intact.
 */
@Component
@MonitorEnabled
public class HibernateCrudListenerRegistrar {

    public static final String SYSTEM_TABLE = "(monitor)";
    public static final String UNAVAILABLE_DESCRIPTION = "write capture unavailable";

    private static final Logger log = LoggerFactory.getLogger(HibernateCrudListenerRegistrar.class);

    private final EntityManagerFactory entityManagerFactory;
    private final CrudEventCapture capture;
    private final CrudEventRecorder recorder;

    public HibernateCrudListenerRegistrar(EntityManagerFactory entityManagerFactory, CrudEventCapture capture,
                                          CrudEventRecorder recorder) {
        this.entityManagerFactory = entityManagerFactory;
        this.capture = capture;
        this.recorder = recorder;
    }

    @PostConstruct
    public void register() {
        try {
            SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            EventListenerRegistry registry = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
            HibernateCrudEventListener listener = new HibernateCrudEventListener(capture);
            registry.appendListeners(EventType.POST_INSERT, listener);
            registry.appendListeners(EventType.POST_UPDATE, listener);
            registry.appendListeners(EventType.POST_DELETE, listener);
        } catch (Exception e) {
            // Monitoring is optional: never fail application startup because of it. But do not fail silently
            // either: put one visible system event in the feed so the viewer shows that writes are not captured.
            log.warn("CRUD monitor could not register Hibernate listeners: {}", e.getClass().getSimpleName());
            recorder.record(Instant.now(), CrudOperation.READ, SYSTEM_TABLE, null, CrudResult.FAILURE,
                    UNAVAILABLE_DESCRIPTION, null, null, null);
        }
    }
}
