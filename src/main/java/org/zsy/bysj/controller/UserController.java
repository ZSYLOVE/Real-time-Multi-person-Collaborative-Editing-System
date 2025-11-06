package org.zsy.bysj.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.annotation.PublicEndpoint;
import org.zsy.bysj.dto.LoginRequest;
import org.zsy.bysj.dto.RegisterRequest;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.model.User;
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

    /**
     * 用户注册
     */
    @PublicEndpoint
    @PostMapping("/register")
    public ResponseEntity<Result<Map<String, Object>>> register(@RequestBody RegisterRequest request) {
        try {
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
            String token = userService.login(request.getUsername(), request.getPassword());
            User user = userService.getUserByUsername(request.getUsername());
            
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            
            return ResponseEntity.ok(Result.success("登录成功", data));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * 获取用户信息
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
}

