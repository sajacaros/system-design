package kr.study.systemdesign.shorturl.service;

import kr.study.systemdesign.uniqueid.service.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SnowflakeUrlShortenerService implements UrlShortenerService {
    private static final String BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;

    private final Map<String, String> urlStorage = new ConcurrentHashMap<>();
    private final Map<String, String> originalToShortUrl = new ConcurrentHashMap<>();
    @Autowired
    private SnowflakeIdGenerator idGenerator;

    @Override
    public String shorten(String originalUrl) {
        // 이미 변환한 URL인 경우 캐시된 결과 사용
        if (originalToShortUrl.containsKey(originalUrl)) {
            return originalToShortUrl.get(originalUrl);
        }

        // Snowflake ID 생성 후 Base62로 변환
        long id = idGenerator.nextId();
        String shortUrl = encodeBase62(id);

        urlStorage.put(shortUrl, originalUrl);
        originalToShortUrl.put(originalUrl, shortUrl);

        return shortUrl;
    }

    @Override
    public String getOriginalUrl(String shortUrl) {
        return urlStorage.getOrDefault(shortUrl, null);
    }

    /**
     * 10진수 숫자를 Base62 문자열로 변환
     */
    private String encodeBase62(long value) {
        StringBuilder sb = new StringBuilder();

        while (value > 0) {
            sb.append(BASE62_CHARS.charAt((int) (value % BASE)));
            value /= BASE;
        }

        return sb.reverse().toString();
    }

    /**
     * Base62 문자열을 10진수 숫자로 변환
     */
    private long decodeBase62(String str) {
        long result = 0;

        for (char c : str.toCharArray()) {
            result = result * BASE + BASE62_CHARS.indexOf(c);
        }

        return result;
    }
}
