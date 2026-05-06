package org.zsy.bysj.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一对一聊天会话表（对应 schema.sql 的 chat_room）
 */
@Data
@TableName("chat_room")
public class ChatRoom {
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 参与用户A（较小userId）
     */
    private Long userAId;

    /**
     * 参与用户B（较大userId）
     */
    private Long userBId;

    private LocalDateTime createdAt;
}

