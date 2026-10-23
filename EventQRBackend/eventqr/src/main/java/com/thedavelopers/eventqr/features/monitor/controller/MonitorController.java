package com.thedavelopers.eventqr.features.monitor.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.thedavelopers.eventqr.features.monitor.config.MonitorEnabled;
import com.thedavelopers.eventqr.features.monitor.config.MonitorSettings;
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
    private final int defaultLimit;
    private final int maxLimit;

    /** In public mode the page size is capped lower, to bound the cost of anonymous polling. */
    public MonitorController(CrudEventRecorder recorder,
                             @Value("${app.monitor.public:false}") String publicSetting) {
        this.recorder = recorder;
        boolean publicMode = MonitorSettings.isPublic(publicSetting);
        this.defaultLimit = publicMode ? CrudEventRecorder.PUBLIC_DEFAULT_LIMIT : CrudEventRecorder.DEFAULT_LIMIT;
        this.maxLimit = publicMode ? CrudEventRecorder.PUBLIC_MAX_LIMIT : CrudEventRecorder.MAX_LIMIT;
    }

    @GetMapping("/events")
    public ResponseEntity<MonitorEventsResponse> events(
            @RequestParam(name = "after", defaultValue = "0") long after,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int effective = limit == null ? defaultLimit : Math.max(1, Math.min(limit, maxLimit));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recorder.read(after, effective));
    }
}
