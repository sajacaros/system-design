package kr.study.urlshortener.domain;

import java.math.BigInteger;

public final class Base62 {
    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final BigInteger BASE = BigInteger.valueOf(ALPHABET.length);

    private Base62() {
    }

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        if (value == 0) {
            return "0";
        }

        StringBuilder builder = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            int index = (int) (remaining % ALPHABET.length);
            builder.append(ALPHABET[index]);
            remaining = remaining / ALPHABET.length;
        }
        return builder.reverse().toString();
    }

    public static String encodeFixed(byte[] bytes, int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be positive");
        }

        BigInteger value = new BigInteger(1, bytes);
        char[] output = new char[length];
        for (int index = length - 1; index >= 0; index--) {
            BigInteger[] quotientAndRemainder = value.divideAndRemainder(BASE);
            output[index] = ALPHABET[quotientAndRemainder[1].intValue()];
            value = quotientAndRemainder[0];
        }
        return new String(output);
    }

    public static String fixedFromLong(long value, int length) {
        String encoded = encode(Math.abs(value));
        if (encoded.length() >= length) {
            return encoded.substring(encoded.length() - length);
        }
        return "0".repeat(length - encoded.length()) + encoded;
    }
}
