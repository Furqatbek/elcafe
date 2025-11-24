package com.elcafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class RestaurantDeliveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestaurantDeliveryApplication.class, args);
    }
}
