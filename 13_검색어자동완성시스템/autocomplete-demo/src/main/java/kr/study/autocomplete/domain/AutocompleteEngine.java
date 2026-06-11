package kr.study.autocomplete.domain;

import java.util.List;

public interface AutocompleteEngine {

    String name();

    String strategy();

    List<Suggestion> suggest(String prefix, int limit);
}
