package kr.study.autocomplete.web;

import kr.study.autocomplete.domain.SearchTerm;
import kr.study.autocomplete.service.AutocompleteComparisonService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/autocomplete")
public class AutocompleteApiController {

    private final AutocompleteComparisonService comparisonService;

    public AutocompleteApiController(AutocompleteComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping("/compare")
    public AutocompleteComparisonService.ComparisonResponse compare(
            @RequestParam(defaultValue = "한") String prefix,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return comparisonService.compare(prefix, limit);
    }

    @GetMapping("/terms")
    public List<SearchTerm> terms() {
        return comparisonService.terms();
    }
}
