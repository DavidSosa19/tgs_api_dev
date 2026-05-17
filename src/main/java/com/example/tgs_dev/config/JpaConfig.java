package com.example.tgs_dev.config;

import com.example.tgs_dev.repository.base.BaseRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableJpaRepositories(
        basePackages = "com.example.tgs_dev.repository",
        repositoryBaseClass = BaseRepositoryImpl.class
)
public class JpaConfig {
}