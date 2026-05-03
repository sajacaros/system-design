package kr.study.monitoring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Load", description = "Grafana 대시보드의 그래프 변화를 관찰하기 위한 CPU·메모리 부하 엔드포인트")
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
    @Operation(summary = "CPU 부하 발생",
            description = "지정한 스레드 수만큼 빈 루프를 돌려 CPU를 점유합니다. Grafana의 Process CPU Usage 패널에서 변화를 관찰하세요. (max " + MAX_CPU_THREADS + " threads, " + MAX_CPU_SECONDS + "s)")
    public Map<String, Object> cpu(
            @Parameter(description = "부하 지속 시간 (초)", example = "30")
            @RequestParam(defaultValue = "10") int seconds,
            @Parameter(description = "동시 점유 스레드 수", example = "4")
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
    @Operation(summary = "메모리 부하 발생",
            description = "지정 크기의 byte[] 블록을 할당해 보유합니다. seconds 후 자동 해제. Grafana의 JVM Heap Memory 패널에서 used 라인이 점프하는 모습을 관찰하세요. (max " + MAX_MEMORY_MB + " MB, " + MAX_MEMORY_SECONDS + "s)")
    public Map<String, Object> memory(
            @Parameter(description = "할당할 메모리 크기 (MB)", example = "200")
            @RequestParam(defaultValue = "100") int mb,
            @Parameter(description = "보유 시간 후 자동 해제 (초)", example = "60")
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
    @Operation(summary = "보유 중인 메모리 즉시 해제",
            description = "현재 보유한 모든 메모리 블록을 비우고 System.gc()를 호출합니다. Grafana 그래프에서 heap used가 즉시 떨어지는 모습을 관찰하세요.")
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
