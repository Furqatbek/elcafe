package com.elcafe;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.retry.annotation.EnableRetry;
import com.elcafe.common.tenant.TenantScopedJpaRepository;
import java.util.TimeZone;

@SpringBootApplication
@EnableCaching
@EnableFeignClients
@EnableRetry
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
// Phase 0 §3.4: route every repository through TenantScopedJpaRepository so findById is query-based
// (and therefore scoped by the restaurantFilter in enforce mode), closing the /{id} IDOR class.
@EnableJpaRepositories(basePackages = "com.elcafe", repositoryBaseClass = TenantScopedJpaRepository.class)
public class RestaurantDeliveryApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tashkent"));
        SpringApplication.run(RestaurantDeliveryApplication.class, args);
    }

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tashkent"));
    }
}
