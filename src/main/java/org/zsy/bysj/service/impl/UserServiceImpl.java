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

    @Override
    public java.util.List<User> searchUsers(String keyword) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like("username", keyword)
                             .or()
                             .like("email", keyword)
                             .or()
                             .like("nickname", keyword));
        }
        wrapper.last("LIMIT 20"); // 限制返回20条结果
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public User updateUserInfo(Long userId, String nickname, String email, String avatar) {
        // 获取用户信息
        User user = this.getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 如果更新邮箱，检查邮箱是否已被其他用户使用
        if (email != null && !email.trim().isEmpty() && !email.equals(user.getEmail())) {
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("email", email);
            wrapper.ne("id", userId); // 排除当前用户
            User existingUser = this.getOne(wrapper);
            if (existingUser != null) {
                throw new RuntimeException("邮箱已被其他用户使用");
            }
            user.setEmail(email);
        }

        // 更新昵称
        if (nickname != null) {
            user.setNickname(nickname.trim().isEmpty() ? null : nickname.trim());
        }

        // 更新头像
        if (avatar != null) {
            user.setAvatar(avatar.trim().isEmpty() ? null : avatar.trim());
        }

        // 保存更新
        this.updateById(user);
        
        // 返回更新后的用户信息
        return this.getById(userId);
    }
}

