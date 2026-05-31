package kr.study.urlshortener.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.study.urlshortener.config.DemoProperties;
import kr.study.urlshortener.domain.SnowflakeIdGenerator.DecodedId;
import org.springframework.stereotype.Service;

@Service
public class UrlShortenerService {
    private static final int RECENT_LIMIT = 80;
    private static final int MAX_HASH_RETRY = 20;

    private final DemoProperties properties;
    private final SnowflakeIdGenerator idGenerator;
    private final Map<String, StoredUrl> byCode = new ConcurrentHashMap<>();
    private final Map<LongUrlKey, String> byLongUrl = new ConcurrentHashMap<>();
    private final Deque<String> recentCodes = new ArrayDeque<>();
    private final AtomicLong order = new AtomicLong();
    private final AtomicLong totalRedirects = new AtomicLong();

    public UrlShortenerService(DemoProperties properties) {
        this.properties = properties;
        this.idGenerator = new SnowflakeIdGenerator(properties.datacenterId(), properties.workerId());
    }

    public synchronized ShortenResult shorten(ShortenCommand command) {
        String longUrl = normalizeAndValidate(command == null ? null : command.longUrl());
        GenerationStrategy strategy = command.strategyOrDefault();
        LongUrlKey key = new LongUrlKey(strategy, longUrl);
        String existingCode = byLongUrl.get(key);
        if (existingCode != null) {
            StoredUrl stored = byCode.get(existingCode);
            if (stored != null) {
                return new ShortenResult(stored.toMapping(), List.of(), true, "이미 등록된 URL이라 기존 단축 URL을 반환했습니다.");
            }
        }

        if (strategy == GenerationStrategy.ID_BASE62) {
            return shortenById(longUrl, key);
        }
        return shortenByHash(longUrl, key, command == null ? null : command.forcedFirstCode());
    }

    public synchronized CollisionDemoResult runCollisionDemo(ShortenCommand command) {
        String requestedLongUrl = normalizeAndValidate(command == null ? null : command.longUrl());
        String forcedCode = unusedDemoCode();
        String suffix = Base62.fixedFromLong(System.nanoTime(), 8);
        String blockerLongUrl = requestedLongUrl + (requestedLongUrl.contains("?") ? "&" : "?")
            + "__collision_blocker=" + suffix;
        ShortenResult first = shorten(new ShortenCommand(
            blockerLongUrl,
            GenerationStrategy.HASH,
            forcedCode
        ));
        ShortenResult second = shorten(new ShortenCommand(
            requestedLongUrl,
            GenerationStrategy.HASH,
            forcedCode
        ));
        return new CollisionDemoResult(forcedCode, first, second);
    }

    public synchronized DemoState state() {
        List<UrlMapping> mappings = recentCodes.stream()
            .map(byCode::get)
            .filter(stored -> stored != null)
            .map(StoredUrl::toMapping)
            .toList();
        long hashCount = byCode.values().stream()
            .filter(stored -> stored.strategy == GenerationStrategy.HASH)
            .count();
        long idCount = byCode.values().stream()
            .filter(stored -> stored.strategy == GenerationStrategy.ID_BASE62)
            .count();
        return new DemoState(
            properties.publicBaseUrl(),
            properties.hashCodeLength(),
            SnowflakeIdGenerator.CUSTOM_EPOCH_MILLIS,
            Instant.ofEpochMilli(SnowflakeIdGenerator.CUSTOM_EPOCH_MILLIS).toString(),
            properties.datacenterId(),
            properties.workerId(),
            byCode.size(),
            hashCount,
            idCount,
            totalRedirects.get(),
            mappings
        );
    }

    public Optional<RedirectTarget> redirectTarget(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        StoredUrl stored = byCode.get(code);
        if (stored == null) {
            return Optional.empty();
        }
        long redirects = stored.redirectCount.incrementAndGet();
        totalRedirects.incrementAndGet();
        return Optional.of(new RedirectTarget(stored.longUrl, redirects));
    }

