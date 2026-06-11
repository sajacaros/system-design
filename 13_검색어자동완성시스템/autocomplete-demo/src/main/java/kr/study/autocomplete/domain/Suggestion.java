package kr.study.autocomplete.domain;

public record Suggestion(String query, long frequency) {

    public static Suggestion from(SearchTerm term) {
        return new Suggestion(term.query(), term.frequency());
    }
}
