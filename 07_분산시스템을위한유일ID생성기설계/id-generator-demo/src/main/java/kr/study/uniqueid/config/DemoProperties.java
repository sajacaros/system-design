package kr.study.uniqueid.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public record DemoProperties(
    String nodeId,
    long datacenterId,
    long workerId,
    List<String> peers,
    int defaultBatchSize
) {
}
