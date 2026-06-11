package kr.study.autocomplete.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanUtilsAutocompleteEngineTest {

    @Test
    void korean_utils_startsWith로_부분_한글_입력을_조회한다() {
        KoreanUtilsAutocompleteEngine engine = new KoreanUtilsAutocompleteEngine(List.of(
                new SearchTerm("한글 입력기", 100),
                new SearchTerm("한국 여행", 90),
                new SearchTerm("카카오톡", 120)
        ));

        assertThat(engine.suggest("ㅎ", 5))
                .extracting(Suggestion::query)
                .contains("한글 입력기", "한국 여행");
        assertThat(engine.suggest("카", 5))
                .extracting(Suggestion::query)
                .containsExactly("카카오톡");
    }
}
