package org.zsy.bysj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.dao.PersistenceExceptionTranslationAutoConfiguration;

import java.nio.charset.StandardCharsets;

@SpringBootApplication(exclude = {
    PersistenceExceptionTranslationAutoConfiguration.class
})
public class BysjApplication {

    public static void main(String[] args) {
        // 设置系统属性，确保properties文件使用UTF-8编码
        System.setProperty("file.encoding", StandardCharsets.UTF_8.name());
        SpringApplication.run(BysjApplication.class, args);
    }

}
