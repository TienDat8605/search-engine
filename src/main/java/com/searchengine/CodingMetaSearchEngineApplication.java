package com.searchengine;

import com.searchengine.config.SearchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SearchProperties.class)
public class CodingMetaSearchEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodingMetaSearchEngineApplication.class, args);
    }
}
