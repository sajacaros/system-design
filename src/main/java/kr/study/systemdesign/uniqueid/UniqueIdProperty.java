package kr.study.systemdesign.uniqueid;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "unique-id")
@Getter @Setter
public class UniqueIdProperty {
        private long workerId;
        private long datacenterId;
}
