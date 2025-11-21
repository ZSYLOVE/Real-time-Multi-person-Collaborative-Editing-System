package org.zsy.bysj.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件服务接口
 */
public interface FileService {
    
    /**
     * 保存头像文件
     * @param file 上传的文件
     * @param userId 用户ID
     * @return 文件相对路径（相对于uploads目录）
     * @throws IOException 文件保存失败时抛出
     */
    String saveAvatar(MultipartFile file, Long userId) throws IOException;
}

