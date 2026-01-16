package com.meitou.admin.storage.impl;

import com.meitou.admin.storage.FileStorageService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.util.UUID;

/**
 * 腾讯云COS文件存储服务实现类
 * 使用 @ConditionalOnProperty 注解，只有当配置文件中 file.storage.type=tencent 时才会启用
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "tencent", matchIfMissing = false)
public class TencentCosServiceImpl implements FileStorageService {
    
    /**
     * 腾讯云COS SecretId
     */
    @Value("${file.storage.tencent.secret-id:}")
    private String secretId;
    
    /**
     * 腾讯云COS SecretKey
     */
    @Value("${file.storage.tencent.secret-key:}")
    private String secretKey;
    
    /**
     * 腾讯云COS区域（如：ap-guangzhou）
     */
    @Value("${file.storage.tencent.region:ap-guangzhou}")
    private String region;
    
    /**
     * 腾讯云COS存储桶名称
     */
    @Value("${file.storage.tencent.bucket-name:}")
    private String bucketName;
    
    /**
     * 腾讯云COS访问域名（可选，如果不配置则使用默认域名）
     */
    @Value("${file.storage.tencent.domain:}")
    private String domain;
    
    /**
     * COS客户端
     */
    private COSClient cosClient;
    
    /**
     * 初始化COS客户端
     */
    @PostConstruct
    public void init() {
        try {
            // 创建COS凭证
            COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
            
            // 创建客户端配置
            Region regionObj = new Region(region);
            ClientConfig clientConfig = new ClientConfig(regionObj);
            // 使用HTTPS协议
            clientConfig.setHttpProtocol(HttpProtocol.https);
            
            // 创建COS客户端
            cosClient = new COSClient(cred, clientConfig);
            
            log.info("腾讯云COS客户端初始化成功，区域：{}，存储桶：{}", region, bucketName);
        } catch (Exception e) {
            log.error("腾讯云COS客户端初始化失败", e);
            throw new RuntimeException("腾讯云COS客户端初始化失败", e);
        }
    }
    
    /**
     * 销毁COS客户端，释放资源
     */
    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info("腾讯云COS客户端已关闭");
        }
    }
    
    @Override
    public String upload(InputStream inputStream, String folder, String fileName) throws Exception {
        // 构建对象键（文件路径）
        String objectKey;
        if (folder != null && !folder.isEmpty()) {
            // 确保文件夹路径以 / 结尾
            if (!folder.endsWith("/")) {
                folder = folder + "/";
            }
            objectKey = folder + fileName;
        } else {
            objectKey = fileName;
        }

        try {
            // 设置对象元数据
            ObjectMetadata metadata = new ObjectMetadata();
            // 腾讯云COS可能需要ContentLength，如果不设置可能会有警告或问题，
            // 但如果流不可重复读且未设置长度，SDK可能会缓存流。
            // 这里为了通用性暂不设置长度。

            // 创建上传请求
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName,
                    objectKey,
                    inputStream,
                    metadata
            );

            // 执行上传
            cosClient.putObject(putObjectRequest);

            // 构建文件访问URL
            String fileUrl;
            if (domain != null && !domain.isEmpty()) {
                // 使用自定义域名
                if (domain.endsWith("/")) {
                    fileUrl = domain + objectKey;
                } else {
                    fileUrl = domain + "/" + objectKey;
                }
            } else {
                // 使用默认域名
                fileUrl = String.format("https://%s.cos.%s.myqcloud.com/%s", 
                        bucketName, region, objectKey);
            }

            log.info("文件上传成功：{} -> {}", fileName, fileUrl);
            return fileUrl;

        } catch (CosClientException e) {
            log.error("腾讯云COS上传失败：{}", e.getMessage(), e);
            throw new Exception("文件上传失败：" + e.getMessage(), e);
        }
    }

    /**
     * 上传文件到腾讯云COS
     * 
     * @param file 要上传的文件
     * @param folder 存储文件夹路径
     * @return 文件的访问URL
     * @throws Exception 上传失败时抛出异常
     */
    @Override
    public String upload(MultipartFile file, String folder) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        
        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        
        return upload(file.getInputStream(), folder, fileName);
    }

    @Override
    public String getFileUrl(String keyOrUrl) {
        // 腾讯云COS目前假设为公开访问，直接返回
        return keyOrUrl;
    }
}

