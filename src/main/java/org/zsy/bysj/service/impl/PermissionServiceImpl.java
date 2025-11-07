package org.zsy.bysj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zsy.bysj.mapper.DocumentMapper;
import org.zsy.bysj.mapper.DocumentPermissionMapper;
import org.zsy.bysj.model.Document;
import org.zsy.bysj.model.DocumentPermission;
import org.zsy.bysj.service.PermissionService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限服务实现类
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<DocumentPermissionMapper, DocumentPermission> implements PermissionService {

    @Autowired
    private DocumentMapper documentMapper;

    @Override
    public boolean hasPermission(Long documentId, Long userId, String permissionType) {
        System.out.println("检查权限: documentId=" + documentId + ", userId=" + userId + ", permissionType=" + permissionType);

        // 首先检查用户是否是文档的创建者（创建者拥有所有权限）
        Document document = documentMapper.selectById(documentId);
        if (document != null && document.getCreatorId() != null && document.getCreatorId().equals(userId)) {
            System.out.println("用户" + userId + "是文档创建者，拥有所有权限");
            return true; // 创建者拥有所有权限
        }

        // 检查权限表
        QueryWrapper<DocumentPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .eq("user_id", userId);
        DocumentPermission permission = this.getOne(wrapper);

        System.out.println("数据库权限记录: " + permission);

        if (permission == null) {
            System.out.println("用户" + userId + "没有在权限表中找到记录");
            return false;
        }

        // 检查权限级别
        if ("ADMIN".equals(permissionType)) {
            boolean hasAdmin = "ADMIN".equals(permission.getPermissionType());
            System.out.println("ADMIN权限检查: " + hasAdmin + " (当前权限: " + permission.getPermissionType() + ")");
            return hasAdmin;
        } else if ("WRITE".equals(permissionType)) {
            boolean hasWrite = "ADMIN".equals(permission.getPermissionType()) || "WRITE".equals(permission.getPermissionType());
            System.out.println("WRITE权限检查: " + hasWrite + " (当前权限: " + permission.getPermissionType() + ")");
            return hasWrite;
        } else {
            System.out.println("READ权限检查: true (当前权限: " + permission.getPermissionType() + ")");
            return true; // READ权限最低，有权限就能读
        }
    }

    @Override
    @Transactional
    public void addPermission(Long documentId, Long userId, String permissionType) {
        QueryWrapper<DocumentPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .eq("user_id", userId);
        DocumentPermission existing = this.getOne(wrapper);
        
        if (existing != null) {
            existing.setPermissionType(permissionType);
            this.updateById(existing);
        } else {
            DocumentPermission permission = new DocumentPermission();
            permission.setDocumentId(documentId);
            permission.setUserId(userId);
            permission.setPermissionType(permissionType);
            permission.setCreatedAt(LocalDateTime.now());
            this.save(permission);
        }
    }

    @Override
    @Transactional
    public void updatePermission(Long documentId, Long userId, String permissionType) {
        QueryWrapper<DocumentPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .eq("user_id", userId);
        DocumentPermission permission = this.getOne(wrapper);
        
        if (permission != null) {
            permission.setPermissionType(permissionType);
            this.updateById(permission);
        }
    }

    @Override
    @Transactional
    public void removePermission(Long documentId, Long userId) {
        QueryWrapper<DocumentPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .eq("user_id", userId);
        this.remove(wrapper);
    }

    @Override
    public List<DocumentPermission> getDocumentPermissions(Long documentId) {
        QueryWrapper<DocumentPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId);
        return this.list(wrapper);
    }

    @Override
    public List<DocumentPermission> getUserPermissions(Long userId) {
        QueryWrapper<DocumentPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        return this.list(wrapper);
    }
}

