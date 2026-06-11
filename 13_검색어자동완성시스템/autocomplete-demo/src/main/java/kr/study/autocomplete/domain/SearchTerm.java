package kr.study.autocomplete.domain;

public record SearchTerm(String query, long frequency) {

    public SearchTerm {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (frequency < 0) {
            throw new IllegalArgumentException("frequency must not be negative");
        }
        query = query.strip();
    }
}
