package kr.study.autocomplete.domain;

import io.github.crizin.KoreanUtils;

import java.util.List;

public final class KoreanUtilsAutocompleteEngine implements AutocompleteEngine {

    private final List<SearchTerm> terms;

    public KoreanUtilsAutocompleteEngine(List<SearchTerm> terms) {
        this.terms = terms.stream()
                .sorted(SuggestionRanking.TERM_BY_POPULARITY)
                .toList();
    }

    @Override
    public String name() {
        return "korean-utils";
    }

    @Override
    public String strategy() {
        return "KoreanUtils.startsWith 스캔";
    }

    @Override
    public List<Suggestion> suggest(String prefix, int limit) {
        int requested = Math.max(limit, 1);
        if (prefix == null || prefix.isBlank()) {
            return terms.stream()
                    .limit(requested)
                    .map(Suggestion::from)
                    .toList();
        }

        return terms.stream()
                .filter(term -> KoreanUtils.startsWith(term.query(), prefix))
                .limit(requested)
                .map(Suggestion::from)
                .toList();
    }
}
