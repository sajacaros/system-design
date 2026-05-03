package kr.study.monitoring.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/load")
public class LoadController {

    private static final Logger log = LoggerFactory.getLogger(LoadController.class);

    private static final int MAX_CPU_THREADS = 32;
    private static final int MAX_CPU_SECONDS = 120;
    private static final int MAX_MEMORY_MB = 1024;
    private static final int MAX_MEMORY_SECONDS = 600;

    private final ExecutorService cpuExecutor = Executors.newFixedThreadPool(MAX_CPU_THREADS);

    private final ScheduledExecutorService releaseScheduler = Executors.newSingleThreadScheduledExecutor();

    private final List<byte[]> memoryHolder = Collections.synchronizedList(new java.util.ArrayList<>());

    @GetMapping("/cpu")
    public Map<String, Object> cpu(@RequestParam(defaultValue = "10") int seconds,
                                   @RequestParam(defaultValue = "1") int threads) {
        int s = clamp(seconds, 1, MAX_CPU_SECONDS);
        int t = clamp(threads, 1, MAX_CPU_THREADS);
        long endAt = System.currentTimeMillis() + s * 1000L;
        for (int i = 0; i < t; i++) {
            cpuExecutor.execute(() -> burnCpu(endAt));
        }
        log.info("CPU load started: {} threads for {} seconds", t, s);
        return Map.of(
                "endpoint", "/load/cpu",
                "threads", t,
                "seconds", s
        );
    }

    @PostMapping("/memory")
    public Map<String, Object> memory(@RequestParam(defaultValue = "100") int mb,
                                      @RequestParam(defaultValue = "30") int seconds) {
        int sizeMb = clamp(mb, 1, MAX_MEMORY_MB);
        int holdSec = clamp(seconds, 1, MAX_MEMORY_SECONDS);

        byte[] block = new byte[sizeMb * 1024 * 1024];
        // Touch each page so the JVM actually commits memory (otherwise it may stay zero-filled / lazy).
        for (int i = 0; i < block.length; i += 4096) {
            block[i] = 1;
        }
        memoryHolder.add(block);
        log.info("Memory allocated: {} MB, will release in {} seconds (total blocks now: {})",
                sizeMb, holdSec, memoryHolder.size());

        releaseScheduler.schedule(() -> {
            memoryHolder.remove(block);
            log.info("Memory released: {} MB (remaining blocks: {})", sizeMb, memoryHolder.size());
        }, holdSec, TimeUnit.SECONDS);

        return Map.of(
                "endpoint", "/load/memory",
                "allocated_mb", sizeMb,
                "hold_seconds", holdSec,
                "current_blocks", memoryHolder.size(),
                "approx_held_mb", approxHeldMb()
        );
    }

    @PostMapping("/memory/release")
    public Map<String, Object> release() {
        int blocks = memoryHolder.size();
        long mb = approxHeldMb();
        memoryHolder.clear();
        System.gc();
        log.info("Manually released all blocks: {} blocks (~{} MB)", blocks, mb);
        return Map.of(
                "endpoint", "/load/memory/release",
                "released_blocks", blocks,
                "released_approx_mb", mb
        );
    }

    private void burnCpu(long endAt) {
        double sink = 0;
        while (System.currentTimeMillis() < endAt) {
            for (int i = 0; i < 10000; i++) {
                sink += Math.atan(Math.sqrt(i + sink));
            }
        }
        // Defeat dead-code elimination so the loop is not optimized away.
        if (sink == Double.NEGATIVE_INFINITY) {
            log.debug("unreachable");
        }
    }

    private long approxHeldMb() {
        long bytes = 0;
        synchronized (memoryHolder) {
            for (byte[] b : memoryHolder) bytes += b.length;
        }
        return bytes / (1024 * 1024);
    }

    private static int clamp(int v, int min, int max) {
        return Math.min(max, Math.max(min, v));
    }

    @PreDestroy
    void shutdown() {
        cpuExecutor.shutdownNow();
        releaseScheduler.shutdownNow();
    }
}
