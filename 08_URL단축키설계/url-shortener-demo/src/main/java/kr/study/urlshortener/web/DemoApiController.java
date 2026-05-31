package kr.study.urlshortener.web;

import kr.study.urlshortener.domain.UrlShortenerService;
import kr.study.urlshortener.domain.UrlShortenerService.CollisionDemoResult;
import kr.study.urlshortener.domain.UrlShortenerService.DemoState;
import kr.study.urlshortener.domain.UrlShortenerService.ShortenCommand;
import kr.study.urlshortener.domain.UrlShortenerService.ShortenResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoApiController {

    private final UrlShortenerService service;

    public DemoApiController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public DemoState state() {
        return service.state();
    }

    @PostMapping("/shorten")
    public ShortenResult shorten(@RequestBody ShortenCommand command) {
        return service.shorten(command);
    }

    @PostMapping("/collision-demo")
    public CollisionDemoResult collisionDemo(@RequestBody ShortenCommand command) {
        return service.runCollisionDemo(command);
    }

    @DeleteMapping("/reset")
    public void reset() {
        service.reset();
    }
}
