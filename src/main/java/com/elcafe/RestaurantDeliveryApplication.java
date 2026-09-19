package com.elcafe;

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

    /**
     * Must stay the first statement, before {@code SpringApplication.run}.
     *
     * <p>Changing the JVM's default zone is only safe while nothing has captured it yet. A JDBC
     * connection captures one when it is opened, and {@code DATE} columns are bound through
     * {@link java.sql.Date}, which is an instant and so converts through whichever zone each side
     * believes in. Set the zone after the pool exists and the two sides disagree by the offset — a
     * date written as the 19th reads back as the 18th.
     *
     * <p>This used to be duplicated in a {@code @PostConstruct}, which was a no-op in production (this
     * line had already run) and harmful everywhere else: under test there is no {@code main}, so the
     * zone flipped mid-bootstrap, after the pool had opened connections in UTC. That produced exactly
     * the off-by-one above, and {@code DailyOrderSequenceService} then wrote the shifted date back and
     * turned it into drift. The duplicate is gone; tests now run start to finish in one zone.
     *
     * <p>The container passes {@code -Duser.timezone} as well, so the JVM starts in the right zone
     * and this line has nothing left to change. It stays because {@code JAVA_OPTS} is documented as
     * overridable, and because running the jar by hand should not quietly behave differently.
     */
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tashkent"));
        SpringApplication.run(RestaurantDeliveryApplication.class, args);
    }
}
