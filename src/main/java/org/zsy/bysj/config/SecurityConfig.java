package org.zsy.bysj.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 安全配置类
 */
@Configuration
public class SecurityConfig {

    /**
     * 注册JWT认证过滤器
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/**", "/ws/**");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

