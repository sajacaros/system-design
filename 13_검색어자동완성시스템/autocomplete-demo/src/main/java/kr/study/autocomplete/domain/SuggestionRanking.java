package kr.study.autocomplete.domain;

import java.util.Comparator;

public final class SuggestionRanking {

    public static final Comparator<Suggestion> BY_POPULARITY =
            Comparator.comparingLong(Suggestion::frequency).reversed()
                    .thenComparing(Suggestion::query);

    public static final Comparator<SearchTerm> TERM_BY_POPULARITY =
            Comparator.comparingLong(SearchTerm::frequency).reversed()
                    .thenComparing(SearchTerm::query);

    private SuggestionRanking() {
    }
}
