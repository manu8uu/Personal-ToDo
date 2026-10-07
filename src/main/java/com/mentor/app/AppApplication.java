package com.mentor.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class AppApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone(
                System.getenv().getOrDefault("APP_TIMEZONE", "Europe/Madrid")));
        SpringApplication.run(AppApplication.class, args);
    }
}