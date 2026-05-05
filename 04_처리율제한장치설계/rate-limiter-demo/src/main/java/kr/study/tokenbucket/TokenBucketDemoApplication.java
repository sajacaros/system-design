package kr.study.tokenbucket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"kr.study.tokenbucket", "kr.study.leakybucket"})
public class TokenBucketDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(TokenBucketDemoApplication.class, args);
    }
}
