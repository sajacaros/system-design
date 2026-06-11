package kr.study.autocomplete.service;

import kr.study.autocomplete.domain.AutocompleteEngine;
import kr.study.autocomplete.domain.KoreanAutocompleteNormalizer;
import kr.study.autocomplete.domain.KoreanUtilsAutocompleteEngine;
import kr.study.autocomplete.domain.SearchTerm;
import kr.study.autocomplete.domain.Suggestion;
import kr.study.autocomplete.domain.TrieAutocompleteEngine;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutocompleteComparisonService {

    private static final int CACHE_SIZE = 10;

    private final KoreanAutocompleteNormalizer normalizer = new KoreanAutocompleteNormalizer();
    private final List<SearchTerm> terms = SampleSearchTerms.load();
    private final TrieAutocompleteEngine trieEngine = new TrieAutocompleteEngine(terms, normalizer, CACHE_SIZE);
    private final KoreanUtilsAutocompleteEngine libraryEngine = new KoreanUtilsAutocompleteEngine(terms);

    public ComparisonResponse compare(String prefix, int limit) {
        int requested = Math.min(Math.max(limit, 1), 10);
        return new ComparisonResponse(
                prefix == null ? "" : prefix,
                requested,
                resultOf(trieEngine, prefix, requested),
                resultOf(libraryEngine, prefix, requested),
                new NormalizationView(
                        normalizer.queryKeys(prefix),
                        normalizer.indexKeys(exampleFor(prefix)),
                        trieEngine.nodeCount(),
                        terms.size()
                )
        );
    }

    public List<SearchTerm> terms() {
        return terms;
    }

    private EngineResult resultOf(AutocompleteEngine engine, String prefix, int limit) {
        long started = System.nanoTime();
        List<Suggestion> suggestions = engine.suggest(prefix, limit);
        long elapsedMicros = (System.nanoTime() - started) / 1_000;
        return new EngineResult(engine.name(), engine.strategy(), elapsedMicros, suggestions);
    }

    private String exampleFor(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "한국 여행";
        }
        return prefix;
    }

    public record ComparisonResponse(
            String prefix,
            int limit,
            EngineResult trie,
            EngineResult library,
            NormalizationView normalization
    ) {
    }

    public record EngineResult(
            String name,
            String strategy,
            long elapsedMicros,
            List<Suggestion> suggestions
    ) {
    }

    public record NormalizationView(
            List<String> lookupKeys,
            List<String> indexKeysForInput,
            int trieNodeCount,
            int termCount
    ) {
    }
}
