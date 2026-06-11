package kr.study.autocomplete.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutocompleteTrieTest {

    private final KoreanAutocompleteNormalizer normalizer = new KoreanAutocompleteNormalizer();

    @Test
    void 접두어_노드의_topK_캐시에서_인기순_결과를_반환한다() {
        AutocompleteTrie trie = trieOf(
                new SearchTerm("한국 여행", 100),
                new SearchTerm("한국어 공부", 90),
                new SearchTerm("한글 입력기", 80),
                new SearchTerm("카카오톡", 120)
        );

        assertThat(trie.suggest("한", 2))
                .extracting(Suggestion::query)
                .containsExactly("한국 여행", "한국어 공부");
    }

    @Test
    void 초성_접두어로도_한글_검색어를_조회한다() {
        AutocompleteTrie trie = trieOf(
                new SearchTerm("한국 여행", 100),
                new SearchTerm("한국어 공부", 90),
                new SearchTerm("한글 입력기", 80),
                new SearchTerm("강남 맛집", 70)
        );

        assertThat(trie.suggest("ㅎㄱ", 5))
                .extracting(Suggestion::query)
                .containsExactly("한국 여행", "한국어 공부", "한글 입력기");
    }

    @Test
    void 공백을_건너뛴_입력과_복합_받침_입력_과정을_처리한다() {
        AutocompleteTrie trie = trieOf(
                new SearchTerm("한국 여행", 100),
                new SearchTerm("값싼 항공권", 95),
                new SearchTerm("갑자기 비", 40)
        );

        assertThat(trie.suggest("한국여", 5))
                .extracting(Suggestion::query)
                .containsExactly("한국 여행");
        assertThat(trie.suggest("갑", 5))
                .extracting(Suggestion::query)
                .contains("값싼 항공권", "갑자기 비");
    }

    @Test
    void 영문_검색어는_대소문자와_공백_축약을_처리한다() {
        AutocompleteTrie trie = trieOf(
                new SearchTerm("java trie", 80),
                new SearchTerm("java string", 70),
                new SearchTerm("spring boot autocomplete", 60)
        );

        assertThat(trie.suggest("JAVA", 5))
                .extracting(Suggestion::query)
                .containsExactly("java trie", "java string");
        assertThat(trie.suggest("springboot", 5))
                .extracting(Suggestion::query)
                .containsExactly("spring boot autocomplete");
    }

    private AutocompleteTrie trieOf(SearchTerm... terms) {
        AutocompleteTrie trie = new AutocompleteTrie(normalizer, 10);
        List.of(terms).forEach(trie::insert);
        return trie;
    }
}
