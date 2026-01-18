package com.meitou.admin.service.common;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.meitou.admin.config.FileStorageConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 阿里云OSS服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AliyunOssService {

    private final FileStorageConfig fileStorageConfig;
    private final RestTemplate restTemplate;

    /**
     * 上传Base64图片
     * @param base64Data Base64字符串 (data:image/png;base64,...)
     * @param directory 目录
     * @return OSS URL
     */
    public String uploadBase64(String base64Data, String directory) {
        if (base64Data == null || !base64Data.startsWith("data:")) {
            return base64Data;
        }

        try {
            int commaIndex = base64Data.indexOf(",");
            if (commaIndex == -1) {
                throw new IllegalArgumentException("Base64格式错误");
            }

            String header = base64Data.substring(0, commaIndex);
            String data = base64Data.substring(commaIndex + 1);
            if (data == null || data.isEmpty()) {
                throw new IllegalArgumentException("Base64内容为空");
            }

            String extension = "png";
            if (header.contains("image/jpeg") || header.contains("image/jpg")) {
                extension = "jpg";
            } else if (header.contains("image/png")) {
                extension = "png";
            } else if (header.contains("image/webp")) {
                extension = "webp";
            } else if (header.contains("image/gif")) {
                extension = "gif";
            }

            byte[] bytes = java.util.Base64.getDecoder().decode(data);
            String fileName = directory + generateFileName(extension);
            
            return uploadBytes(bytes, fileName);
        } catch (Exception e) {
            log.error("Base64上传失败", e);
            throw new RuntimeException("Base64上传失败: " + e.getMessage());
        }
    }

    /**
     * 上传网络图片/视频到OSS
     *
     * @param url 网络文件URL
     * @param directory 目录前缀 (e.g., "images/", "videos/")
     * @return OSS访问URL
     */
    public String uploadFromUrl(String url, String directory) {
        try {
            // 下载文件
            byte[] fileBytes = restTemplate.getForObject(url, byte[].class);
            if (fileBytes == null || fileBytes.length == 0) {
                throw new RuntimeException("下载文件失败: " + url);
            }

            // 获取文件扩展名
            String extension = getExtensionFromUrl(url);
            if (extension == null || extension.isEmpty()) {
                extension = "png"; // 默认扩展名
            }

            // 生成文件名
            String fileName = directory + generateFileName(extension);

            // 上传到OSS
            return uploadBytes(fileBytes, fileName);

        } catch (Exception e) {
            log.error("上传文件失败: {}", e.getMessage(), e);
            throw new RuntimeException("上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 上传字节数组到OSS
     */
    public String uploadBytes(byte[] bytes, String fileName) {
        OSS ossClient = null;
        try {
            FileStorageConfig.AliyunConfig config = fileStorageConfig.getAliyun();
            
            // 创建OSSClient实例
            ossClient = new OSSClientBuilder().build(
                    config.getEndpoint(),
                    config.getAccessKeyId(),
                    config.getAccessKeySecret());

            // 创建上传请求
            InputStream inputStream = new ByteArrayInputStream(bytes);
            PutObjectRequest putObjectRequest = new PutObjectRequest(config.getBucketName(), fileName, inputStream);
            
            // 设置元数据
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(bytes.length);
            putObjectRequest.setMetadata(metadata);

            // 上传
            ossClient.putObject(putObjectRequest);

            // 构建返回URL
            String domain = config.getDomain();
            if (domain == null || domain.isEmpty()) {
                String cleanEndpoint = config.getEndpoint();
                if (cleanEndpoint != null) {
                    if (cleanEndpoint.startsWith("http://")) {
                        cleanEndpoint = cleanEndpoint.substring(7);
                    } else if (cleanEndpoint.startsWith("https://")) {
                        cleanEndpoint = cleanEndpoint.substring(8);
                    }
                    if (cleanEndpoint.endsWith("/")) {
                        cleanEndpoint = cleanEndpoint.substring(0, cleanEndpoint.length() - 1);
                    }
                }
                domain = "https://" + config.getBucketName() + "." + cleanEndpoint;
            }
            
            // 确保域名以http/https开头
            if (!domain.startsWith("http")) {
                domain = "https://" + domain;
            }
            
            // 确保域名末尾有斜杠
            if (!domain.endsWith("/")) {
                domain = domain + "/";
            }

            return domain + fileName;

        } catch (Exception e) {
            log.error("OSS上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("OSS上传失败: " + e.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    /**
     * 生成文件名: yyyyMMdd/uuid.ext
     */
    private String generateFileName(String extension) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String datePath = sdf.format(new Date());
        return datePath + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    /**
     * 从URL获取扩展名
     */
    private String getExtensionFromUrl(String url) {
        try {
            // 移除查询参数
            if (url.contains("?")) {
                url = url.substring(0, url.indexOf("?"));
            }
            // 获取最后一个点之后的内容
            int lastDotIndex = url.lastIndexOf(".");
            if (lastDotIndex > 0 && lastDotIndex < url.length() - 1) {
                return url.substring(lastDotIndex + 1);
            }
        } catch (Exception e) {
            // ignore
        }
        return "";
    }

    public String getSignedUrl(String keyOrUrl) {
        if (keyOrUrl == null || keyOrUrl.isEmpty()) {
            return keyOrUrl;
        }

        FileStorageConfig.AliyunConfig config = fileStorageConfig.getAliyun();
        if (config == null) {
            return keyOrUrl;
        }

        String endpoint = cleanEndpointHost(config.getEndpoint());
        if (endpoint.isEmpty() || isBlank(config.getBucketName()) || isBlank(config.getAccessKeyId()) || isBlank(config.getAccessKeySecret())) {
            return keyOrUrl;
        }

        boolean isHttp = keyOrUrl.startsWith("http://") || keyOrUrl.startsWith("https://");
        if (isHttp && !isAliyunOssUrl(keyOrUrl)) {
            return keyOrUrl;
        }

        if (!isHttp) {
            String type = fileStorageConfig.getType();
            if (type == null || !"aliyun".equalsIgnoreCase(type)) {
                return keyOrUrl;
            }
        }

        String rawQuery = null;
        String urlWithoutQuery = keyOrUrl;
        int queryStartIndex = keyOrUrl.indexOf("?");
        if (queryStartIndex >= 0) {
            urlWithoutQuery = keyOrUrl.substring(0, queryStartIndex);
            rawQuery = keyOrUrl.substring(queryStartIndex + 1);
        }

        String objectKey = urlWithoutQuery;
        String bucketNameForSigning = config.getBucketName();
        boolean isUrl = false;

        String domain = normalizeDomainHost(config.getDomain());
        if (!domain.isEmpty()) {
            String cleanUrl = stripProtocol(urlWithoutQuery);
            if (cleanUrl.startsWith(domain)) {
                objectKey = cleanUrl.substring(domain.length());
                isUrl = true;
            }
        }

        if (!isUrl && isHttp) {
            String cleanUrl = stripProtocol(urlWithoutQuery);
            int slashIndex = cleanUrl.indexOf('/');
            String hostPart = slashIndex >= 0 ? cleanUrl.substring(0, slashIndex) : cleanUrl;
            String pathPart = slashIndex >= 0 ? cleanUrl.substring(slashIndex) : "";

            String endpointSuffix = "." + endpoint;
            if (hostPart.endsWith(endpointSuffix)) {
                String bucketFromUrl = hostPart.substring(0, hostPart.length() - endpointSuffix.length());
                if (!bucketFromUrl.isEmpty()) {
                    bucketNameForSigning = bucketFromUrl;
                    objectKey = pathPart;
                }
            }
        }

        while (objectKey.startsWith("/")) {
            objectKey = objectKey.substring(1);
        }

        if (objectKey.startsWith("http://") || objectKey.startsWith("https://")) {
            if (keyOrUrl.contains("://")) {
                return keyOrUrl;
            }
            objectKey = keyOrUrl;
        }

        OSS ossClient = null;
        try {
            String endpointWithProtocol = config.getEndpoint();
            if (endpointWithProtocol == null) {
                return keyOrUrl;
            }
            if (!endpointWithProtocol.startsWith("http://") && !endpointWithProtocol.startsWith("https://")) {
                endpointWithProtocol = "https://" + endpointWithProtocol;
            }

            ossClient = new OSSClientBuilder().build(endpointWithProtocol, config.getAccessKeyId(), config.getAccessKeySecret());

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
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private boolean isAliyunOssUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        try {
            String host = new URL(url).getHost().toLowerCase();
            return host.contains(".aliyuncs.com") && host.contains("oss-");
        } catch (Exception e) {
            return false;
        }
    }

    private String stripProtocol(String url) {
        String cleanUrl = url;
        if (cleanUrl.startsWith("http://")) cleanUrl = cleanUrl.substring(7);
        if (cleanUrl.startsWith("https://")) cleanUrl = cleanUrl.substring(8);
        return cleanUrl;
    }

    private String cleanEndpointHost(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        String result = endpoint.trim();
        if (result.startsWith("http://")) result = result.substring(7);
        if (result.startsWith("https://")) result = result.substring(8);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String normalizeDomainHost(String domain) {
        if (domain == null) {
            return "";
        }
        String result = domain.trim();
        if (result.isEmpty()) {
            return "";
        }
        result = stripProtocol(result);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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
