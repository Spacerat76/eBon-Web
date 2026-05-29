package de.spacerat76.ebon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EbonApplication {
    public static void main(String[] args) {
        SpringApplication.run(EbonApplication.class, args);
    }
}
