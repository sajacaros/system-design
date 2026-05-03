package kr.study.systemdesign.shorturl.controller;

import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import kr.study.systemdesign.shorturl.config.ShortUrlProperty;
import kr.study.systemdesign.shorturl.dto.UrlShortenRequest;
import kr.study.systemdesign.shorturl.dto.UrlShortenResponse;
import kr.study.systemdesign.shorturl.service.UrlShortenerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/url/hash")
public class UrlHashShortenerController {

    @Autowired
    private ShortUrlProperty property;


    @Autowired
    @Qualifier(value="hashUrlShortenerService")
    private UrlShortenerService urlShortenerService;

    /**
     * hash URL 단축 요청 처리
     */
    @PostMapping(value="/shorten")
    public UrlShortenResponse shortenUrl(@RequestBody @Valid UrlShortenRequest request) {
        String originUrl = request.getOriginUrl();

        String shortKey = urlShortenerService.shorten(originUrl);
        String shortUrl = property.getHashShortenerUrlTemplate() + shortKey;

        return UrlShortenResponse.builder()
                .shortUrl(shortUrl)
                .originUrl(originUrl)
                .build();
    }

    @GetMapping("/s/{shortKey}")
    public RedirectView redirectToOriginUrl(@PathVariable String shortKey) {
        String originalUrl = urlShortenerService.getOriginalUrl(shortKey);

        if (originalUrl == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 URL입니다.");
        }

        return new RedirectView(originalUrl);
    }
}
