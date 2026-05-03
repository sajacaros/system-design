package kr.study.systemdesign.shorturl.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "server")
@Getter
@Setter
public class ShortUrlProperty {
    private String port;

    public String getSnowFlakeShortenerUrlTemplate() {
        return "http://localhost:" + port + "/api/url/sf/s/";
    }

    public String getHashShortenerUrlTemplate() {
        return "http://localhost:" + port + "/api/url/hash/s/";
    }
}
