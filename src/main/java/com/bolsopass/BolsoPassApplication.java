package com.bolsopass;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BolsoPassApplication {

    public static void main(String[] args) {
        SpringApplication.run(BolsoPassApplication.class, args);
    }
}

