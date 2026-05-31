package kr.study.urlshortener;

import kr.study.urlshortener.config.DemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DemoProperties.class)
public class UrlShortenerDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerDemoApplication.class, args);
    }
}
