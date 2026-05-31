package kr.study.urlshortener.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import kr.study.urlshortener.domain.UrlShortenerService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class RedirectController {

    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/r/{code}")
    public void redirect(@PathVariable String code, HttpServletResponse response) throws IOException {
        UrlShortenerService.RedirectTarget target = service.redirectTarget(code)
            .orElseThrow(() -> new ShortCodeNotFoundException(code));
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", target.longUrl());
    }
}