    public synchronized void reset() {
        byCode.clear();
        byLongUrl.clear();
        recentCodes.clear();
        totalRedirects.set(0);
    }

    private ShortenResult shortenById(String longUrl, LongUrlKey key) {
        for (int attempt = 0; attempt <= MAX_HASH_RETRY; attempt++) {
            long numericId = idGenerator.nextId();
            String code = Base62.encode(numericId);
            if (!byCode.containsKey(code)) {
                DecodedId decoded = idGenerator.decode(numericId);
                StoredUrl stored = new StoredUrl(
                    order.incrementAndGet(),
                    code,
                    shortUrl(code),
                    longUrl,
                    GenerationStrategy.ID_BASE62,
                    Long.toUnsignedString(numericId),
                    null,
                    0,
                    decoded.timestampMillis(),
                    decoded.instant().toString(),
                    decoded.datacenterId(),
                    decoded.workerId(),
                    decoded.sequence(),
                    Instant.now().toString()
                );
                store(key, stored);
                return new ShortenResult(stored.toMapping(), List.of(), false, "Snowflake ID를 Base62로 변환했습니다.");
            }
        }
        throw new IllegalStateException("Unable to allocate an ID based short code");
    }

    private ShortenResult shortenByHash(String longUrl, LongUrlKey key, String forcedFirstCode) {
        List<CollisionStep> collisions = new ArrayList<>();
        for (int attempt = 0; attempt <= MAX_HASH_RETRY; attempt++) {
            String code = hashCode(longUrl, attempt, normalizeForcedCode(forcedFirstCode));
            StoredUrl existing = byCode.get(code);
            if (existing == null) {
                StoredUrl stored = new StoredUrl(
                    order.incrementAndGet(),
                    code,
                    shortUrl(code),
                    longUrl,
                    GenerationStrategy.HASH,
                    null,
                    "SHA-256",
                    attempt,
                    0,
                    null,
                    null,
                    null,
                    null,
                    Instant.now().toString()
                );
                store(key, stored);
                String message = attempt == 0
                    ? "SHA-256 해시를 7자리 Base62로 잘라 단축 URL을 만들었습니다."
                    : "충돌이 발생해 salt를 바꾸고 다시 해시했습니다.";
                return new ShortenResult(stored.toMapping(), List.copyOf(collisions), false, message);
            }

            if (existing.longUrl.equals(longUrl) && existing.strategy == GenerationStrategy.HASH) {
                return new ShortenResult(existing.toMapping(), List.copyOf(collisions), true, "같은 URL이 이미 등록되어 기존 단축 URL을 반환했습니다.");
            }

            collisions.add(new CollisionStep(
                attempt,
                code,
                existing.longUrl,
                "이미 다른 URL이 사용 중인 코드라 salt=" + (attempt + 1) + "로 재시도"
            ));
        }
        throw new IllegalStateException("Unable to resolve hash collision after " + MAX_HASH_RETRY + " retries");
    }

    private void store(LongUrlKey key, StoredUrl stored) {
        byCode.put(stored.code, stored);
        byLongUrl.put(key, stored.code);
        recentCodes.addFirst(stored.code);
        while (recentCodes.size() > RECENT_LIMIT) {
            recentCodes.removeLast();
        }
    }

    private String hashCode(String longUrl, int attempt, String forcedFirstCode) {
        if (attempt == 0 && forcedFirstCode != null) {
            return forcedFirstCode;
        }
        MessageDigest digest = newSha256();
        byte[] bytes = digest.digest((longUrl + "#salt=" + attempt).getBytes(StandardCharsets.UTF_8));
        return Base62.encodeFixed(bytes, properties.hashCodeLength());
    }

