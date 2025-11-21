package org.zsy.bysj.service;

import java.awt.image.BufferedImage;

/**
 * 验证码服务接口
 */
public interface CaptchaService {

    /**
     * 生成验证码图片和验证码值
     * @return 验证码图片
     */
    BufferedImage generateCaptcha(String captchaId);

    /**
     * 获取验证码值（用于验证）
     * @param captchaId 验证码ID
     * @return 验证码值
     */
    String getCaptchaCode(String captchaId);

    /**
     * 验证验证码
     * @param captchaId 验证码ID
     * @param userInput 用户输入的验证码
     * @return 验证是否通过
     */
    boolean verifyCaptcha(String captchaId, String userInput);

    /**
     * 删除验证码（验证成功后删除）
     * @param captchaId 验证码ID
     */
    void deleteCaptcha(String captchaId);
}

