package org.zsy.bysj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.zsy.bysj.model.ChatRoom;

/**
 * 聊天会话 Mapper
 */
@Mapper
public interface ChatRoomMapper extends BaseMapper<ChatRoom> {
}

