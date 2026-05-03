package kr.study.systemdesign.shorturl.service;

public interface UrlShortenerService {
    /**
     * 원본 URL을 단축 URL로 변환
     *
     * @param originalUrl 원본 URL
     * @return 단축 URL 코드(키)
     */
    String shorten(String originalUrl);

    /**
     * 단축 URL 코드로부터 원본 URL 조회
     *
     * @param shortUrl 단축 URL 코드(키)
     * @return 원본 URL
     */
    String getOriginalUrl(String shortUrl);
}
