package com.meitou.admin.service.app;

import com.meitou.admin.entity.User;
import com.meitou.admin.entity.UserTransaction;
import com.meitou.admin.mapper.AnalysisRecordMapper;
import com.meitou.admin.mapper.UserMapper;
import com.meitou.admin.mapper.UserTransactionMapper;
import com.meitou.admin.service.admin.ApiPlatformService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class VideoAnalysisServiceTest {

    @Test
    void failAndRefundIfPending_shouldRefundOnceWhenPending() throws Exception {
        ApiPlatformService apiPlatformService = mock(ApiPlatformService.class);
        UserMapper userMapper = mock(UserMapper.class);
        UserTransactionMapper userTransactionMapper = mock(UserTransactionMapper.class);
        AnalysisRecordMapper analysisRecordMapper = mock(AnalysisRecordMapper.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        when(analysisRecordMapper.update(isNull(), any())).thenReturn(1);
        when(userMapper.incrementBalance(anyLong(), anyInt(), any(java.time.LocalDateTime.class))).thenReturn(1);
        User userAfter = new User();
        userAfter.setBalance(500);
        when(userMapper.selectById(anyLong())).thenReturn(userAfter);
        when(userTransactionMapper.insert(any(UserTransaction.class))).thenReturn(1);

        VideoAnalysisService service = new VideoAnalysisService(
                apiPlatformService,
                userMapper,
                userTransactionMapper,
                analysisRecordMapper,
                transactionTemplate);

        Method method = VideoAnalysisService.class.getDeclaredMethod(
                "failAndRefundIfPending",
                Long.class, Long.class, Long.class, int.class, String.class, String.class);
        method.setAccessible(true);

        Long recordId = 100L;
        Long userId = 200L;
        Long siteId = 1L;
        int cost = 20;
        method.invoke(service, recordId, userId, siteId, cost, "err", "视频分析失败退款-testModel");

        verify(userMapper).incrementBalance(eq(userId), eq(cost), any(java.time.LocalDateTime.class));

        ArgumentCaptor<UserTransaction> txCaptor = ArgumentCaptor.forClass(UserTransaction.class);
        verify(userTransactionMapper).insert((UserTransaction) txCaptor.capture());
        UserTransaction tx = txCaptor.getValue();
        assertNotNull(tx);
        assertEquals("REFUND", tx.getType());
        assertEquals(cost, tx.getAmount());
        assertEquals(500, tx.getBalanceAfter());
        assertEquals("视频分析失败退款-testModel", tx.getDescription());
    }
}
