package org.zsy.bysj.dto;

import lombok.Data;

/**
 * 登录请求DTO
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
    private String captchaId;  // 验证码ID
    private String captchaCode; // 验证码值
}