    private String unusedDemoCode() {
        for (int attempt = 0; attempt < 200; attempt++) {
            String candidate = "D" + Base62.fixedFromLong(System.nanoTime() + attempt, properties.hashCodeLength() - 1);
            if (!byCode.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to find an unused demo collision code");
    }

    private String normalizeForcedCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.length() != properties.hashCodeLength()) {
            throw new IllegalArgumentException("forcedFirstCode must be " + properties.hashCodeLength() + " characters");
        }
        if (!trimmed.matches("[0-9a-zA-Z]+")) {
            throw new IllegalArgumentException("forcedFirstCode must use Base62 characters");
        }
        return trimmed;
    }

    private String normalizeAndValidate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("longUrl is required");
        }
        String trimmed = value.trim();
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("Only http and https URLs are supported");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("URL host is required");
            }
            return uri.toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid URL: " + value, ex);
        }
    }

    private MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String shortUrl(String code) {
        String baseUrl = properties.publicBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/r/" + code;
    }

    public record ShortenCommand(
        String longUrl,
        GenerationStrategy strategy,
        String forcedFirstCode
    ) {
        GenerationStrategy strategyOrDefault() {
            return strategy == null ? GenerationStrategy.HASH : strategy;
        }
    }

    public record ShortenResult(
        UrlMapping mapping,
        List<CollisionStep> collisionSteps,
        boolean reused,
        String message
    ) {
    }

    public record CollisionDemoResult(
        String forcedCode,
        ShortenResult first,
        ShortenResult second
    ) {
    }

    public record CollisionStep(
        int attempt,
        String code,
        String occupiedBy,
        String action
    ) {
    }

    public record UrlMapping(
        long order,
        String code,
        String shortUrl,
        String longUrl,
        GenerationStrategy strategy,
        String numericId,
        String hashAlgorithm,
        int hashAttempt,
        long timestampMillis,
        String timestampIso,
        Long datacenterId,
        Long workerId,
        Long sequence,
        String createdAt,
        long redirectCount
    ) {
    }

    public record DemoState(
        String publicBaseUrl,
        int hashCodeLength,
        long customEpochMillis,
        String customEpochIso,
        long datacenterId,
        long workerId,
        int totalMappings,
        long hashMappings,
        long idMappings,
        long totalRedirects,
        List<UrlMapping> recentMappings
    ) {
    }

    public record RedirectTarget(
        String longUrl,
        long redirectCount
    ) {
    }

    private record LongUrlKey(
        GenerationStrategy strategy,
        String longUrl
    ) {
    }

    private static class StoredUrl {
        private final long order;
        private final String code;
        private final String shortUrl;
        private final String longUrl;
        private final GenerationStrategy strategy;
        private final String numericId;
        private final String hashAlgorithm;
        private final int hashAttempt;
        private final long timestampMillis;
        private final String timestampIso;
        private final Long datacenterId;
        private final Long workerId;
        private final Long sequence;
        private final String createdAt;
        private final AtomicLong redirectCount = new AtomicLong();

        private StoredUrl(
            long order,
            String code,
            String shortUrl,
            String longUrl,
            GenerationStrategy strategy,
            String numericId,
            String hashAlgorithm,
            int hashAttempt,
            long timestampMillis,
            String timestampIso,
            Long datacenterId,
            Long workerId,
            Long sequence,
            String createdAt
        ) {
            this.order = order;
            this.code = code;
            this.shortUrl = shortUrl;
            this.longUrl = longUrl;
            this.strategy = strategy;
            this.numericId = numericId;
            this.hashAlgorithm = hashAlgorithm;
            this.hashAttempt = hashAttempt;
            this.timestampMillis = timestampMillis;
            this.timestampIso = timestampIso;
            this.datacenterId = datacenterId;
            this.workerId = workerId;
            this.sequence = sequence;
            this.createdAt = createdAt;
        }

        private UrlMapping toMapping() {
            return new UrlMapping(
                order,
                code,
                shortUrl,
                longUrl,
                strategy,
                numericId,
                hashAlgorithm,
                hashAttempt,
                timestampMillis,
                timestampIso,
                datacenterId,
                workerId,
                sequence,
                createdAt,
                redirectCount.get()
            );
        }
    }
}
