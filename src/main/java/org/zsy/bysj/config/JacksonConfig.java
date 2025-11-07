package org.zsy.bysj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Jackson配置类 - 支持Java 8时间类型序列化
 */
@Configuration
public class JacksonConfig {

    /**
     * 自定义 Jackson ObjectMapper Builder
     * 这个方法会被 Spring Boot 自动调用
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.modules(new JavaTimeModule());
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            System.out.println("=== Jackson2ObjectMapperBuilderCustomizer applied ===");
        };
    }

    /**
     * 创建主 ObjectMapper Bean
     * 使用 Jackson2ObjectMapperBuilder 确保与 Spring Boot 自动配置一致
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        // 确保 JavaTimeModule 已注册
        builder.modules(new JavaTimeModule());
        builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        ObjectMapper mapper = builder.build();
        
        // 再次强制注册 JavaTimeModule（确保）
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 验证配置
        System.out.println("=== ObjectMapper Bean Created ===");
        System.out.println("Registered modules: " + mapper.getRegisteredModuleIds());
        // 检查模块是否注册（使用模块ID而不是类名）
        boolean hasJavaTimeModule = mapper.getRegisteredModuleIds().stream()
            .anyMatch(id -> id.toString().contains("jsr310") || id.toString().contains("JavaTime"));
        System.out.println("JavaTimeModule registered: " + hasJavaTimeModule);
        System.out.println("WRITE_DATES_AS_TIMESTAMPS disabled: " + 
            !mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        
        // 测试序列化 LocalDateTime
        try {
            java.time.LocalDateTime testTime = java.time.LocalDateTime.now();
            String testJson = mapper.writeValueAsString(testTime);
            System.out.println("LocalDateTime serialization test: " + testJson);
        } catch (Exception e) {
            System.out.println("LocalDateTime serialization test FAILED: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("=================================");
        
        return mapper;
    }

    /**
     * 创建 HTTP 消息转换器
     */
    @Bean
    @Primary
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        System.out.println("=== MappingJackson2HttpMessageConverter created with configured ObjectMapper ===");
        return converter;
    }
}

