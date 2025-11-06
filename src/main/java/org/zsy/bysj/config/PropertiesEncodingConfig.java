package org.zsy.bysj.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import java.nio.charset.StandardCharsets;

/**
 * Properties文件编码配置
 * 使Spring Boot使用UTF-8编码读取application.properties文件
 */
@Configuration
public class PropertiesEncodingConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setFileEncoding(StandardCharsets.UTF_8.name());
        configurer.setIgnoreUnresolvablePlaceholders(true);
        return configurer;
    }
}

