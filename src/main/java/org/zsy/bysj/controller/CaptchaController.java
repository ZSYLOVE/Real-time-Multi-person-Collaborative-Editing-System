package org.zsy.bysj.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.annotation.PublicEndpoint;
import org.zsy.bysj.service.CaptchaService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * 验证码控制器
 */
@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {

    @Autowired
    private CaptchaService captchaService;

    /**
     * 生成验证码图片
     * @return 验证码ID和图片
     */
    @PublicEndpoint
    @GetMapping("/generate")
    public ResponseEntity<?> generateCaptcha() {
        try {
            // 生成唯一ID
            String captchaId = UUID.randomUUID().toString();

            // 生成验证码图片
            BufferedImage image = captchaService.generateCaptcha(captchaId);

            // 将图片转换为字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.set("X-Captcha-Id", captchaId); // 在响应头中返回验证码ID

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(imageBytes);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("生成验证码失败: " + e.getMessage());
        }
    }
}

