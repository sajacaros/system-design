package kr.study.bloomfilter.web;

import java.util.Map;
import kr.study.bloomfilter.service.BloomFilterDemoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoApiController {

    private final BloomFilterDemoService demoService;

    public DemoApiController(BloomFilterDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping("/dashboard")
    public BloomFilterDemoService.DashboardState dashboard() {
        return demoService.dashboard();
    }

    @PostMapping("/urls/check")
    public BloomFilterDemoService.DashboardState checkUrl(@RequestBody UrlRequest request) {
        return demoService.checkUrl(request.url());
    }

    @PostMapping("/urls/add")
    public BloomFilterDemoService.DashboardState addUrl(@RequestBody UrlRequest request) {
        return demoService.addUrl(request.url());
    }

    @PostMapping("/false-positive")
    public BloomFilterDemoService.DashboardState findFalsePositive() {
        return demoService.findFalsePositive();
    }

    @PostMapping("/configure")
    public BloomFilterDemoService.DashboardState configure(
        @RequestParam(defaultValue = "64") int bitSize,
        @RequestParam(defaultValue = "3") int hashFunctionCount
    ) {
        return demoService.configure(bitSize, hashFunctionCount);
    }

    @PostMapping("/reset")
    public BloomFilterDemoService.DashboardState reset() {
        return demoService.reset();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException exception) {
        return Map.of("message", exception.getMessage());
    }

    public record UrlRequest(String url) {
    }
}
