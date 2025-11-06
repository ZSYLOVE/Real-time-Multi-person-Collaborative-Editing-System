package org.zsy.bysj.service;

import org.zsy.bysj.dto.WebSocketMessage;
import java.util.List;
import java.util.Map;

/**
 * 协同编辑服务接口
 */
public interface CollaborationService {
    
    /**
     * 处理用户操作
     */
    void handleOperation(WebSocketMessage message);
    
    /**
     * 处理光标移动
     */
    void handleCursorMove(Long documentId, Long userId, Integer position);
    
    /**
     * 获取在线用户列表
     */
    List<Map<String, Object>> getOnlineUsers(Long documentId);
    
    /**
     * 用户加入文档编辑
     */
    void userJoinDocument(Long documentId, Long userId);
    
    /**
     * 用户离开文档编辑
     */
    void userLeaveDocument(Long documentId, Long userId);
    
    /**
     * 广播消息给文档的所有用户
     */
    void broadcastToDocument(Long documentId, WebSocketMessage message);
}

