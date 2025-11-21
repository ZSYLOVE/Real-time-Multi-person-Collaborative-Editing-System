package org.zsy.bysj.dto;

import lombok.Data;

/**
 * 注册请求DTO
 */
@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private String nickname;
    private String captchaId;  // 验证码ID
    private String captchaCode; // 验证码值
}

