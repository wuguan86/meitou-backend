package com.meitou.admin.service.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.entity.GenerationRecord;
import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.mapper.AnalysisRecordMapper;
import com.meitou.admin.mapper.GenerationRecordMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import com.meitou.admin.service.common.ApiParameterMappingCacheService;
import com.meitou.admin.service.common.AliyunOssService;
import com.meitou.admin.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenerationServiceTest {

    @Test
    void failGenerationTask_shouldRefundOnlyOnceWhenMultipleCallsOccur() throws Exception {
        // Setup mocks
        ApiPlatformService apiPlatformService = mock(ApiPlatformService.class);
        GenerationRecordMapper generationRecordMapper = mock(GenerationRecordMapper.class);
        AnalysisRecordMapper analysisRecordMapper = mock(AnalysisRecordMapper.class);
        ApiParameterMappingCacheService mappingCacheService = mock(ApiParameterMappingCacheService.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserTransactionMapper userTransactionMapper = mock(UserTransactionMapper.class);
        AliyunOssService aliyunOssService = mock(AliyunOssService.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        // Mock first call: success (updatedRows = 1)
        when(generationRecordMapper.update(isNull(), any())).thenReturn(1, 0);

        GenerationRecord record = new GenerationRecord();
        record.setId(10L);
        record.setType("txt2img");
        record.setUserId(20L);
        record.setSiteId(1L);
        record.setCost(50);
        record.setStatus("processing");
        when(generationRecordMapper.selectById(10L)).thenReturn(record);

        when(userMapper.incrementBalance(anyLong(), anyInt(), any(java.time.LocalDateTime.class))).thenReturn(1);
        User userAfter = new User();
        userAfter.setBalance(100);
        when(userMapper.selectById(anyLong())).thenReturn(userAfter);
        when(userTransactionMapper.insert(any(UserTransaction.class))).thenReturn(1);

        GenerationService service = new GenerationService(
                apiPlatformService,
                generationRecordMapper,
                analysisRecordMapper,
                mappingCacheService,
                userMapper,
                userTransactionMapper,
                aliyunOssService,
                transactionTemplate,
                fileStorageService);

        // First call - Success
        service.failIfProcessingAndRefund(10L, "failure reason");

        // Verify refund occurred once
        verify(userMapper, times(1)).incrementBalance(eq(20L), eq(50), any(java.time.LocalDateTime.class));
        verify(userTransactionMapper, times(1)).insert(any(UserTransaction.class));

        reset(userMapper, userTransactionMapper);
        when(userMapper.selectById(anyLong())).thenReturn(userAfter);
        when(userTransactionMapper.insert(any(UserTransaction.class))).thenReturn(1);

        // Second call - Should be ignored by logic
        service.failIfProcessingAndRefund(10L, "failure reason");

        // Verify refund DID NOT occur again
        verify(userMapper, never()).incrementBalance(anyLong(), anyInt(), any(java.time.LocalDateTime.class));
        verify(userTransactionMapper, never()).insert(any(UserTransaction.class));
    }
}
