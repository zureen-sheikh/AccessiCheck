package com.accessicheck;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AccessiCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccessiCheckApplication.class, args);
    }

    @Bean
    CommandLineRunner seed(ScanRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.save(new ScanRecord("20 Oct 2026", 45, 8));
                repo.save(new ScanRecord("21 Oct 2026", 58, 5));
                repo.save(new ScanRecord("22 Oct 2026", 65, 3));
            }
        };
    }
}