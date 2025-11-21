package org.zsy.bysj.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.annotation.PublicEndpoint;
import org.zsy.bysj.dto.LoginRequest;
import org.zsy.bysj.dto.RegisterRequest;
import org.zsy.bysj.dto.UpdateUserRequest;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.model.User;
import org.zsy.bysj.service.CaptchaService;
import org.zsy.bysj.service.UserService;
import org.zsy.bysj.util.RequestUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private CaptchaService captchaService;

    /**
     * 用户注册
     */
    @PublicEndpoint
    @PostMapping("/register")
    public ResponseEntity<Result<Map<String, Object>>> register(@RequestBody RegisterRequest request) {
        try {
            // 验证验证码
            if (request.getCaptchaId() == null || request.getCaptchaCode() == null) {
                return ResponseEntity.badRequest().body(Result.error("请输入验证码"));
            }
            if (!captchaService.verifyCaptcha(request.getCaptchaId(), request.getCaptchaCode())) {
                return ResponseEntity.badRequest().body(Result.error("验证码错误或已过期"));
            }

            User user = userService.register(
                request.getUsername(),
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
            );
            
            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            
            return ResponseEntity.ok(Result.success("注册成功", data));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 用户登录
     */
    @PublicEndpoint
    @PostMapping("/login")
    public ResponseEntity<Result<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        try {
            // 验证验证码
            if (request.getCaptchaId() == null || request.getCaptchaCode() == null) {
                return ResponseEntity.badRequest().body(Result.error("请输入验证码"));
            }
            if (!captchaService.verifyCaptcha(request.getCaptchaId(), request.getCaptchaCode())) {
                return ResponseEntity.badRequest().body(Result.error("验证码错误或已过期"));
            }

            String token = userService.login(request.getUsername(), request.getPassword());
            User user = userService.getUserByUsername(request.getUsername());
            
            // 清除敏感信息
            user.setPassword(null);
            
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("email", user.getEmail());
            data.put("avatar", user.getAvatar());
            
            return ResponseEntity.ok(Result.success("登录成功", data));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public ResponseEntity<Result<User>> getCurrentUserInfo(HttpServletRequest request) {
        try {
            Long userId = RequestUtil.getUserId(request);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            
            User user = userService.getById(userId);
            if (user != null) {
                // 清除敏感信息
                user.setPassword(null);
                return ResponseEntity.ok(Result.success(user));
            }
            return ResponseEntity.badRequest().body(Result.error("用户不存在"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("获取用户信息失败: " + e.getMessage()));
        }
    }

    /**
     * 根据ID获取用户信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<Result<User>> getUser(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user != null) {
            // 清除敏感信息
            user.setPassword(null);
            return ResponseEntity.ok(Result.success(user));
        }
        return ResponseEntity.badRequest().body(Result.error("用户不存在"));
    }

    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public ResponseEntity<Result<Void>> changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            HttpServletRequest request) {
        try {
            Long userId = RequestUtil.getUserId(request);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }
            userService.changePassword(userId, oldPassword, newPassword);
            return ResponseEntity.ok(Result.success("密码修改成功", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 搜索用户（根据用户名或邮箱）
     */
    @GetMapping("/search")
    public ResponseEntity<Result<java.util.List<User>>> searchUsers(@RequestParam String keyword) {
        try {
            java.util.List<User> users = userService.searchUsers(keyword);
            // 清除敏感信息
            users.forEach(user -> user.setPassword(null));
            return ResponseEntity.ok(Result.success(users));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("搜索用户失败: " + e.getMessage()));
        }
    }

    /**
     * 更新当前登录用户信息
     */
    @PutMapping("/info")
    public ResponseEntity<Result<User>> updateUserInfo(
            @RequestBody UpdateUserRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long userId = RequestUtil.getUserId(httpRequest);
            if (userId == null) {
                return ResponseEntity.badRequest().body(Result.error("未认证"));
            }

            // 验证邮箱格式（如果提供了邮箱）
            if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                String email = request.getEmail().trim();
                // 简单的邮箱格式验证
                if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    return ResponseEntity.badRequest().body(Result.error("邮箱格式不正确"));
                }
            }

            // 验证昵称长度（如果提供了昵称）
            if (request.getNickname() != null && request.getNickname().length() > 50) {
                return ResponseEntity.badRequest().body(Result.error("昵称长度不能超过50个字符"));
            }

            // 更新用户信息
            User updatedUser = userService.updateUserInfo(
                userId,
                request.getNickname(),
                request.getEmail(),
                request.getAvatar()
            );

            if (updatedUser != null) {
                // 清除敏感信息
                updatedUser.setPassword(null);
                return ResponseEntity.ok(Result.success("更新成功", updatedUser));
            }
            return ResponseEntity.badRequest().body(Result.error("更新失败"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("更新用户信息失败: " + e.getMessage()));
        }
    }
}

