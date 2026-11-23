package dev.henrique.collectorrunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.context.annotation.PropertySource("classpath:collector-runner.properties")
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class SocialApiAdapterApplication {
    public static void main(String[] args) {
        SpringApplication.run(SocialApiAdapterApplication.class, args);
    }
}
