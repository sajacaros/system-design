package kr.study.systemdesign.shorturl.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 해시 기반 URL 단축 서비스 구현
 * - 해시 + 충돌 해결 방식 사용
 */
@Service
@Slf4j
public class HashUrlShortenerService implements UrlShortenerService {
    private static final int SHORT_URL_LENGTH = 7;
    private final Map<String, String> urlStorage = new ConcurrentHashMap<>();
    private final Map<String, String> originalToShortUrl = new ConcurrentHashMap<>();

    @Override
    public String shorten(String originalUrl) {
        // 이미 변환한 URL인 경우 캐시된 결과 사용
        if (originalToShortUrl.containsKey(originalUrl)) {
            return originalToShortUrl.get(originalUrl);
        }

        String shortUrl = generateShortUrl(originalUrl);

        // 충돌 해결: 이미 저장된 키와 충돌이 발생하면 새 해시 생성
        while (urlStorage.containsKey(shortUrl) && !urlStorage.get(shortUrl).equals(originalUrl)) {
            log.info("crash, regenerate shortUrl...");
            shortUrl = generateShortUrl(originalUrl + System.nanoTime());
        }

        urlStorage.put(shortUrl, originalUrl);
        originalToShortUrl.put(originalUrl, shortUrl);

        return shortUrl;
    }

    @Override
    public String getOriginalUrl(String shortUrl) {
        return urlStorage.getOrDefault(shortUrl, null);
    }

    private String generateShortUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));

            String base64Encoded = Base64.getUrlEncoder().encodeToString(hash);
            // 7자리로 줄임 (일부 잘라서 사용)
            return base64Encoded.substring(0, SHORT_URL_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("해시 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}
