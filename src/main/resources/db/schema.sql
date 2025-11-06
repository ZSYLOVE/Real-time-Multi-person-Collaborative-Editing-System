-- 实时多人协同编辑系统数据库脚本

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    `email` VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    `password` VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    `avatar` VARCHAR(255) COMMENT '头像URL',
    `nickname` VARCHAR(50) COMMENT '昵称',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_email` (`email`),
    INDEX `idx_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 文档表
CREATE TABLE IF NOT EXISTS `document` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `title` VARCHAR(255) NOT NULL COMMENT '文档标题',
    `content` LONGTEXT COMMENT '文档内容（JSON格式）',
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
    `version` INT DEFAULT 1 COMMENT '当前版本号',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '是否删除',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_creator_id` (`creator_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

-- 文档版本表
CREATE TABLE IF NOT EXISTS `document_version` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `version` INT NOT NULL COMMENT '版本号',
    `content` LONGTEXT COMMENT '文档内容快照',
    `snapshot` LONGTEXT COMMENT '版本快照（JSON）',
    `created_by` BIGINT NOT NULL COMMENT '创建者ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_document_version` (`document_id`, `version`),
    FOREIGN KEY (`document_id`) REFERENCES `document`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本表';

-- 文档权限表
CREATE TABLE IF NOT EXISTS `document_permission` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `permission_type` VARCHAR(20) NOT NULL COMMENT '权限类型：READ/WRITE/ADMIN',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_document_user` (`document_id`, `user_id`),
    INDEX `idx_user_id` (`user_id`),
    FOREIGN KEY (`document_id`) REFERENCES `document`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档权限表';

-- 文档操作日志表（用于OT算法）
CREATE TABLE IF NOT EXISTS `document_operation` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `operation_type` VARCHAR(20) NOT NULL COMMENT '操作类型：INSERT/DELETE/RETAIN',
    `operation_data` TEXT COMMENT '操作数据（JSON格式）',
    `position` INT NOT NULL COMMENT '操作位置',
    `length` INT DEFAULT 0 COMMENT '操作长度',
    `timestamp` BIGINT NOT NULL COMMENT '时间戳（用于排序）',
    `version` INT NOT NULL COMMENT '操作时的文档版本',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_document_timestamp` (`document_id`, `timestamp`),
    INDEX `idx_document_version` (`document_id`, `version`),
    FOREIGN KEY (`document_id`) REFERENCES `document`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档操作日志表';

-- 评论表
CREATE TABLE IF NOT EXISTS `comment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `document_id` BIGINT NOT NULL COMMENT '文档ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `position` INT NOT NULL COMMENT '评论位置（在文档中的位置）',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID（用于回复）',
    `is_resolved` TINYINT DEFAULT 0 COMMENT '是否已解决',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_document_id` (`document_id`),
    INDEX `idx_parent_id` (`parent_id`),
    FOREIGN KEY (`document_id`) REFERENCES `document`(`id`) ON DELETE CASCADE,
    FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- 用户会话表（用于在线用户追踪）
CREATE TABLE IF NOT EXISTS `user_session` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `document_id` BIGINT COMMENT '当前编辑的文档ID',
    `session_id` VARCHAR(100) NOT NULL COMMENT '会话ID',
    `cursor_position` INT DEFAULT 0 COMMENT '光标位置',
    `last_active_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_document_id` (`document_id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话表';

