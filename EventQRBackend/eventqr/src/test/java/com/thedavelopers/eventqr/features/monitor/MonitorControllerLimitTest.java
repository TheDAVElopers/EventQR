package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.monitor.controller.MonitorController;
import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;
import com.thedavelopers.eventqr.features.monitor.model.CrudResult;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventRecorder;

class MonitorControllerLimitTest {

    private static CrudEventRecorder recorderWith(int events) {
        CrudEventRecorder recorder = new CrudEventRecorder(1000);
        for (int i = 0; i < events; i++) {
            recorder.record(Instant.now(), CrudOperation.READ, "t", null, CrudResult.SUCCESS, "d", "GET /x", null, 1);
        }
        return recorder;
    }

    private static int size(MonitorController controller, Integer limit) {
        return controller.events(0, limit).getBody().events().size();
    }

    @Test
    @DisplayName("private mode: default 200, maximum 500")
    void privateMode() {
        MonitorController controller = new MonitorController(recorderWith(700), "false");
        assertThat(size(controller, null)).isEqualTo(200);
        assertThat(size(controller, 1000)).isEqualTo(500);
        assertThat(size(controller, 50)).isEqualTo(50);
        assertThat(size(controller, 0)).isEqualTo(1);
        assertThat(size(controller, -3)).isEqualTo(1);
    }

    @Test
    @DisplayName("public mode: default 100, maximum 100")
    void publicMode() {
        MonitorController controller = new MonitorController(recorderWith(700), " TRUE ");
        assertThat(size(controller, null)).isEqualTo(100);
        assertThat(size(controller, 1000)).isEqualTo(100);
        assertThat(size(controller, Integer.MAX_VALUE)).isEqualTo(100);
        assertThat(size(controller, 5)).isEqualTo(5);
        assertThat(size(controller, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("an unrecognised public setting behaves as private mode")
    void unrecognisedSettingIsPrivate() {
        MonitorController controller = new MonitorController(recorderWith(700), "ture");
        assertThat(size(controller, null)).isEqualTo(200);
        assertThat(size(controller, 1000)).isEqualTo(500);
    }
}
