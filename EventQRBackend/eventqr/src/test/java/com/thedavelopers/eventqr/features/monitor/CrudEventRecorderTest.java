package com.thedavelopers.eventqr.features.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.thedavelopers.eventqr.features.monitor.model.CrudEvent;
import com.thedavelopers.eventqr.features.monitor.model.CrudOperation;
import com.thedavelopers.eventqr.features.monitor.model.CrudResult;
import com.thedavelopers.eventqr.features.monitor.model.MonitorEventsResponse;
import com.thedavelopers.eventqr.features.monitor.service.CrudEventRecorder;

class CrudEventRecorderTest {

    private static void add(CrudEventRecorder recorder, int count) {
        for (int i = 0; i < count; i++) {
            recorder.record(Instant.now(), CrudOperation.READ, "t" + i, null, CrudResult.SUCCESS, "d", "GET /x", null, 1);
        }
    }

    private static List<Long> seqs(MonitorEventsResponse response) {
        return response.events().stream().map(CrudEvent::seq).toList();
    }

    @Test
    @DisplayName("sequence starts at 1 and increases by one")
    void sequenceIsMonotonic() {
        CrudEventRecorder recorder = new CrudEventRecorder(10);
        assertThat(recorder.latestSeq()).isZero();
        add(recorder, 3);
        MonitorEventsResponse response = recorder.read(0, 200);
        assertThat(seqs(response)).containsExactly(1L, 2L, 3L);
        assertThat(response.latestSeq()).isEqualTo(3);
        assertThat(response.bufferSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("the oldest events are evicted once the buffer is full")
    void evictsOldest() {
        CrudEventRecorder recorder = new CrudEventRecorder(3);
        add(recorder, 5);
        MonitorEventsResponse response = recorder.read(0, 200);
        assertThat(seqs(response)).containsExactly(3L, 4L, 5L);
        assertThat(response.latestSeq()).isEqualTo(5);
        assertThat(recorder.read(1, 200).events()).extracting(CrudEvent::seq).containsExactly(3L, 4L, 5L);
    }

    @Test
    @DisplayName("after returns only newer events, ascending; after beyond latest returns none")
    void afterSemantics() {
        CrudEventRecorder recorder = new CrudEventRecorder(10);
        add(recorder, 5);
        assertThat(seqs(recorder.read(3, 200))).containsExactly(4L, 5L);
        assertThat(recorder.read(5, 200).events()).isEmpty();
        MonitorEventsResponse beyond = recorder.read(99, 200);
        assertThat(beyond.events()).isEmpty();
        assertThat(beyond.latestSeq()).isEqualTo(5);
        assertThat(seqs(recorder.read(-7, 200))).hasSize(5);
    }

    @Test
    @DisplayName("after is clamped to [0, latestSeq]: no overflow with extreme values")
    void afterIsClamped() {
        CrudEventRecorder recorder = new CrudEventRecorder(10);
        add(recorder, 4);
        MonitorEventsResponse max = recorder.read(Long.MAX_VALUE, 200);
        assertThat(max.events()).isEmpty();
        assertThat(max.latestSeq()).isEqualTo(4);
        assertThat(recorder.read(Long.MIN_VALUE, 200).events()).hasSize(4);
        assertThat(recorder.read(Long.MAX_VALUE - 1, Integer.MAX_VALUE).events()).isEmpty();
        assertThat(new CrudEventRecorder(10).read(Long.MAX_VALUE, 1).events()).isEmpty();
    }

    @Test
    @DisplayName("limit caps the page (oldest first) and is clamped to 1..500")
    void limitSemantics() {
        CrudEventRecorder recorder = new CrudEventRecorder(1000);
        add(recorder, 700);
        assertThat(seqs(recorder.read(0, 2))).containsExactly(1L, 2L);
        assertThat(recorder.read(0, 0).events()).hasSize(1);
        assertThat(recorder.read(0, -5).events()).hasSize(1);
        assertThat(recorder.read(0, 100_000).events()).hasSize(CrudEventRecorder.MAX_LIMIT);
    }

    @Test
    @DisplayName("bootId is stable for one recorder and differs between instances")
    void bootId() {
        CrudEventRecorder a = new CrudEventRecorder(5);
        CrudEventRecorder b = new CrudEventRecorder(5);
        assertThat(a.bootId()).isNotBlank().isEqualTo(a.read(0, 1).bootId()).isEqualTo(a.bootId());
        assertThat(a.bootId()).isNotEqualTo(b.bootId());
    }

    @Test
    @DisplayName("buffer size is bounded to a sane range")
    void bufferSizeIsBounded() {
        assertThat(new CrudEventRecorder(0).bufferSize()).isEqualTo(1);
        assertThat(new CrudEventRecorder(10_000_000).bufferSize()).isEqualTo(CrudEventRecorder.MAX_BUFFER_SIZE);
    }

    @Test
    @DisplayName("concurrent writers never produce duplicate or skipped sequence numbers")
    void concurrentRecording() throws Exception {
        CrudEventRecorder recorder = new CrudEventRecorder(5000);
        int threads = 8;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.execute(() -> {
                add(recorder, perThread);
                done.countDown();
            });
        }
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(recorder.latestSeq()).isEqualTo((long) threads * perThread);
        List<CrudEvent> all = recorder.read(0, CrudEventRecorder.MAX_LIMIT).events();
        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i).seq()).isEqualTo(all.get(i - 1).seq() + 1);
        }
    }
}
