package kr.study.autocomplete.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class KoreanAutocompleteNormalizer {

    private static final int HANGUL_BASE = 0xAC00;
    private static final int HANGUL_END = 0xD7A3;
    private static final int JUNGSEONG_COUNT = 21;
    private static final int JONGSEONG_COUNT = 28;

    private static final String[] CHOSEONG = {
            "ㄱ", "ㄲ", "ㄴ", "ㄷ", "ㄸ", "ㄹ", "ㅁ", "ㅂ", "ㅃ", "ㅅ",
            "ㅆ", "ㅇ", "ㅈ", "ㅉ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };

    private static final String[] JUNGSEONG_INPUT = {
            "ㅏ", "ㅐ", "ㅑ", "ㅒ", "ㅓ", "ㅔ", "ㅕ", "ㅖ", "ㅗ", "ㅗㅏ",
            "ㅗㅐ", "ㅗㅣ", "ㅛ", "ㅜ", "ㅜㅓ", "ㅜㅔ", "ㅜㅣ", "ㅠ", "ㅡ", "ㅡㅣ", "ㅣ"
    };

    private static final String[] JONGSEONG_INPUT = {
            "", "ㄱ", "ㄲ", "ㄱㅅ", "ㄴ", "ㄴㅈ", "ㄴㅎ", "ㄷ", "ㄹ", "ㄹㄱ",
            "ㄹㅁ", "ㄹㅂ", "ㄹㅅ", "ㄹㅌ", "ㄹㅍ", "ㄹㅎ", "ㅁ", "ㅂ", "ㅂㅅ", "ㅅ",
            "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"
    };

    public List<String> indexKeys(String text) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addIfPresent(keys, searchKey(text, false));
        addIfPresent(keys, searchKey(text, true));
        addIfPresent(keys, initialKey(text));
        return List.copyOf(keys);
    }

    public List<String> queryKeys(String prefix) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addIfPresent(keys, searchKey(prefix, false));
        addIfPresent(keys, searchKey(prefix, true));
        return List.copyOf(keys);
    }

    public String searchKey(String text) {
        return searchKey(text, false);
    }

    public String compactSearchKey(String text) {
        return searchKey(text, true);
    }

    public String initialKey(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        text.strip().codePoints().forEach(codePoint -> {
            if (isHangulSyllable(codePoint)) {
                builder.append(CHOSEONG[choseongIndex(codePoint)]);
            } else if (!Character.isWhitespace(codePoint)) {
                builder.append(normalizeNonSyllable(codePoint));
            }
        });
        return builder.toString();
    }

    private String searchKey(String text, boolean compact) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        List<Integer> codePoints = text.strip().codePoints().boxed().toList();
        boolean previousWasWhitespace = false;
        for (int codePoint : codePoints) {
            if (Character.isWhitespace(codePoint)) {
                if (!compact && !previousWasWhitespace && !builder.isEmpty()) {
                    builder.append(' ');
                }
                previousWasWhitespace = true;
                continue;
            }

            if (isHangulSyllable(codePoint)) {
                builder.append(decomposeSyllable(codePoint));
            } else {
                builder.append(normalizeNonSyllable(codePoint));
            }
            previousWasWhitespace = false;
        }
        return builder.toString().strip();
    }

    private String decomposeSyllable(int codePoint) {
        int offset = codePoint - HANGUL_BASE;
        int choseongIndex = offset / (JUNGSEONG_COUNT * JONGSEONG_COUNT);
        int jungseongIndex = (offset % (JUNGSEONG_COUNT * JONGSEONG_COUNT)) / JONGSEONG_COUNT;
        int jongseongIndex = offset % JONGSEONG_COUNT;
        return CHOSEONG[choseongIndex] + JUNGSEONG_INPUT[jungseongIndex] + JONGSEONG_INPUT[jongseongIndex];
    }

    private String normalizeNonSyllable(int codePoint) {
        String mappedJamo = mapConjoiningJamo(codePoint);
        if (mappedJamo != null) {
            return mappedJamo;
        }
        return new String(Character.toChars(codePoint)).toLowerCase(Locale.ROOT);
    }

    private String mapConjoiningJamo(int codePoint) {
        if (codePoint >= 0x1100 && codePoint <= 0x1112) {
            return CHOSEONG[codePoint - 0x1100];
        }
        if (codePoint >= 0x1161 && codePoint <= 0x1175) {
            return JUNGSEONG_INPUT[codePoint - 0x1161];
        }
        if (codePoint >= 0x11A8 && codePoint <= 0x11C2) {
            return JONGSEONG_INPUT[codePoint - 0x11A7];
        }
        return null;
    }

    private boolean isHangulSyllable(int codePoint) {
        return codePoint >= HANGUL_BASE && codePoint <= HANGUL_END;
    }

    private int choseongIndex(int codePoint) {
        return (codePoint - HANGUL_BASE) / (JUNGSEONG_COUNT * JONGSEONG_COUNT);
    }

    private void addIfPresent(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key);
        }
    }
}
