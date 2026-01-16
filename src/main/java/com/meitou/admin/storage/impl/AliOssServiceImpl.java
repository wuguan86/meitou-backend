package com.meitou.admin.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.meitou.admin.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 阿里云OSS文件存储服务实现类
 * 使用 @ConditionalOnProperty 注解，只有当配置文件中 file.storage.type=aliyun 时才会启用
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "aliyun", matchIfMissing = false)
public class AliOssServiceImpl implements FileStorageService {
    
    /**
     * 阿里云OSS AccessKeyId
     */
    @Value("${file.storage.aliyun.access-key-id:}")
    private String accessKeyId;
    
    /**
     * 阿里云OSS AccessKeySecret
     */
    @Value("${file.storage.aliyun.access-key-secret:}")
    private String accessKeySecret;
    
    /**
     * 阿里云OSS Endpoint（如：oss-cn-hangzhou.aliyuncs.com）
     */
    @Value("${file.storage.aliyun.endpoint:}")
    private String endpoint;
    
    /**
     * 阿里云OSS Bucket名称
     */
    @Value("${file.storage.aliyun.bucket-name:}")
    private String bucketName;
    
    /**
     * 阿里云OSS访问域名（可选）
     */
    @Value("${file.storage.aliyun.domain:}")
    private String domain;
    
    /**
     * OSS客户端
     */
    private OSS ossClient;
    
    /**
     * 初始化OSS客户端
     */
    @PostConstruct
    public void init() {
        try {
            // 处理Endpoint，去除协议头
            if (endpoint != null) {
                if (endpoint.startsWith("http://")) {
                    endpoint = endpoint.substring(7);
                } else if (endpoint.startsWith("https://")) {
                    endpoint = endpoint.substring(8);
                }
                // 去除结尾的 /
                if (endpoint.endsWith("/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 1);
                }
            }

            // 创建OSS客户端
            String endpointWithProtocol = endpoint;
            if (endpointWithProtocol != null && !endpointWithProtocol.startsWith("http")) {
                endpointWithProtocol = "https://" + endpointWithProtocol;
            }
            ossClient = new OSSClientBuilder().build(endpointWithProtocol, accessKeyId, accessKeySecret);
            
            log.info("阿里云OSS客户端初始化成功，Endpoint：{}，Bucket：{}", endpoint, bucketName);
        } catch (Exception e) {
            log.error("阿里云OSS客户端初始化失败", e);
            throw new RuntimeException("阿里云OSS客户端初始化失败", e);
        }
    }
    
    /**
     * 销毁OSS客户端，释放资源
     */
    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("阿里云OSS客户端已关闭");
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
            // 注意：如果知道内容长度，最好设置 ContentLength，否则 OSS 可能会报错或需要缓存整个流
            // 这里我们不强制设置 ContentLength，但如果可以获取到最好设置
            // metadata.setContentLength(size); 
            // 简单起见，不设置长度，OSS SDK 会自动处理（可能会缓存）

            // 创建上传请求
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    bucketName,
                    objectKey,
                    inputStream,
                    metadata
            );

