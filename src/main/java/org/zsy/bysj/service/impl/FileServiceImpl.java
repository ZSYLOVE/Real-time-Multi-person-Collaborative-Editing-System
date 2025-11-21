package org.zsy.bysj.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.zsy.bysj.service.FileService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件服务实现类
 */
@Service
public class FileServiceImpl implements FileService {

    @Value("${file.upload.path:uploads}")
    private String uploadPath;

    @Override
    public String saveAvatar(MultipartFile file, Long userId) throws IOException {
        // 获取文件扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 生成唯一文件名：用户ID_时间戳_UUID.扩展名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String filename = userId + "_" + timestamp + "_" + uuid + extension;

        // 创建目录结构：uploads/avatars/
        String avatarDir = uploadPath + File.separator + "avatars";
        Path dirPath = Paths.get(avatarDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        // 保存文件
        Path filePath = Paths.get(avatarDir, filename);
        Files.write(filePath, file.getBytes());

        // 返回相对路径（相对于uploads目录），使用正斜杠作为路径分隔符（用于URL）
        return "avatars/" + filename;
    }
}

