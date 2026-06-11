package kr.study.autocomplete.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AutocompleteTrie {

    private final KoreanAutocompleteNormalizer normalizer;
    private final int cacheSize;
    private final Node root = new Node();
    private int nodeCount = 1;

    public AutocompleteTrie(KoreanAutocompleteNormalizer normalizer, int cacheSize) {
        if (normalizer == null) {
            throw new IllegalArgumentException("normalizer must not be null");
        }
        if (cacheSize <= 0) {
            throw new IllegalArgumentException("cacheSize must be positive");
        }
        this.normalizer = normalizer;
        this.cacheSize = cacheSize;
    }

    public void insert(SearchTerm term) {
        for (String key : normalizer.indexKeys(term.query())) {
            insertKey(key, term);
        }
    }

    public List<Suggestion> suggest(String prefix, int limit) {
        int requested = Math.max(limit, 1);
        Map<String, Suggestion> merged = new LinkedHashMap<>();
        if (prefix == null || prefix.isBlank()) {
            merge(merged, root.topSuggestions);
        } else {
            for (String key : normalizer.queryKeys(prefix)) {
                Node node = findNode(key);
                if (node != null) {
                    merge(merged, node.topSuggestions);
                }
            }
        }

        return merged.values().stream()
                .sorted(SuggestionRanking.BY_POPULARITY)
                .limit(requested)
                .toList();
    }

    public int nodeCount() {
        return nodeCount;
    }

    private void insertKey(String key, SearchTerm term) {
        Node current = root;
        current.remember(term, cacheSize);
        for (int index = 0; index < key.length();) {
            int codePoint = key.codePointAt(index);
            current = current.children.computeIfAbsent(codePoint, ignored -> {
                nodeCount++;
                return new Node();
            });
            current.remember(term, cacheSize);
            index += Character.charCount(codePoint);
        }
    }

    private Node findNode(String key) {
        Node current = root;
        for (int index = 0; index < key.length();) {
            int codePoint = key.codePointAt(index);
            current = current.children.get(codePoint);
            if (current == null) {
                return null;
            }
            index += Character.charCount(codePoint);
        }
        return current;
    }

    private void merge(Map<String, Suggestion> merged, List<Suggestion> suggestions) {
        for (Suggestion suggestion : suggestions) {
            merged.merge(
                    suggestion.query(),
                    suggestion,
                    (left, right) -> left.frequency() >= right.frequency() ? left : right
            );
        }
    }

    private static final class Node {

        private final Map<Integer, Node> children = new HashMap<>();
        private final List<Suggestion> topSuggestions = new ArrayList<>();

        private void remember(SearchTerm term, int cacheSize) {
            Suggestion suggestion = Suggestion.from(term);
            topSuggestions.removeIf(existing -> existing.query().equals(suggestion.query()));
            topSuggestions.add(suggestion);
            topSuggestions.sort(SuggestionRanking.BY_POPULARITY);
            if (topSuggestions.size() > cacheSize) {
                topSuggestions.remove(topSuggestions.size() - 1);
            }
        }
    }
}
