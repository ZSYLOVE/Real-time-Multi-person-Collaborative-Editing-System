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
        // 首先检查用户是否是文档的创建者（创建者拥有所有权限）
        Document document = documentMapper.selectById(documentId);
        if (document != null && document.getCreatorId() != null && document.getCreatorId().equals(userId)) {
            return true; // 创建者拥有所有权限
        }
        
        // 检查权限表
        QueryWrapper<DocumentPermission> wrapper = new QueryWrapper<>();
        wrapper.eq("document_id", documentId)
               .eq("user_id", userId);
        DocumentPermission permission = this.getOne(wrapper);
        
        if (permission == null) {
            return false;
        }
        
        // 检查权限级别
        if ("ADMIN".equals(permissionType)) {
            return "ADMIN".equals(permission.getPermissionType());
        } else if ("WRITE".equals(permissionType)) {
            return "ADMIN".equals(permission.getPermissionType()) || "WRITE".equals(permission.getPermissionType());
        } else {
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

