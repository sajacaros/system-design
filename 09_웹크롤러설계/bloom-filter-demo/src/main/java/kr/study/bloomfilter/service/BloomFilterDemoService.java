package kr.study.bloomfilter.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import kr.study.bloomfilter.domain.BloomFilter;
import org.springframework.stereotype.Service;

@Service
public class BloomFilterDemoService {

    private static final int DEFAULT_BIT_SIZE = 64;
    private static final int DEFAULT_HASH_FUNCTION_COUNT = 3;
    private static final int HISTORY_LIMIT = 10;
    private static final List<String> SEED_URLS = List.of(
        "https://news.example.com/",
        "https://news.example.com/world",
        "https://shop.example.com/products/42",
        "https://blog.example.com/system-design",
        "https://docs.example.com/crawler/robots",
        "https://video.example.com/watch/abc",
        "https://forum.example.com/thread/100"
    );
    private static final List<String> SAMPLE_URLS = List.of(
        "https://news.example.com/world",
        "https://shop.example.com/products/99",
        "https://docs.example.com/crawler/bloom-filter",
        "https://archive.example.com/2026/05/27",
        "https://video.example.com/watch/xyz"
    );

    private BloomFilter filter = new BloomFilter(DEFAULT_BIT_SIZE, DEFAULT_HASH_FUNCTION_COUNT);
    private final Set<String> visitedUrls = new LinkedHashSet<>();
    private final Deque<OperationEvent> history = new ArrayDeque<>();

    public BloomFilterDemoService() {
        seed();
    }

    public synchronized DashboardState dashboard() {
        return state(null);
    }

    public synchronized DashboardState addUrl(String url) {
        String normalizedUrl = normalize(url);
        boolean actuallyVisitedBefore = visitedUrls.contains(normalizedUrl);
        BloomFilter.AddResult result = filter.add(normalizedUrl);
        visitedUrls.add(normalizedUrl);

        OperationEvent event = OperationEvent.fromAddResult(
            result,
            actuallyVisitedBefore,
            result.probablyPresentBefore()
                ? "모든 비트가 이미 켜져 있어 크롤러는 방문한 URL로 판단합니다."
                : "하나 이상의 비트가 새로 켜졌고 방문 URL 목록에 기록했습니다."
        );
        remember(event);
        return state(event);
    }

    public synchronized DashboardState checkUrl(String url) {
        String normalizedUrl = normalize(url);
        BloomFilter.CheckResult result = filter.mightContain(normalizedUrl);
        boolean actuallyVisited = visitedUrls.contains(normalizedUrl);
        OperationEvent event = OperationEvent.fromCheckResult(result, actuallyVisited, decisionFor(result.probablyPresent(), actuallyVisited));
        remember(event);
        return state(event);
    }

    public synchronized DashboardState findFalsePositive() {
        for (int index = 1; index <= 50_000; index++) {
            String candidate = "https://candidate.example/page-" + index;
            BloomFilter.CheckResult result = filter.mightContain(candidate);
            if (result.probablyPresent() && !visitedUrls.contains(result.value())) {
                OperationEvent event = OperationEvent.fromCheckResult(
                    result,
                    false,
                    "실제로는 방문하지 않았지만 모든 비트가 켜져 있어 false positive가 발생했습니다."
                );
                remember(event);
                return state(event);
            }
        }

        OperationEvent event = OperationEvent.message(
            "false-positive-search",
            "이번 설정에서는 50,000개 후보 안에서 false positive를 찾지 못했습니다. 비트 배열을 더 작게 만들거나 URL을 더 추가해 보세요."
        );
        remember(event);
        return state(event);
    }

    public synchronized DashboardState configure(int bitSize, int hashFunctionCount) {
        filter = new BloomFilter(bitSize, hashFunctionCount);
        visitedUrls.clear();
        history.clear();
        seed();
        OperationEvent event = OperationEvent.message(
            "configure",
            "비트 배열과 해시 함수 수를 바꾸고 샘플 방문 URL을 다시 적재했습니다."
        );
        remember(event);
        return state(event);
    }

    public synchronized DashboardState reset() {
        filter = new BloomFilter(filter.bitSize(), filter.hashFunctionCount());
        visitedUrls.clear();
        history.clear();
        seed();
        OperationEvent event = OperationEvent.message("reset", "샘플 방문 URL로 Bloom filter를 초기화했습니다.");
        remember(event);
        return state(event);
    }

    private void seed() {
        SEED_URLS.forEach(url -> {
            String normalizedUrl = normalize(url);
            filter.add(normalizedUrl);
            visitedUrls.add(normalizedUrl);
        });
    }

    private DashboardState state(OperationEvent latest) {
        return new DashboardState(
            filter.snapshot(visitedUrls.size()),
            new ArrayList<>(visitedUrls),
            history.stream().toList(),
            SAMPLE_URLS,
            latest
        );
    }

    private void remember(OperationEvent event) {
        history.addFirst(event);
        while (history.size() > HISTORY_LIMIT) {
            history.removeLast();
        }
    }

    private String decisionFor(boolean probablyPresent, boolean actuallyVisited) {
        if (!probablyPresent) {
            return "꺼져 있는 비트가 있으므로 이 URL은 확실히 방문하지 않았습니다.";
        }
        if (actuallyVisited) {
            return "모든 비트가 켜져 있고 실제 방문 목록에도 있으므로 중복 URL입니다.";
        }
        return "모든 비트가 켜져 있지만 실제 방문 목록에는 없는 false positive입니다.";
    }

    private String normalize(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
        return url.trim().toLowerCase();
    }

    public record DashboardState(
        BloomFilter.Snapshot filter,
        List<String> visitedUrls,
        List<OperationEvent> history,
        List<String> sampleUrls,
        OperationEvent latest
    ) {
    }

    public record OperationEvent(
        String type,
        String url,
        boolean probablyPresent,
        boolean actuallyVisited,
        boolean changedAnyBit,
        String decision,
        List<BloomFilter.HashProbe> probes
    ) {

        static OperationEvent fromAddResult(BloomFilter.AddResult result, boolean actuallyVisitedBefore, String decision) {
            return new OperationEvent(
                "add",
                result.value(),
                result.probablyPresentBefore(),
                actuallyVisitedBefore,
                result.changedAnyBit(),
                decision,
                result.probes()
            );
        }

        static OperationEvent fromCheckResult(BloomFilter.CheckResult result, boolean actuallyVisited, String decision) {
            return new OperationEvent(
                "check",
                result.value(),
                result.probablyPresent(),
                actuallyVisited,
                false,
                decision,
                result.probes()
            );
        }

        static OperationEvent message(String type, String decision) {
            return new OperationEvent(type, "", false, false, false, decision, List.of());
        }
    }
}
