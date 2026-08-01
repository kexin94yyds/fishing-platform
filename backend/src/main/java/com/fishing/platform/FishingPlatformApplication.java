package com.fishing.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.fishing.platform.mapper")
@SpringBootApplication
public class FishingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishingPlatformApplication.class, args);
    }
}
