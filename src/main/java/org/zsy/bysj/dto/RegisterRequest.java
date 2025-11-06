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
}

