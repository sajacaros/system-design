package kr.study.uniqueid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IdGeneratorDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdGeneratorDemoApplication.class, args);
    }
}
