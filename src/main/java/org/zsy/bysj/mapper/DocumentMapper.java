package org.zsy.bysj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.zsy.bysj.model.Document;

import java.util.List;

/**
 * 文档Mapper接口
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /**
     * 查询回收站（已删除文档），用于绕过 MyBatis-Plus 可能注入的逻辑删除过滤。
     * 注意：这里必须使用自定义 SQL，避免 selectList/selectById 自动带上 is_deleted=0 的条件。
     */
    List<Document> selectDeletedDocumentsByCreatorId(@Param("userId") Long userId);

    /**
     * 物理删除文档（不可恢复）
     * <p>
     * 注意：使用自定义 SQL，避免 MyBatis-Plus 注入逻辑删除过滤。
     */
    int forceDeleteById(@Param("documentId") Long documentId);
}

