# MyBatis Plus 重构总结

## 已完成的SQL语句替换

### 1. DocumentMapper - 文档Mapper

#### 之前（使用SQL注解）：
```java
@Update("UPDATE document SET content = #{content}, version = #{version}, updated_at = NOW() " +
        "WHERE id = #{id}")
void updateContent(@Param("id") Long id, @Param("content") String content, @Param("version") Integer version);
```

#### 现在（使用MyBatis Plus）：
```java
// Mapper接口：只继承BaseMapper，无需自定义方法
public interface DocumentMapper extends BaseMapper<Document> {
}

// Service实现：使用UpdateWrapper
UpdateWrapper<Document> updateWrapper = new UpdateWrapper<>();
updateWrapper.eq("id", documentId)
             .set("content", content)
             .set("version", version + 1)
             .set("updated_at", LocalDateTime.now());
documentMapper.update(null, updateWrapper);
```

### 2. DocumentOperationMapper - 文档操作Mapper

#### 之前（使用SQL注解）：
```java
@Select("SELECT * FROM document_operation " +
        "WHERE document_id = #{documentId} AND version > #{version} " +
        "ORDER BY timestamp ASC")
List<DocumentOperation> selectByDocumentIdAndVersion(@Param("documentId") Long documentId, 
                                                      @Param("version") Integer version);
```

#### 现在（使用MyBatis Plus）：
```java
// Mapper接口：只继承BaseMapper
public interface DocumentOperationMapper extends BaseMapper<DocumentOperation> {
}

// Service实现：使用QueryWrapper
QueryWrapper<DocumentOperation> queryWrapper = new QueryWrapper<>();
queryWrapper.eq("document_id", documentId)
           .gt("version", version)
           .orderByAsc("timestamp");
List<DocumentOperation> operations = documentOperationMapper.selectList(queryWrapper);
```

## MyBatis Plus 方法使用总结

### 1. 查询操作（QueryWrapper）

#### 基本查询
```java
// 根据ID查询
documentMapper.selectById(id);

// 查询列表
QueryWrapper<Document> wrapper = new QueryWrapper<>();
wrapper.eq("creator_id", userId)
       .orderByDesc("updated_at");
List<Document> documents = documentMapper.selectList(wrapper);

// 查询单个
QueryWrapper<Document> wrapper = new QueryWrapper<>();
wrapper.eq("id", id);
Document document = documentMapper.selectOne(wrapper);
```

#### 条件查询
```java
// 等值查询
wrapper.eq("document_id", documentId);

// 大于查询
wrapper.gt("version", version);

// 为空查询
wrapper.isNull("parent_id");

// 排序
wrapper.orderByAsc("timestamp");
wrapper.orderByDesc("updated_at");

// 组合条件
wrapper.eq("document_id", documentId)
       .gt("version", version)
       .orderByAsc("timestamp");
```

### 2. 更新操作（UpdateWrapper）

#### 方式1：使用UpdateWrapper
```java
UpdateWrapper<Document> updateWrapper = new UpdateWrapper<>();
updateWrapper.eq("id", documentId)
             .set("content", content)
             .set("version", version + 1)
             .set("updated_at", LocalDateTime.now());
documentMapper.update(null, updateWrapper);
```

#### 方式2：更新实体
```java
Document document = documentMapper.selectById(id);
document.setContent(content);
document.setVersion(version + 1);
documentMapper.updateById(document);
```

### 3. 插入操作

```java
// 直接插入实体
Document document = new Document();
document.setTitle(title);
document.setContent(content);
documentMapper.insert(document);
```

### 4. 删除操作

```java
// 根据ID删除
documentMapper.deleteById(id);

// 条件删除
QueryWrapper<Document> wrapper = new QueryWrapper<>();
wrapper.eq("id", id);
documentMapper.delete(wrapper);
```

## 已清理的SQL语句

### ✅ 已移除的SQL注解
1. ❌ `@Update` - DocumentMapper.updateContent()
2. ❌ `@Select` - DocumentOperationMapper.selectByDocumentIdAndVersion()

### ✅ 已替换的SQL操作
1. ✅ 文档内容更新 → `UpdateWrapper`
2. ✅ 文档操作查询 → `QueryWrapper`
3. ✅ 用户文档列表 → `QueryWrapper`

## MyBatis Plus 优势

### 1. 代码简洁
- **之前**：需要写SQL语句，容易出错
- **现在**：链式调用，代码清晰

### 2. 类型安全
- **之前**：SQL字符串，编译期无法检查
- **现在**：Java方法调用，编译期检查

### 3. 自动映射
- **之前**：需要手动映射字段
- **现在**：自动映射驼峰命名

### 4. 功能强大
- 支持逻辑删除（`@TableLogic`）
- 支持自动填充
- 支持分页查询
- 支持条件构造器

## 当前代码状态

### Mapper接口
所有Mapper接口都只继承`BaseMapper<T>`，无需自定义方法：
- ✅ `UserMapper extends BaseMapper<User>`
- ✅ `DocumentMapper extends BaseMapper<Document>`
- ✅ `DocumentOperationMapper extends BaseMapper<DocumentOperation>`
- ✅ `DocumentPermissionMapper extends BaseMapper<DocumentPermission>`
- ✅ `CommentMapper extends BaseMapper<Comment>`

### Service实现
所有Service都使用MyBatis Plus的方法：
- ✅ `QueryWrapper` - 查询条件
- ✅ `UpdateWrapper` - 更新条件
- ✅ `selectById()` - 根据ID查询
- ✅ `selectList()` - 查询列表
- ✅ `insert()` - 插入
- ✅ `update()` - 更新
- ✅ `deleteById()` - 删除

## 最佳实践

### 1. 查询条件
```java
// ✅ 推荐：使用QueryWrapper
QueryWrapper<Document> wrapper = new QueryWrapper<>();
wrapper.eq("creator_id", userId)
       .orderByDesc("updated_at");
List<Document> documents = documentMapper.selectList(wrapper);
```

### 2. 更新操作
```java
// ✅ 推荐：使用UpdateWrapper（部分更新）
UpdateWrapper<Document> updateWrapper = new UpdateWrapper<>();
updateWrapper.eq("id", id)
             .set("content", content);
documentMapper.update(null, updateWrapper);

// ✅ 也可以：更新实体（全量更新）
Document document = documentMapper.selectById(id);
document.setContent(content);
documentMapper.updateById(document);
```

### 3. 删除操作
```java
// ✅ 推荐：使用逻辑删除（配合@TableLogic注解）
documentMapper.deleteById(id);  // 自动转换为UPDATE is_deleted = 1

// ✅ 物理删除
QueryWrapper<Document> wrapper = new QueryWrapper<>();
wrapper.eq("id", id);
documentMapper.delete(wrapper);
```

## 总结

✅ **所有SQL语句已替换为MyBatis Plus方法**
✅ **代码更简洁、类型安全**
✅ **充分利用MyBatis Plus的强大功能**
✅ **减少手写SQL，降低出错概率**

现在整个项目完全使用MyBatis Plus进行数据库操作，没有手写SQL语句！

