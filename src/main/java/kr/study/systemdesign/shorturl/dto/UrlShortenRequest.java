package kr.study.systemdesign.shorturl.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class UrlShortenRequest {
    @NotEmpty(message = "url 정보는 필수입니다.")
    private String originUrl;
}
