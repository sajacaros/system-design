package kr.study.systemdesign.shorturl.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class UrlShortenResponse {
    String originUrl;
    String shortUrl;
}
