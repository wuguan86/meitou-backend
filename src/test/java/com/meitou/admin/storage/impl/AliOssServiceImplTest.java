package com.meitou.admin.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.net.URL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AliOssServiceImplTest {

    @Test
    void getFileUrl_shouldNotPolluteObjectKeyWithQuery_onDefaultHost() throws Exception {
        AliOssServiceImpl service = new AliOssServiceImpl();
        setField(service, "bucketName", "aitoutou");
        setField(service, "endpoint", "oss-cn-shenzhen.aliyuncs.com");
        setField(service, "domain", "");

        OSS oss = mock(OSS.class);
        when(oss.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("https://signed.example.com/test"));
        setField(service, "ossClient", oss);

        String input = "https://aitoutou.oss-cn-shenzhen.aliyuncs.com/videos/a.mp4?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast";
        String signed = service.getFileUrl(input);

        Assertions.assertTrue(signed.startsWith("https://signed.example.com/"));

        ArgumentCaptor<GeneratePresignedUrlRequest> captor = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(oss).generatePresignedUrl(captor.capture());
        Assertions.assertEquals("videos/a.mp4", captor.getValue().getKey());
    }

    @Test
    void getFileUrl_shouldExtractBucketFromHost_whenBucketDiffersFromConfig() throws Exception {
        AliOssServiceImpl service = new AliOssServiceImpl();
        setField(service, "bucketName", "aitoutou");
        setField(service, "endpoint", "oss-cn-shenzhen.aliyuncs.com");
        setField(service, "domain", "");

        OSS oss = mock(OSS.class);
        when(oss.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("https://signed.example.com/test"));
        setField(service, "ossClient", oss);

        String input = "https://another-bucket.oss-cn-shenzhen.aliyuncs.com/images/a.png";
        String signed = service.getFileUrl(input);

        Assertions.assertTrue(signed.startsWith("https://signed.example.com/"));

        ArgumentCaptor<GeneratePresignedUrlRequest> captor = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(oss).generatePresignedUrl(captor.capture());
        Assertions.assertEquals("another-bucket", captor.getValue().getBucketName());
        Assertions.assertEquals("images/a.png", captor.getValue().getKey());
    }

    @Test
    void getFileUrl_shouldNotPolluteObjectKeyWithQuery_onCustomDomain() throws Exception {
        AliOssServiceImpl service = new AliOssServiceImpl();
        setField(service, "bucketName", "aitoutou");
        setField(service, "endpoint", "oss-cn-shenzhen.aliyuncs.com");
        setField(service, "domain", "https://cdn.example.com");

        OSS oss = mock(OSS.class);
        when(oss.generatePresignedUrl(any(GeneratePresignedUrlRequest.class)))
                .thenReturn(new URL("https://signed.example.com/test"));
        setField(service, "ossClient", oss);

        String input = "https://cdn.example.com/videos/a.mp4?x-oss-process=video/snapshot,t_1000,f_jpg,w_800,h_0,m_fast";
        String signed = service.getFileUrl(input);

        Assertions.assertTrue(signed.startsWith("https://signed.example.com/"));

        ArgumentCaptor<GeneratePresignedUrlRequest> captor = ArgumentCaptor.forClass(GeneratePresignedUrlRequest.class);
        verify(oss).generatePresignedUrl(captor.capture());
        Assertions.assertEquals("videos/a.mp4", captor.getValue().getKey());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
