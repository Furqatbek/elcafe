package com.elcafe.config;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
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
        module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);
        return module;
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer hibernateObjectMapperCustomizer(Hibernate6Module hibernate6Module) {
        return builder -> builder.modulesToInstall(hibernate6Module);
    }
}
