package kr.study.uniqueid.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnowflakeIdGeneratorTest {

    @Test
    void generatedIdsAreUniqueAndOrderedOnSingleNode() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 7);
        List<Long> ids = new ArrayList<>();

        for (int index = 0; index < 10_000; index++) {
            ids.add(generator.nextId());
        }

        assertThat(new HashSet<>(ids)).hasSize(ids.size());
        assertThat(ids).isSorted();
    }

    @Test
    void generatedIdCanBeDecoded() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, 11);

        long id = generator.nextId();
        SnowflakeIdGenerator.DecodedId decoded = generator.decode(id);

        assertThat(decoded.datacenterId()).isEqualTo(3);
        assertThat(decoded.workerId()).isEqualTo(11);
        assertThat(decoded.sequence()).isZero();
        assertThat(decoded.timestampMillis()).isGreaterThanOrEqualTo(SnowflakeIdGenerator.CUSTOM_EPOCH_MILLIS);
    }
}
