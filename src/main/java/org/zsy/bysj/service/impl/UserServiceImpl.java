package org.zsy.bysj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zsy.bysj.mapper.UserMapper;
import org.zsy.bysj.model.User;
import org.zsy.bysj.service.UserService;
import org.zsy.bysj.util.JwtUtil;
import cn.hutool.crypto.digest.BCrypt;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public User register(String username, String email, String password, String nickname) {
        // 检查用户名是否已存在
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        User existingUser = this.getOne(wrapper);
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查邮箱是否已存在
        wrapper.clear();
        wrapper.eq("email", email);
        existingUser = this.getOne(wrapper);
        if (existingUser != null) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 创建新用户
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(password)); // 使用BCrypt加密密码
        user.setNickname(nickname != null ? nickname : username);

        this.save(user);
        return user;
    }

    @Override
    public String login(String username, String password) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        User user = this.getOne(wrapper);
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 验证密码
        if (!BCrypt.checkpw(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 生成JWT Token
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    @Override
    public User getUserByUsername(String username) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        return this.getOne(wrapper);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证旧密码
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }

        // 更新密码
        user.setPassword(BCrypt.hashpw(newPassword));
        this.updateById(user);
    }
}

