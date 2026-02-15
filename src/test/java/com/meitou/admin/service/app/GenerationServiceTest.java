package com.meitou.admin.service.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meitou.admin.entity.GenerationRecord;
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
        PointsLedgerService pointsLedgerService = mock(PointsLedgerService.class);

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

        GenerationService service = new GenerationService(
                apiPlatformService,
                generationRecordMapper,
                analysisRecordMapper,
                mappingCacheService,
                userMapper,
                userTransactionMapper,
                pointsLedgerService,
                aliyunOssService,
                transactionTemplate,
                fileStorageService);

        // First call - Success
        service.failIfProcessingAndRefund(10L, "failure reason");

        // Verify refund occurred once
        verify(pointsLedgerService, times(1)).refund(eq(20L), eq("generation"), eq(10L), anyString());
        reset(pointsLedgerService);

        // Second call - Should be ignored by logic
        service.failIfProcessingAndRefund(10L, "failure reason");

        // Verify refund DID NOT occur again
        verify(pointsLedgerService, never()).refund(anyLong(), anyString(), anyLong(), anyString());
    }
}
