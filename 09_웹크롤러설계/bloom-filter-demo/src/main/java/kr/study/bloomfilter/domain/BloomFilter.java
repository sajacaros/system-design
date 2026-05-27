package kr.study.bloomfilter.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class BloomFilter {

    private final int bitSize;
    private final int hashFunctionCount;
    private final BitSet bits;

    public BloomFilter(int bitSize, int hashFunctionCount) {
        if (bitSize < 8) {
            throw new IllegalArgumentException("bitSize must be at least 8");
        }
        if (hashFunctionCount < 1) {
            throw new IllegalArgumentException("hashFunctionCount must be at least 1");
        }
        this.bitSize = bitSize;
        this.hashFunctionCount = hashFunctionCount;
        this.bits = new BitSet(bitSize);
    }

    public AddResult add(String value) {
        List<HashProbe> probes = probes(value);
        boolean probablyPresentBefore = probes.stream().allMatch(HashProbe::alreadySet);

        for (HashProbe probe : probes) {
            bits.set(probe.position());
        }

        boolean changedAnyBit = probes.stream().anyMatch(probe -> !probe.alreadySet());
        return new AddResult(normalize(value), probes, probablyPresentBefore, changedAnyBit);
    }

    public CheckResult mightContain(String value) {
        List<HashProbe> probes = probes(value);
        boolean probablyPresent = probes.stream().allMatch(HashProbe::alreadySet);
        return new CheckResult(normalize(value), probes, probablyPresent);
    }

    public Snapshot snapshot(int insertedItemCount) {
        List<Integer> setBits = bits.stream()
            .boxed()
            .toList();
        double fillRatio = (double) bits.cardinality() / bitSize;
        double estimatedFalsePositiveRate = Math.pow(1 - Math.exp(-(double) hashFunctionCount * insertedItemCount / bitSize),
            hashFunctionCount);

        return new Snapshot(
            bitSize,
            hashFunctionCount,
            insertedItemCount,
            bits.cardinality(),
            fillRatio,
            estimatedFalsePositiveRate,
            setBits
        );
    }

    public int bitSize() {
        return bitSize;
    }

    public int hashFunctionCount() {
        return hashFunctionCount;
    }

    private List<HashProbe> probes(String value) {
        String normalized = normalize(value);
        HashPair pair = hashPair(normalized);
        List<HashProbe> probes = new ArrayList<>();

        for (int index = 0; index < hashFunctionCount; index++) {
            long combinedHash = pair.first() + index * pair.second() + (long) index * index;
            int position = Math.floorMod(combinedHash, bitSize);
            probes.add(new HashProbe(index + 1, position, bits.get(position)));
        }

        return probes;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value.trim().toLowerCase();
    }

    private HashPair hashPair(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.wrap(hashed);
            long first = buffer.getLong();
            long second = buffer.getLong();
            if (second == 0) {
                second = 0x9E3779B97F4A7C15L;
            }
            return new HashPair(first, second);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record HashPair(long first, long second) {
    }

    public record HashProbe(int hashIndex, int position, boolean alreadySet) {
    }

    public record AddResult(
        String value,
        List<HashProbe> probes,
        boolean probablyPresentBefore,
        boolean changedAnyBit
    ) {
    }

    public record CheckResult(
        String value,
        List<HashProbe> probes,
        boolean probablyPresent
    ) {
    }

    public record Snapshot(
        int bitSize,
        int hashFunctionCount,
        int insertedItemCount,
        int setBitCount,
        double fillRatio,
        double estimatedFalsePositiveRate,
        List<Integer> setBits
    ) {
    }
}
