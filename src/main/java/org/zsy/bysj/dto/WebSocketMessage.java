package org.zsy.bysj.dto;

import lombok.Data;

/**
 * WebSocket消息DTO
 */
@Data
public class WebSocketMessage {
    private String type; // OPERATION/CURSOR/COMMENT/PERMISSION
    private Long documentId;
    private Long userId;
    private Object data; // 根据type不同，data结构不同
    private Long timestamp;
}

