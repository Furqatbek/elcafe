package com.elcafe.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the Hibernate6Module with Jackson so that lazy proxy objects
 * (ByteBuddyInterceptor) are serialized as null instead of throwing
 * HttpMessageConversionException.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Hibernate6Module hibernate6Module() {
        Hibernate6Module module = new Hibernate6Module();
        // Serialize uninitialized lazy associations as null rather than forcing a load.
        // This prevents ByteBuddyInterceptor from reaching Jackson's serializer.
        module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
        return module;
    }
}
