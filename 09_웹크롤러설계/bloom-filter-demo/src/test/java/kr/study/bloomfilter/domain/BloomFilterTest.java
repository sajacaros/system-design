package kr.study.bloomfilter.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BloomFilterTest {

    @Test
    void addedValueIsProbablyContained() {
        BloomFilter filter = new BloomFilter(128, 3);

        BloomFilter.AddResult addResult = filter.add("https://example.com/a");
        BloomFilter.CheckResult checkResult = filter.mightContain("https://example.com/a");

        assertThat(addResult.changedAnyBit()).isTrue();
        assertThat(checkResult.probablyPresent()).isTrue();
        assertThat(checkResult.probes()).hasSize(3);
    }

    @Test
    void missingBitMeansValueIsDefinitelyNotContained() {
        BloomFilter filter = new BloomFilter(256, 4);
        filter.add("https://example.com/a");

        BloomFilter.CheckResult result = filter.mightContain("https://example.com/not-added");

        assertThat(result.probablyPresent()).isFalse();
        assertThat(result.probes()).anyMatch(probe -> !probe.alreadySet());
    }

    @Test
    void addingSameValueAgainDoesNotChangeAnyBit() {
        BloomFilter filter = new BloomFilter(64, 3);
        filter.add("https://example.com/a");

        BloomFilter.AddResult duplicate = filter.add("https://example.com/a");

        assertThat(duplicate.probablyPresentBefore()).isTrue();
        assertThat(duplicate.changedAnyBit()).isFalse();
    }

    @Test
    void snapshotShowsSetBitsAndEstimatedFalsePositiveRate() {
        BloomFilter filter = new BloomFilter(64, 3);
        filter.add("https://example.com/a");
        filter.add("https://example.com/b");

        BloomFilter.Snapshot snapshot = filter.snapshot(2);

        assertThat(snapshot.setBitCount()).isPositive();
        assertThat(snapshot.setBits()).hasSize(snapshot.setBitCount());
        assertThat(snapshot.estimatedFalsePositiveRate()).isBetween(0.0, 1.0);
    }
}
