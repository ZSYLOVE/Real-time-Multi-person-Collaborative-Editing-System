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
     * 通过用户ID登录（用于邮箱验证码登录等场景）
     * <p>
     * 会复用现有“登录占用锁”逻辑，确保同账号并发登录被限制。
     */
    String loginByUserId(Long userId);
    
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

    /**
     * 退出登录（释放“登录占用锁”）
     */
    void logout(Long userId);
}

