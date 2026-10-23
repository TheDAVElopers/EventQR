package com.thedavelopers.eventqr.features.monitor.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.model.MonitorEventsResponse;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventRecorder;

/**
 * Read-only feed of recent database operations for the log viewer. Reads the in-memory buffer only (no database
 * access). Authenticated by {@code MonitorTokenFilter} in a dedicated security chain, not by user JWTs.
 */
@RestController
@RequestMapping("/api/v1/monitor")
@MonitorEnabled
public class MonitorController {

    private final CrudEventRecorder recorder;

    public MonitorController(CrudEventRecorder recorder) {
        this.recorder = recorder;
    }

    @GetMapping("/events")
    public ResponseEntity<MonitorEventsResponse> events(
            @RequestParam(name = "after", defaultValue = "0") long after,
            @RequestParam(name = "limit", defaultValue = "" + CrudEventRecorder.DEFAULT_LIMIT) int limit) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recorder.read(after, limit));
    }
}
