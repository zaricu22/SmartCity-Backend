package com.example.smartcityback;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackdomApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackdomApplication.class, args);
    }

}
