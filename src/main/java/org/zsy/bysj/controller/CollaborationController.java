package org.zsy.bysj.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.zsy.bysj.dto.Result;
import org.zsy.bysj.service.CollaborationService;

import java.util.List;
import java.util.Map;

/**
 * 协作控制器
 */
@RestController
@RequestMapping("/api/collaboration")
public class CollaborationController {

    @Autowired
    private CollaborationService collaborationService;

    /**
     * 获取文档在线用户列表
     */
    @GetMapping("/online/{documentId}")
    public Result<List<Map<String, Object>>> getOnlineUsers(@PathVariable Long documentId) {
        try {
            List<Map<String, Object>> users = collaborationService.getOnlineUsers(documentId);
            return Result.success(users);
        } catch (Exception e) {
            return Result.error("获取在线用户列表失败: " + e.getMessage());
        }
    }
}
