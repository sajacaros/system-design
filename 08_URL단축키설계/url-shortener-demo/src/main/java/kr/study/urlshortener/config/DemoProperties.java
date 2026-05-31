package kr.study.urlshortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public record DemoProperties(
    String publicBaseUrl,
    long datacenterId,
    long workerId,
    int hashCodeLength
) {
}
