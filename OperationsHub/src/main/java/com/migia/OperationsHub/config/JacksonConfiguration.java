package com.migia.OperationsHub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JacksonConfiguration {

    @Bean
    public JsonMapper jsonMapper() {
        return JsonMapper.builder()
                // configure your features here
                .build();
    }
}
