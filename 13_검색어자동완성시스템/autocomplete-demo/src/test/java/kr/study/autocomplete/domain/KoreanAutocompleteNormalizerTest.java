package kr.study.autocomplete.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanAutocompleteNormalizerTest {

    private final KoreanAutocompleteNormalizer normalizer = new KoreanAutocompleteNormalizer();

    @Test
    void 한글_음절을_입력_순서에_가까운_자모_키로_분해한다() {
        assertThat(normalizer.searchKey("한글")).isEqualTo("ㅎㅏㄴㄱㅡㄹ");
        assertThat(normalizer.searchKey("과자")).isEqualTo("ㄱㅗㅏㅈㅏ");
        assertThat(normalizer.searchKey("값싼")).isEqualTo("ㄱㅏㅂㅅㅆㅏㄴ");
    }

    @Test
    void 초성_키와_공백_제거_키를_함께_만든다() {
        assertThat(normalizer.indexKeys("한국 여행"))
                .containsExactly(
                        "ㅎㅏㄴㄱㅜㄱ ㅇㅕㅎㅐㅇ",
                        "ㅎㅏㄴㄱㅜㄱㅇㅕㅎㅐㅇ",
                        "ㅎㄱㅇㅎ"
                );
    }

    @Test
    void 영문은_대소문자를_통일한다() {
        assertThat(normalizer.searchKey("Java Trie")).isEqualTo("java trie");
        assertThat(normalizer.compactSearchKey("Java Trie")).isEqualTo("javatrie");
    }
}
