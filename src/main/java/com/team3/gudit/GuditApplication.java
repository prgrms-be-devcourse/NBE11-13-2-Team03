package com.team3.gudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuditApplication.class, args);
    }

}
