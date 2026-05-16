package kr.study.kvstore.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "demo")
public record DemoProperties(
    String nodeId,
    List<String> peers,
    int replicationFactor,
    int virtualNodes,
    int writeQuorum,
    int readQuorum,
    Gossip gossip
) {

    public record Gossip(
        long suspectAfterMs,
        long deadAfterMs
    ) {
    }
}
