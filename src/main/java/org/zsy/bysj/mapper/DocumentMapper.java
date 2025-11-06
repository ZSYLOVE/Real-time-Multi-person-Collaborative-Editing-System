package org.zsy.bysj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.zsy.bysj.model.Document;

/**
 * 文档Mapper接口
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}

