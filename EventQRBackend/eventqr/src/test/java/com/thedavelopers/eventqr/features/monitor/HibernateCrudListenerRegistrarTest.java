package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.monitor.model.CrudEvent;
import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;
import com.thedavelopers.eventqr.features.monitor.model.CrudResult;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventCapture;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventRecorder;
import com.thedavelopers.eventqr.features.monitor.service.HibernateCrudListenerRegistrar;

import jakarta.persistence.EntityManagerFactory;

class HibernateCrudListenerRegistrarTest {

    @Test
    @DisplayName("when listeners cannot be registered, one visible system event tells the viewer writes are not captured")
    void registrationFailureIsVisible() {
        CrudEventRecorder recorder = new CrudEventRecorder(10);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(emf.unwrap(any())).thenThrow(new IllegalStateException("no hibernate"));

        new HibernateCrudListenerRegistrar(emf, new CrudEventCapture(recorder, 1000), recorder).register();

        assertThat(recorder.read(0, 10).events()).singleElement().satisfies((CrudEvent e) -> {
            assertThat(e.table()).isEqualTo("(monitor)");
            assertThat(e.description()).isEqualTo("write capture unavailable");
            assertThat(e.result()).isEqualTo(CrudResult.FAILURE);
            assertThat(e.operation()).isEqualTo(CrudOperation.READ);
            assertThat(e.entityId()).isNull();
        });
    }
}