            // 执行上传
            ossClient.putObject(putObjectRequest);

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
                // 格式：https://bucket-name.endpoint/object-key
                fileUrl = String.format("https://%s.%s/%s", bucketName, endpoint, objectKey);
            }

            log.info("文件上传成功：{} -> {}", fileName, fileUrl);
            return fileUrl;

        } catch (OSSException e) {
            log.error("阿里云OSS上传失败：{}", e.getMessage(), e);
            throw new Exception("文件上传失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("阿里云OSS上传失败：{}", e.getMessage(), e);
            throw new Exception("文件上传失败：" + e.getMessage(), e);
        }
    }

    /**
     * 上传文件到阿里云OSS
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
        if (keyOrUrl == null || keyOrUrl.isEmpty()) {
            return keyOrUrl;
        }

        String rawQuery = null;
        String urlWithoutQuery = keyOrUrl;
        int queryStartIndex = keyOrUrl.indexOf("?");
        if (queryStartIndex >= 0) {
            urlWithoutQuery = keyOrUrl.substring(0, queryStartIndex);
            rawQuery = keyOrUrl.substring(queryStartIndex + 1);
        }

        // 尝试从URL中提取ObjectKey
        String objectKey = urlWithoutQuery;
        boolean isUrl = false;
        String bucketNameForSigning = bucketName;

        // 1. 检查是否包含自定义域名
        if (domain != null && !domain.isEmpty()) {
            // 处理domain格式（去除协议头）
            String cleanDomain = domain;
            if (cleanDomain.startsWith("http://")) cleanDomain = cleanDomain.substring(7);
            if (cleanDomain.startsWith("https://")) cleanDomain = cleanDomain.substring(8);
            if (cleanDomain.endsWith("/")) cleanDomain = cleanDomain.substring(0, cleanDomain.length() - 1);

            // 移除协议头后的URL
            String cleanUrl = urlWithoutQuery;
            if (cleanUrl.startsWith("http://")) cleanUrl = cleanUrl.substring(7);
            if (cleanUrl.startsWith("https://")) cleanUrl = cleanUrl.substring(8);

            if (cleanUrl.startsWith(cleanDomain)) {
                objectKey = cleanUrl.substring(cleanDomain.length());
                isUrl = true;
            }
        } 
        
        // 2. 检查是否包含默认Endpoint域名（仅当未匹配自定义域名时）
        if (!isUrl) {
            // 移除协议头后的URL
            String cleanUrl = urlWithoutQuery;
            if (cleanUrl.startsWith("http://")) cleanUrl = cleanUrl.substring(7);
            if (cleanUrl.startsWith("https://")) cleanUrl = cleanUrl.substring(8);

            int slashIndex = cleanUrl.indexOf('/');
            String hostPart = slashIndex >= 0 ? cleanUrl.substring(0, slashIndex) : cleanUrl;
            String pathPart = slashIndex >= 0 ? cleanUrl.substring(slashIndex) : "";

            String endpointSuffix = "." + endpoint;
            if (hostPart.endsWith(endpointSuffix)) {
                String bucketFromUrl = hostPart.substring(0, hostPart.length() - endpointSuffix.length());
                if (!bucketFromUrl.isEmpty()) {
                    bucketNameForSigning = bucketFromUrl;
                    objectKey = pathPart;
                    isUrl = true;
                }
            }
        }

        // 去掉开头的 /
        while (objectKey.startsWith("/")) {
            objectKey = objectKey.substring(1);
        }

        // 如果经过处理后还是以 http 开头，说明可能不是本OSS的文件，或者提取失败
        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            // 这种情况下，如果它是本OSS的URL但我们没匹配到域名，可能会有问题
            // 但为了安全起见，如果不确定是Key，就不要去签名，直接返回原URL
            // 除非用户明确传入的就是 Key
            // 这里我们假设如果 keyOrUrl 不含 / 或者不含 :// 则它就是 Key
            if (keyOrUrl.contains("://")) {
                 // 如果无法提取Key，且看起来是个URL，则原样返回
                 return keyOrUrl;
            }
            objectKey = keyOrUrl;
        }

        try {
            // 设置过期时间为1小时
            Date expiration = new Date(System.currentTimeMillis() + 3600 * 1000);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketNameForSigning, objectKey, HttpMethod.GET);
            request.setExpiration(expiration);

            Map<String, String> extraQueryParameters = parseQueryParameters(rawQuery);
            for (Map.Entry<String, String> entry : extraQueryParameters.entrySet()) {
                if (!isSignatureRelatedParameter(entry.getKey())) {
                    request.addQueryParameter(entry.getKey(), entry.getValue());
                }
            }

            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            log.error("生成OSS签名URL失败: {}", keyOrUrl, e);
            return keyOrUrl;
        }
    }

    private Map<String, String> parseQueryParameters(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isEmpty()) {
                continue;
            }
            int equalsIndex = pair.indexOf('=');
            String rawKey = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
            String rawValue = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";

            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    private boolean isSignatureRelatedParameter(String parameterName) {
        if (parameterName == null || parameterName.isEmpty()) {
            return false;
        }
        String name = parameterName.toLowerCase();
        return name.equals("signature")
                || name.equals("ossaccesskeyid")
                || name.equals("expires")
                || name.equals("security-token")
                || name.equals("x-oss-security-token")
                || name.equals("x-oss-signature")
                || name.equals("x-oss-signature-version")
                || name.equals("x-oss-credential")
                || name.equals("x-oss-date")
                || name.equals("x-oss-expires")
                || name.equals("x-oss-signature-nonce")
                || name.equals("x-oss-additional-headers");
    }
}

