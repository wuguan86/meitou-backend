package com.meitou.admin.service.app;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.meitou.admin.config.PaymentProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentServiceTest {

    @Test
    void shouldFallbackToWapPay_whenSubCodeForbidden() {
        PaymentService paymentService = new PaymentService();
        AlipayTradePrecreateResponse response = new AlipayTradePrecreateResponse();
        response.setSubCode("ACQ.ACCESS_FORBIDDEN");
        Assertions.assertTrue(paymentService.shouldFallbackToWapPay(response));
    }

    @Test
    void isMobileUserAgent_detectsCommonMobileAgents() {
        PaymentService paymentService = new PaymentService();
        Assertions.assertTrue(paymentService.isMobileUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)"));
        Assertions.assertTrue(paymentService.isMobileUserAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7)"));
        Assertions.assertFalse(paymentService.isMobileUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"));
    }

    @Test
    void createAlipayWapPayForm_allowsEmptyReturnUrl() throws Exception {
        PaymentService paymentService = new PaymentService();

        PaymentProperties.AlipayConfig config = new PaymentProperties.AlipayConfig();
        config.setAppId("test");
        config.setNotifyUrl("https://example.com/notify");
        config.setPrivateKey("test");
        config.setReturnUrl("");

        AlipayClient alipayClient = Mockito.mock(AlipayClient.class);
        AlipayTradeWapPayResponse wapPayResponse = new AlipayTradeWapPayResponse();
        wapPayResponse.setBody("form-body");
        Mockito.when(alipayClient.pageExecute(Mockito.any(AlipayTradeWapPayRequest.class))).thenReturn(wapPayResponse);

        String form = paymentService.createAlipayWapPayForm(alipayClient, config, "order-1", "1.00", "subject");
        Assertions.assertEquals("form-body", form);
    }

    @Test
    void createAlipayPagePayForm_allowsEmptyReturnUrl() throws Exception {
        PaymentService paymentService = new PaymentService();

        PaymentProperties.AlipayConfig config = new PaymentProperties.AlipayConfig();
        config.setAppId("test");
        config.setNotifyUrl("https://example.com/notify");
        config.setPrivateKey("test");
        config.setReturnUrl("");

        AlipayClient alipayClient = Mockito.mock(AlipayClient.class);
        AlipayTradePagePayResponse pagePayResponse = new AlipayTradePagePayResponse();
        pagePayResponse.setBody("page-form-body");
        Mockito.when(alipayClient.pageExecute(Mockito.any(AlipayTradePagePayRequest.class))).thenReturn(pagePayResponse);

        String form = paymentService.createAlipayPagePayForm(alipayClient, config, "order-1", "1.00", "subject");
        Assertions.assertEquals("page-form-body", form);
    }
}
