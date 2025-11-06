package org.zsy.bysj.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.zsy.bysj.interceptor.JwtInterceptor;
import org.zsy.bysj.interceptor.PermissionInterceptor;

/**
 * Web MVC配置 - 注册拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册JWT拦截器
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")  // 拦截所有/api/**路径
                .excludePathPatterns(
                        "/api/user/register",  // 排除注册接口
                        "/api/user/login",     // 排除登录接口
                        "/api/export/**"       // 排除导出接口
                )
                .order(1);  // 设置优先级，数字越小优先级越高

        // 注册权限拦截器
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/user/**",      // 排除用户相关接口
                        "/api/export/**"     // 排除导出接口
                )
                .order(2);  // 在JWT拦截器之后执行
    }
}

