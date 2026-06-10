package com.config;

import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RSQLConfig {

    @Bean
    public RSQLJPASupport rsqlJPASupport(Map<String, EntityManager> entityManagers) {
        return new RSQLJPASupport(entityManagers);
    }
}