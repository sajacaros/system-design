package kr.study.systemdesign.shorturl;

import kr.study.systemdesign.shorturl.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class HashUrlShortenerServiceTest {
    @Autowired
    @Qualifier("hashUrlShortenerService")
    private UrlShortenerService urlShortenerService;

    @Test
    void testShortenAndRetrieveOriginalUrl() {
        String originalUrl = "https://example.com/page1";
        String shortUrl = urlShortenerService.shorten(originalUrl);
        assertNotNull(shortUrl);
        assertEquals(originalUrl, urlShortenerService.getOriginalUrl(shortUrl));
    }

    @Test
    void testSameOriginalUrlReturnsSameShortUrl() {
        String originalUrl = "https://example.com/page2";
        String shortUrl1 = urlShortenerService.shorten(originalUrl);
        String shortUrl2 = urlShortenerService.shorten(originalUrl);
        assertEquals(shortUrl1, shortUrl2);
    }

    @Test
    void testDifferentUrlsGetDifferentShortUrls() {
        String url1 = "https://example.com/page3";
        String url2 = "https://example.com/page4";
        String shortUrl1 = urlShortenerService.shorten(url1);
        String shortUrl2 = urlShortenerService.shorten(url2);

        // 같은 해시 충돌 가능성은 낮지만, 해시 길이를 줄이기 때문에 충돌 테스트도 아래에서 별도로 함
        assertNotEquals(shortUrl1, shortUrl2);
    }

    @Test
    void testHashCollisionResolution() {
        // 충돌 유도: 해시 앞부분이 같도록 만든 URL
        String base = "https://example.com/collision";
        String fakeUrl1 = base + "?v=1";
        String fakeUrl2 = base + "?v=1" + " ";  // 비슷한 URL이지만 다른 값

        String shortUrl1 = urlShortenerService.shorten(fakeUrl1);
        String shortUrl2 = urlShortenerService.shorten(fakeUrl2);

        // 짧은 해시로 인해 충돌 가능성 있음 → 충돌 해결을 위해 다른 shortUrl을 생성해야 함
        assertNotEquals(shortUrl1, shortUrl2);

        assertEquals(fakeUrl1, urlShortenerService.getOriginalUrl(shortUrl1));
        assertEquals(fakeUrl2, urlShortenerService.getOriginalUrl(shortUrl2));
    }

    @Test
    void testNonExistentShortUrlReturnsNull() {
        String result = urlShortenerService.getOriginalUrl("nonexistent");
        assertNull(result);
    }
}
