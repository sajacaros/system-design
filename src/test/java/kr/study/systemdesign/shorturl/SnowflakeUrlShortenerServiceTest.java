package kr.study.systemdesign.shorturl;

import kr.study.systemdesign.shorturl.service.UrlShortenerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SnowflakeUrlShortenerServiceTest {
    @Autowired
    @Qualifier("snowflakeUrlShortenerService")
    private UrlShortenerService urlShortenerService;

    @Test
    public void testShortenUrl() {
        String originalUrl = "https://www.example.com/very/long/url/that/needs/to/be/shortened";

        String shortUrl = urlShortenerService.shorten(originalUrl);

        assertNotNull(shortUrl);
        assertTrue(shortUrl.length() > 0);
    }

    @Test
    public void testGetOriginalUrl() {
        String originalUrl = "https://www.example.com/snowflake/test";

        String shortUrl = urlShortenerService.shorten(originalUrl);
        String retrievedUrl = urlShortenerService.getOriginalUrl(shortUrl);

        assertEquals(originalUrl, retrievedUrl);
    }

    @Test
    public void testSameUrlReturnsSameShortUrl() {
        String originalUrl = "https://www.example.com/same/snowflake/url/test";

        String shortUrl1 = urlShortenerService.shorten(originalUrl);
        String shortUrl2 = urlShortenerService.shorten(originalUrl);

        assertEquals(shortUrl1, shortUrl2);
    }

    @Test
    public void testDifferentUrlsDifferentShortUrls() {
        String originalUrl1 = "https://www.example.com/url1";
        String originalUrl2 = "https://www.example.com/url2";

        String shortUrl1 = urlShortenerService.shorten(originalUrl1);
        String shortUrl2 = urlShortenerService.shorten(originalUrl2);

        assertNotEquals(shortUrl1, shortUrl2);
    }

    @Test
    public void testNonExistentShortUrl() {
        String nonExistentShortUrl = "nonexist";

        String retrievedUrl = urlShortenerService.getOriginalUrl(nonExistentShortUrl);

        assertNull(retrievedUrl);
    }
}
