package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import com.thedavelopers.eventqr.features.monitor.model.CrudEvent;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventCapture;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventRecorder;

class CrudEventCaptureReadRedactionTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("reads on person-related tables carry no row count; other tables keep theirs")
    void rowCountIsRedactedForPersonTables() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/things");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        CrudEventRecorder recorder = new CrudEventRecorder(50);
        CrudEventCapture capture = new CrudEventCapture(recorder, 1000);

        for (String table : new String[] { "user_profiles", "event_registrations", "qr_credentials" }) {
            capture.recordRead(table, "SELECT " + table, 7, true);
            capture.recordRead(table, "SELECT " + table, 0, false);
        }
        capture.recordRead("events", "SELECT events", 7, true);

        assertThat(CrudEventCapture.NO_ROW_COUNT_TABLES)
                .containsExactlyInAnyOrder("user_profiles", "event_registrations", "qr_credentials");
        var events = recorder.read(0, 50).events();
        assertThat(events).hasSize(7);
        assertThat(events.subList(0, 6)).extracting(CrudEvent::rowCount).containsOnlyNulls();
        assertThat(events.get(6).rowCount()).isEqualTo(7);
    }
}
