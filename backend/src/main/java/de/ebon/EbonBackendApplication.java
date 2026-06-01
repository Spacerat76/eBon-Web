package de.ebon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EbonBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EbonBackendApplication.class, args);
    }
}
