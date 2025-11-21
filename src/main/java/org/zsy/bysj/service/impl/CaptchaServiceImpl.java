package org.zsy.bysj.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.zsy.bysj.service.CaptchaService;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务实现类
 */
@Service
public class CaptchaServiceImpl implements CaptchaService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final int CAPTCHA_EXPIRE_MINUTES = 5; // 验证码5分钟过期
    private static final int CAPTCHA_LENGTH = 4;
    private static final String CAPTCHA_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 排除容易混淆的字符

    private final Random random = new Random();

    @Override
    public BufferedImage generateCaptcha(String captchaId) {
        // 生成随机验证码
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            code.append(CAPTCHA_CHARS.charAt(random.nextInt(CAPTCHA_CHARS.length())));
        }
        String captchaCode = code.toString();

        // 存储验证码到Redis（5分钟过期）
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        redisTemplate.opsForValue().set(key, captchaCode, CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 创建验证码图片
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 设置抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 设置背景色
        g.setColor(new Color(240, 242, 245));
        g.fillRect(0, 0, width, height);

        // 绘制验证码文字
        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < captchaCode.length(); i++) {
            char c = captchaCode.charAt(i);
            int x = (width / (CAPTCHA_LENGTH + 1)) * (i + 1);
            int y = height / 2;

            // 随机颜色
            int hue = random.nextInt(360);
            g.setColor(Color.getHSBColor(hue / 360f, 0.7f, 0.5f));

            // 随机旋转
            double angle = (random.nextDouble() - 0.5) * 0.4; // -0.2 到 0.2 弧度
            g.rotate(angle, x, y);
            g.drawString(String.valueOf(c), x - 10, y + 10);
            g.rotate(-angle, x, y);
        }

        // 绘制干扰线
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(
                random.nextInt(255),
                random.nextInt(255),
                random.nextInt(255),
                80
            ));
            g.drawLine(
                random.nextInt(width),
                random.nextInt(height),
                random.nextInt(width),
                random.nextInt(height)
            );
        }

        // 绘制干扰点
        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(
                random.nextInt(255),
                random.nextInt(255),
                random.nextInt(255),
                128
            ));
            g.fillOval(random.nextInt(width), random.nextInt(height), 2, 2);
        }

        g.dispose();
        return image;
    }

    @Override
    public String getCaptchaCode(String captchaId) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public boolean verifyCaptcha(String captchaId, String userInput) {
        if (captchaId == null || userInput == null) {
            return false;
        }

        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String storedCode = redisTemplate.opsForValue().get(key);

        if (storedCode == null) {
            return false; // 验证码已过期或不存在
        }

        // 不区分大小写比较
        boolean isValid = storedCode.equalsIgnoreCase(userInput.trim());

        // 验证成功后删除验证码（防止重复使用）
        if (isValid) {
            redisTemplate.delete(key);
        }

        return isValid;
    }

    @Override
    public void deleteCaptcha(String captchaId) {
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        redisTemplate.delete(key);
    }
}

