package org.zsy.bysj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.zsy.bysj.model.User;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {
    
    /**
     * 用户注册
     */
    User register(String username, String email, String password, String nickname);
    
    /**
     * 用户登录
     */
    String login(String username, String password);
    
    /**
     * 根据用户名获取用户
     */
    User getUserByUsername(String username);
    
    /**
     * 修改密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);
    
    /**
     * 搜索用户（根据用户名或邮箱）
     */
    java.util.List<User> searchUsers(String keyword);
    
    /**
     * 更新用户信息
     */
    User updateUserInfo(Long userId, String nickname, String email, String avatar);
}

