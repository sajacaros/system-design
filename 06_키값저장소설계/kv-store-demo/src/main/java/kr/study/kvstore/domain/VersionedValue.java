package kr.study.kvstore.domain;

import java.time.Instant;

public record VersionedValue(
    String versionId,
    String key,
    String value,
    VectorClock clock,
    String writerNodeId,
    Instant writtenAt
) {
}
