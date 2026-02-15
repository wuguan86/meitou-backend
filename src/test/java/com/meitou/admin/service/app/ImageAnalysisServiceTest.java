package com.meitou.admin.service.app;

import com.meitou.admin.entity.User;
import com.meitou.admin.mapper.AnalysisRecordMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import com.meitou.admin.service.common.AliyunOssService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ImageAnalysisServiceTest {

    @Test
    void failAndRefundIfPending_shouldRefundOnceWhenPending() throws Exception {
        ApiPlatformService apiPlatformService = mock(ApiPlatformService.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserTransactionMapper userTransactionMapper = mock(UserTransactionMapper.class);
        AnalysisRecordMapper analysisRecordMapper = mock(AnalysisRecordMapper.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        AliyunOssService aliyunOssService = mock(AliyunOssService.class);
        PointsLedgerService pointsLedgerService = mock(PointsLedgerService.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        when(analysisRecordMapper.update(isNull(), any())).thenReturn(1);

        ImageAnalysisService service = new ImageAnalysisService(
                apiPlatformService,
                userMapper,
                userTransactionMapper,
                analysisRecordMapper,
                aliyunOssService,
                pointsLedgerService,
                transactionTemplate);

        Method method = ImageAnalysisService.class.getDeclaredMethod(
                "failAndRefundIfPending",
                Long.class, Long.class, Long.class, int.class, String.class, String.class);
        method.setAccessible(true);

        Long recordId = 10L;
        Long userId = 20L;
        Long siteId = 1L;
        int cost = 50;
        method.invoke(service, recordId, userId, siteId, cost, "err", "图片分析失败退款-testModel");

        verify(pointsLedgerService).refund(eq(userId), eq("image_analysis"), eq(recordId), eq("图片分析失败退款-testModel"));
    }

    @Test
    void failAndRefundIfPending_shouldNotRefundWhenNotPending() throws Exception {
        ApiPlatformService apiPlatformService = mock(ApiPlatformService.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserTransactionMapper userTransactionMapper = mock(UserTransactionMapper.class);
        AnalysisRecordMapper analysisRecordMapper = mock(AnalysisRecordMapper.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        AliyunOssService aliyunOssService = mock(AliyunOssService.class);
        PointsLedgerService pointsLedgerService = mock(PointsLedgerService.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        when(analysisRecordMapper.update(isNull(), any())).thenReturn(0);

        ImageAnalysisService service = new ImageAnalysisService(
                apiPlatformService,
                userMapper,
                userTransactionMapper,
                analysisRecordMapper,
                aliyunOssService,
                pointsLedgerService,
                transactionTemplate);

        Method method = ImageAnalysisService.class.getDeclaredMethod(
                "failAndRefundIfPending",
                Long.class, Long.class, Long.class, int.class, String.class, String.class);
        method.setAccessible(true);

        method.invoke(service, 10L, 20L, 1L, 50, "err", "desc");

        verify(pointsLedgerService, never()).refund(anyLong(), anyString(), anyLong(), anyString());
    }
}
