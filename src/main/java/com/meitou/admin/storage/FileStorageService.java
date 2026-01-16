package com.meitou.admin.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务接口
 * 定义通用的文件上传方法，支持多种云存储实现（腾讯云COS、阿里云OSS等）
 */
public interface FileStorageService {
    
    /**
     * 上传文件到云存储
     *
     * @param inputStream 文件输入流
     * @param folder 存储文件夹路径（可选，如 "images/", "videos/" 等）
     * @param fileName 文件名（包含扩展名）
     * @return 文件的访问URL
     * @throws Exception 上传失败时抛出异常
     */
    String upload(java.io.InputStream inputStream, String folder, String fileName) throws Exception;

    /**
     * 上传文件到云存储
     * 
     * @param file 要上传的文件
     * @param folder 存储文件夹路径（可选，如 "images/", "videos/" 等）
     * @return 文件的访问URL
     * @throws Exception 上传失败时抛出异常
     */
    String upload(MultipartFile file, String folder) throws Exception;
    
    /**
     * 上传文件到云存储（默认文件夹）
     * 
     * @param file 要上传的文件
     * @return 文件的访问URL
     * @throws Exception 上传失败时抛出异常
     */
    default String upload(MultipartFile file) throws Exception {
        return upload(file, "");
    }

    /**
     * 获取文件的访问URL（支持私有读写签名）
     * 
     * @param keyOrUrl 文件的Key或完整URL
     * @return 签名后的URL或原URL
     */
    String getFileUrl(String keyOrUrl);
}

