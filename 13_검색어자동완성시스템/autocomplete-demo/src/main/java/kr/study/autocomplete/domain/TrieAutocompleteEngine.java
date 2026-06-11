package kr.study.autocomplete.domain;

import java.util.List;

public final class TrieAutocompleteEngine implements AutocompleteEngine {

    private final AutocompleteTrie trie;

    public TrieAutocompleteEngine(List<SearchTerm> terms, KoreanAutocompleteNormalizer normalizer, int cacheSize) {
        this.trie = new AutocompleteTrie(normalizer, cacheSize);
        terms.forEach(trie::insert);
    }

    @Override
    public String name() {
        return "직접 구현 트라이";
    }

    @Override
    public String strategy() {
        return "자모/초성 다중 키 + 노드별 top-K 캐시";
    }

    @Override
    public List<Suggestion> suggest(String prefix, int limit) {
        return trie.suggest(prefix, limit);
    }

    public int nodeCount() {
        return trie.nodeCount();
    }
}
