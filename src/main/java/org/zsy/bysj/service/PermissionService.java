package org.zsy.bysj.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.zsy.bysj.model.DocumentPermission;
import java.util.List;

/**
 * 权限服务接口
 */
public interface PermissionService extends IService<DocumentPermission> {
    
    /**
     * 检查用户是否有文档权限
     */
    boolean hasPermission(Long documentId, Long userId, String permissionType);
    
    /**
     * 添加文档权限
     */
    void addPermission(Long documentId, Long userId, String permissionType);
    
    /**
     * 更新文档权限
     */
    void updatePermission(Long documentId, Long userId, String permissionType);
    
    /**
     * 删除文档权限
     */
    void removePermission(Long documentId, Long userId);
    
    /**
     * 获取文档的所有权限
     */
    List<DocumentPermission> getDocumentPermissions(Long documentId);
    
    /**
     * 获取用户的所有权限
     */
    List<DocumentPermission> getUserPermissions(Long userId);
}

