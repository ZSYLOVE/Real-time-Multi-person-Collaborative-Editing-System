package org.zsy.bysj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.zsy.bysj.interceptor.JwtInterceptor;
import org.zsy.bysj.interceptor.PermissionInterceptor;

import java.util.List;

/**
 * Web MVC配置 - 注册拦截器和消息转换器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer, ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 完全替换默认的消息转换器列表，确保使用我们配置的 ObjectMapper
        // 先清除所有现有的转换器
        converters.clear();

        // 添加我们配置的 Jackson 转换器（最优先）
        MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
        jacksonConverter.setObjectMapper(objectMapper);
        converters.add(jacksonConverter);
        System.out.println("=== configureMessageConverters: Added MappingJackson2HttpMessageConverter with configured ObjectMapper ===");

        // 添加其他必要的转换器
        converters.add(new org.springframework.http.converter.StringHttpMessageConverter());
        converters.add(new org.springframework.http.converter.ByteArrayHttpMessageConverter());
        converters.add(new org.springframework.http.converter.ResourceHttpMessageConverter());
        converters.add(new org.springframework.http.converter.ResourceRegionHttpMessageConverter());
        converters.add(new org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter());
        converters.add(new org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter());

        System.out.println("=== configureMessageConverters: Total message converters: " + converters.size() + " ===");
        
        // 验证配置
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                MappingJackson2HttpMessageConverter jacksonConv = (MappingJackson2HttpMessageConverter) converter;
                ObjectMapper mapper = jacksonConv.getObjectMapper();
                boolean hasJavaTimeModule = mapper.getRegisteredModuleIds().stream()
                    .anyMatch(id -> id.toString().contains("jsr310"));
                System.out.println("=== configureMessageConverters: Converter at index " + i + " has JavaTimeModule: " + hasJavaTimeModule + " ===");
                System.out.println("=== configureMessageConverters: Converter at index " + i + " ObjectMapper instance: " + mapper.hashCode() + " ===");
                System.out.println("=== configureMessageConverters: Our ObjectMapper instance: " + objectMapper.hashCode() + " ===");
                System.out.println("=== configureMessageConverters: Same instance: " + (mapper == objectMapper) + " ===");
            }
        }
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 在 Spring Boot 自动配置之后，再次确保所有 Jackson 转换器都使用我们的 ObjectMapper
        System.out.println("=== extendMessageConverters: Starting, total converters: " + converters.size() + " ===");
        
        boolean replaced = false;
        for (int i = 0; i < converters.size(); i++) {
            HttpMessageConverter<?> converter = converters.get(i);
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                MappingJackson2HttpMessageConverter jacksonConverter = (MappingJackson2HttpMessageConverter) converter;
                ObjectMapper mapper = jacksonConverter.getObjectMapper();
                
                // 检查是否使用了我们的 ObjectMapper
                if (mapper != objectMapper) {
                    // 替换为使用我们配置的 ObjectMapper
                    jacksonConverter.setObjectMapper(objectMapper);
                    replaced = true;
                    System.out.println("=== extendMessageConverters: Replaced ObjectMapper in converter at index " + i + " ===");
                } else {
                    System.out.println("=== extendMessageConverters: Converter at index " + i + " already uses our ObjectMapper ===");
                }
                
                // 验证配置
                ObjectMapper finalMapper = jacksonConverter.getObjectMapper();
                boolean hasJavaTimeModule = finalMapper.getRegisteredModuleIds().stream()
                    .anyMatch(id -> id.toString().contains("jsr310"));
                System.out.println("=== extendMessageConverters: Converter at index " + i + " has JavaTimeModule: " + hasJavaTimeModule + " ===");
                System.out.println("=== extendMessageConverters: Converter at index " + i + " ObjectMapper instance: " + finalMapper.hashCode() + " ===");
                System.out.println("=== extendMessageConverters: Our ObjectMapper instance: " + objectMapper.hashCode() + " ===");
                System.out.println("=== extendMessageConverters: Same instance: " + (finalMapper == objectMapper) + " ===");
            }
        }
        
        if (!replaced) {
            System.out.println("=== extendMessageConverters: No converters needed replacement ===");
        }
    }

    /**
     * 在应用完全启动后，确保 RequestMappingHandlerAdapter 使用我们的消息转换器
     * 使用 ApplicationListener 避免循环依赖问题
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        try {
            RequestMappingHandlerAdapter adapter = applicationContext.getBean(RequestMappingHandlerAdapter.class);
            List<HttpMessageConverter<?>> converters = adapter.getMessageConverters();
            
            System.out.println("=== ContextRefreshedEvent: RequestMappingHandlerAdapter has " + converters.size() + " converters ===");
            
            boolean replaced = false;
            for (int i = 0; i < converters.size(); i++) {
                HttpMessageConverter<?> converter = converters.get(i);
                if (converter instanceof MappingJackson2HttpMessageConverter) {
                    MappingJackson2HttpMessageConverter jacksonConverter = (MappingJackson2HttpMessageConverter) converter;
                    ObjectMapper mapper = jacksonConverter.getObjectMapper();
                    
                    if (mapper != objectMapper) {
                        jacksonConverter.setObjectMapper(objectMapper);
                        replaced = true;
                        System.out.println("=== ContextRefreshedEvent: Replaced ObjectMapper in RequestMappingHandlerAdapter converter at index " + i + " ===");
                    } else {
                        System.out.println("=== ContextRefreshedEvent: Converter at index " + i + " already uses our ObjectMapper ===");
                    }
                    
                    // 验证配置
                    ObjectMapper finalMapper = jacksonConverter.getObjectMapper();
                    boolean hasJavaTimeModule = finalMapper.getRegisteredModuleIds().stream()
                        .anyMatch(id -> id.toString().contains("jsr310"));
                    System.out.println("=== ContextRefreshedEvent: Converter at index " + i + " has JavaTimeModule: " + hasJavaTimeModule + " ===");
                }
            }
            
            if (!replaced) {
                System.out.println("=== ContextRefreshedEvent: All converters already use our ObjectMapper ===");
            }
        } catch (Exception e) {
            System.out.println("=== ContextRefreshedEvent: Error ensuring RequestMappingHandlerAdapter: " + e.getMessage() + " ===");
            e.printStackTrace();
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置上传文件的访问路径
        // 访问 /uploads/** 时，实际访问的是 uploads 目录下的文件
        String uploadDir = java.nio.file.Paths.get(uploadPath).toAbsolutePath().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

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

