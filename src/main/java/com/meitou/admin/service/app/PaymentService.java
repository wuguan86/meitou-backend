package com.meitou.admin.service.app;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.v3.auth.Verifier;
import com.meitou.admin.config.PaymentProperties;
import com.meitou.admin.exception.BusinessException;
import com.meitou.admin.exception.ErrorCode;
import com.meitou.admin.util.AesEncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * 支付服务类
 * 处理微信支付和支付宝支付的统一下单、回调验证等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 创建微信支付订单
     * 
     * @param orderNo 订单号
     * @param amount 金额（元）
     * @param description 商品描述
     * @param configJson 支付配置JSON（可选，如果提供则优先使用，否则使用application.yml中的配置）
     * @return 支付参数（包含二维码URL或支付链接等）
     */
    public Map<String, String> createWechatPayment(String orderNo, String amount, String description, String configJson) {
        try {
            log.info("创建微信支付订单：订单号={}, 金额={}, 描述={}", orderNo, amount, description);
            
            // 解析配置（优先使用传入的configJson，否则使用application.yml中的配置）
            PaymentProperties.WechatPayConfig config = parseWechatConfig(configJson);

            int totalFen = new BigDecimal(amount)
                .multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();

            WxPayService wxPayService = buildWxPayServiceForV3(config, null);

            WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request()
                .setAppid(config.getAppId())
                .setMchid(config.getMchId())
                .setDescription(description)
                .setOutTradeNo(orderNo)
                .setNotifyUrl(config.getNotifyUrl())
                .setAmount(new WxPayUnifiedOrderV3Request.Amount().setTotal(totalFen).setCurrency("CNY"))
                .setSceneInfo(new WxPayUnifiedOrderV3Request.SceneInfo().setPayerClientIp("127.0.0.1"));

            WxPayUnifiedOrderV3Result result = wxPayService.unifiedOrderV3(TradeTypeEnum.NATIVE, request);

            String codeUrl = result.getCodeUrl();
            Map<String, String> paymentParams = new HashMap<>();
            paymentParams.put("qrCodeUrl", codeUrl != null ? codeUrl : "");
            paymentParams.put("orderId", orderNo);
            paymentParams.put("paymentUrl", codeUrl != null ? codeUrl : "");

            log.info("微信支付订单创建成功：订单号={}, 二维码URL是否为空={}", orderNo, !StringUtils.hasText(codeUrl));
            return paymentParams;
            
        } catch (WxPayException e) {
            log.error("创建微信支付订单失败，错误码：{}, 错误信息：{}", e.getErrCode(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_CREATE_FAILED.getCode(), "创建微信支付订单失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("创建微信支付订单失败", e);
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_CREATE_FAILED.getCode(), "创建微信支付订单失败：" + e.getMessage());
        }
    }

    public Map<String, String> parseWechatCallbackV3(String callbackBody, SignatureHeader signatureHeader, String configJson) {
        return parseWechatCallbackV3(callbackBody, signatureHeader, configJson, null);
    }

    Map<String, String> parseWechatCallbackV3(String callbackBody, SignatureHeader signatureHeader, String configJson, Verifier verifierOverride) {
        try {
            PaymentProperties.WechatPayConfig config = parseWechatConfig(configJson);
            WxPayService wxPayService = buildWxPayServiceForV3(config, verifierOverride);
            WxPayNotifyV3Result notifyResult = wxPayService.parseOrderNotifyV3Result(callbackBody, signatureHeader);
            WxPayNotifyV3Result.DecryptNotifyResult result = notifyResult.getResult();

            Map<String, String> callbackData = new HashMap<>();
            if (result != null) {
                callbackData.put("appid", result.getAppid());
                callbackData.put("mchid", result.getMchid());
                callbackData.put("out_trade_no", result.getOutTradeNo());
                callbackData.put("transaction_id", result.getTransactionId());
                callbackData.put("trade_state", result.getTradeState());
                callbackData.put("success_time", result.getSuccessTime());
                if (result.getAmount() != null && result.getAmount().getTotal() != null) {
                    callbackData.put("amount_total", String.valueOf(result.getAmount().getTotal()));
                }
            }

            return callbackData;
        } catch (WxPayException e) {
            log.error("解析微信支付V3回调失败，错误码：{}, 错误信息：{}", e.getErrCode(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_VERIFY_FAILED.getCode(), "微信支付回调处理失败：" + e.getMessage());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信支付V3回调失败", e);
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_VERIFY_FAILED.getCode(), "微信支付回调处理失败");
        }
    }
    
    /**
     * 创建支付宝支付订单
     * 
     * @param orderNo 订单号
     * @param amount 金额（元）
     * @param description 商品描述
     * @param configJson 支付配置JSON（可选，如果提供则优先使用，否则使用application.yml中的配置）
     * @return 支付参数（包含支付链接或二维码URL等）
     */
    public Map<String, String> createAlipayPayment(String orderNo, String amount, String description, String configJson) {
        return createAlipayPayment(orderNo, amount, description, configJson, null);
    }

    public Map<String, String> createAlipayPayment(String orderNo, String amount, String description, String configJson, String userAgent) {
        try {
            log.info("创建支付宝支付订单：订单号={}, 金额={}, 描述={}", orderNo, amount, description);
            
            // 解析配置（优先使用传入的configJson，否则使用application.yml中的配置）
            PaymentProperties.AlipayConfig config = parseAlipayConfig(configJson);

            AlipayClient alipayClient = buildAlipayClient(config);

            if (isMobileUserAgent(userAgent)) {
                String paymentForm = createAlipayWapPayForm(alipayClient, config, orderNo, amount, description);
                Map<String, String> paymentParams = new HashMap<>();
                paymentParams.put("orderId", orderNo);
                paymentParams.put("paymentForm", paymentForm);
                return paymentParams;
            }
            
            // 构建请求参数（使用扫码支付）
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setNotifyUrl(config.getNotifyUrl()); // 异步通知地址
            
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(orderNo); // 商户订单号
            model.setTotalAmount(amount); // 订单总金额（元）
            model.setSubject(description); // 订单标题
            request.setBizModel(model);
            
            // 调用支付宝接口
            AlipayTradePrecreateResponse response = alipayClient.execute(request);
            
            if (!response.isSuccess()) {
                log.error(
                    "创建支付宝支付订单失败，code={}, msg={}, subCode={}, subMsg={}",
                    response.getCode(),
                    response.getMsg(),
                    response.getSubCode(),
                    response.getSubMsg()
                );

                if (shouldFallbackToWapPay(response)) {
                    String paymentForm = createAlipayPagePayForm(alipayClient, config, orderNo, amount, description);
                    Map<String, String> paymentParams = new HashMap<>();
                    paymentParams.put("orderId", orderNo);
                    paymentParams.put("paymentForm", paymentForm);
                    return paymentParams;
                }

                String msg = response.getSubMsg();
                if (!StringUtils.hasText(msg)) {
                    msg = response.getMsg();
                }
                throw new BusinessException(
                    ErrorCode.PAYMENT_ORDER_CREATE_FAILED.getCode(),
                    "创建支付宝支付订单失败：" + (StringUtils.hasText(msg) ? msg : "Business Failed")
                );
            }
            
            // 构建返回结果
            Map<String, String> paymentParams = new HashMap<>();
            paymentParams.put("qrCodeUrl", response.getQrCode()); // 二维码URL
            paymentParams.put("orderId", response.getOutTradeNo()); // 商户订单号
            paymentParams.put("paymentUrl", response.getQrCode()); // 支付链接（扫码支付时与二维码URL相同）
            
            log.info("支付宝支付订单创建成功：订单号={}, 二维码URL={}", orderNo, response.getQrCode());
            return paymentParams;
            
        } catch (AlipayApiException e) {
            log.error("创建支付宝支付订单失败，错误码：{}, 错误信息：{}", e.getErrCode(), e.getErrMsg(), e);
            throw new BusinessException(
                ErrorCode.PAYMENT_ORDER_CREATE_FAILED.getCode(),
                "支付宝下单失败：" + (StringUtils.hasText(e.getErrMsg()) ? e.getErrMsg() : "请稍后重试")
            );
        } catch (Exception e) {
            log.error("创建支付宝支付订单失败", e);
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_CREATE_FAILED.getCode(), "支付系统异常，请稍后重试");
        }
    }

    boolean isMobileUserAgent(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return false;
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        return ua.contains("android")
            || ua.contains("iphone")
            || ua.contains("ipad")
            || ua.contains("ipod")
            || ua.contains("windows phone")
            || ua.contains("mobi")
            || ua.contains("mobile");
    }

    AlipayClient buildAlipayClient(PaymentProperties.AlipayConfig config) throws AlipayApiException {
        if (StringUtils.hasText(config.getAppCertContent())
            && StringUtils.hasText(config.getAlipayRootCertContent())
            && StringUtils.hasText(config.getAlipayCertContent())) {

            CertAlipayRequest certParams = new CertAlipayRequest();
            certParams.setServerUrl(config.getGatewayUrl());
            certParams.setAppId(config.getAppId());
            certParams.setPrivateKey(config.getPrivateKey());
            certParams.setFormat(config.getFormat());
            certParams.setCharset(config.getCharset());
            certParams.setSignType(config.getSignType());
            certParams.setCertContent(config.getAppCertContent());
            certParams.setRootCertContent(config.getAlipayRootCertContent());
            certParams.setAlipayPublicCertContent(config.getAlipayCertContent());
            return new DefaultAlipayClient(certParams);
        }

        return new DefaultAlipayClient(
            config.getGatewayUrl(),
            config.getAppId(),
            config.getPrivateKey(),
            config.getFormat(),
            config.getCharset(),
            config.getAlipayPublicKey(),
            config.getSignType()
        );
    }

    boolean shouldFallbackToWapPay(AlipayTradePrecreateResponse response) {
        if (response == null) {
            return false;
        }
        String subCode = response.getSubCode();
        String subMsg = response.getSubMsg();
        if (StringUtils.hasText(subCode)) {
            String normalizedSubCode = subCode.trim().toUpperCase();
            if ("ACQ.ACCESS_FORBIDDEN".equals(normalizedSubCode)
                || "AQC_ACCESS_FORBIDDEN".equals(normalizedSubCode)
                || "ACQ_ACCESS_FORBIDDEN".equals(normalizedSubCode)) {
                return true;
            }
        }

        if (StringUtils.hasText(subMsg)) {
            String normalizedSubMsg = subMsg.trim().toUpperCase();
            return normalizedSubMsg.contains("ACCESS_FORBIDDEN")
                || normalizedSubMsg.contains("ACQ.ACCESS_FORBIDDEN")
                || normalizedSubMsg.contains("AQC_ACCESS_FORBIDDEN")
                || normalizedSubMsg.contains("ACQ_ACCESS_FORBIDDEN");
        }

        return false;
    }

    String createAlipayWapPayForm(AlipayClient alipayClient, PaymentProperties.AlipayConfig config, String orderNo, String amount, String description)
        throws AlipayApiException {
        AlipayTradeWapPayRequest wapRequest = new AlipayTradeWapPayRequest();
        wapRequest.setNotifyUrl(config.getNotifyUrl());
        if (StringUtils.hasText(config.getReturnUrl())) {
            wapRequest.setReturnUrl(config.getReturnUrl());
        }

        AlipayTradeWapPayModel wapModel = new AlipayTradeWapPayModel();
        wapModel.setOutTradeNo(orderNo);
        wapModel.setTotalAmount(amount);
        wapModel.setSubject(description);
        wapModel.setProductCode("QUICK_WAP_WAY");
        wapRequest.setBizModel(wapModel);

        return alipayClient.pageExecute(wapRequest).getBody();
    }

    String createAlipayPagePayForm(AlipayClient alipayClient, PaymentProperties.AlipayConfig config, String orderNo, String amount, String description)
        throws AlipayApiException {
        AlipayTradePagePayRequest pageRequest = new AlipayTradePagePayRequest();
        pageRequest.setNotifyUrl(config.getNotifyUrl());
        if (StringUtils.hasText(config.getReturnUrl())) {
            pageRequest.setReturnUrl(config.getReturnUrl());
        }

        AlipayTradePagePayModel pageModel = new AlipayTradePagePayModel();
        pageModel.setOutTradeNo(orderNo);
        pageModel.setTotalAmount(amount);
        pageModel.setSubject(description);
        pageModel.setProductCode("FAST_INSTANT_TRADE_PAY");
        pageRequest.setBizModel(pageModel);

        return alipayClient.pageExecute(pageRequest).getBody();
    }
    
    private WxPayService buildWxPayServiceForV3(PaymentProperties.WechatPayConfig config, Verifier verifierOverride) {
        WxPayConfig wxPayConfig = new WxPayConfig();
        wxPayConfig.setAppId(config.getAppId());
        wxPayConfig.setMchId(config.getMchId());
        wxPayConfig.setUseSandboxEnv(Boolean.TRUE.equals(config.getUseSandbox()));
        wxPayConfig.setApiV3Key(config.getApiV3Key());
        wxPayConfig.setCertSerialNo(config.getCertSerialNo());

        if (StringUtils.hasText(config.getPrivateKey())) {
            wxPayConfig.setPrivateKeyContent(config.getPrivateKey().getBytes(StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(config.getCertContent())) {
            wxPayConfig.setPrivateCertContent(config.getCertContent().getBytes(StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(config.getCertPath())) {
            try {
                ClassPathResource resource = new ClassPathResource(config.getCertPath().replace("classpath:", ""));
                if (resource.exists()) {
                    wxPayConfig.setKeyPath(resource.getFile().getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("加载微信支付证书文件失败：{}", e.getMessage());
            }
        }

        applyPublicKeyModeToWxPayConfig(wxPayConfig, config);

        if (verifierOverride != null) {
            wxPayConfig.setVerifier(verifierOverride);
        } else {
            PublicKey wechatPayPublicKey = loadWechatPayPublicKey(config.getWechatPayPublicKey());
            wxPayConfig.setVerifier(new WechatPayPublicKeyVerifier(config.getWechatPayPublicKeyId(), wechatPayPublicKey));
        }

        WxPayService wxPayService = new WxPayServiceImpl();
        wxPayService.setConfig(wxPayConfig);
        return wxPayService;
    }

    private void applyPublicKeyModeToWxPayConfig(WxPayConfig wxPayConfig, PaymentProperties.WechatPayConfig config) {
        if (wxPayConfig == null || config == null) {
            return;
        }

        String publicKeyId = config.getWechatPayPublicKeyId();
        String publicKeyPem = config.getWechatPayPublicKey();
        if (!StringUtils.hasText(publicKeyId) || !StringUtils.hasText(publicKeyPem)) {
            return;
        }

        invokeIfPresent(wxPayConfig, "setFullPublicKeyModel", boolean.class, true);
        invokeIfPresent(wxPayConfig, "setFullPublicKeyMode", boolean.class, true);
        invokeIfPresent(wxPayConfig, "setPublicKeyModel", boolean.class, true);
        invokeIfPresent(wxPayConfig, "setPublicKeyMode", boolean.class, true);

        invokeIfPresent(wxPayConfig, "setPublicKeyId", String.class, publicKeyId);
        invokeIfPresent(wxPayConfig, "setPublicKeySerialNo", String.class, publicKeyId);
        invokeIfPresent(wxPayConfig, "setWechatPayPublicKeyId", String.class, publicKeyId);
        invokeIfPresent(wxPayConfig, "setWechatPayPublicKeySerialNo", String.class, publicKeyId);
        invokeIfPresent(wxPayConfig, "setWechatPaySerialNo", String.class, publicKeyId);
        invokeIfPresent(wxPayConfig, "setWechatPaySerialNumber", String.class, publicKeyId);

        invokeIfPresent(wxPayConfig, "setPublicKeyString", String.class, publicKeyPem);
        invokeIfPresent(wxPayConfig, "setPublicKeyPem", String.class, publicKeyPem);
        invokeIfPresent(wxPayConfig, "setPublicKeyContent", String.class, publicKeyPem);
        invokeIfPresent(wxPayConfig, "setPublicKeyContent", byte[].class, publicKeyPem.getBytes(StandardCharsets.UTF_8));

        invokeIfPresent(wxPayConfig, "setWechatPayPublicKey", String.class, publicKeyPem);
        invokeIfPresent(wxPayConfig, "setWechatPayPublicKeyContent", String.class, publicKeyPem);
        invokeIfPresent(wxPayConfig, "setWechatPayPublicKeyContent", byte[].class, publicKeyPem.getBytes(StandardCharsets.UTF_8));
    }

    private static void invokeIfPresent(Object target, String methodName, Class<?> paramType, Object paramValue) {
        if (target == null) {
            return;
        }
        try {
            target.getClass().getMethod(methodName, paramType).invoke(target, paramValue);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }
    }
    
    /**
     * 验证支付宝支付回调签名
     * 
     * @param callbackData 回调数据（Map格式）
     * @param configJson 支付配置JSON（可选）
     * @return 是否验证通过
     */
    public boolean verifyAlipayCallback(Map<String, String> callbackData, String configJson) {
        try {
            log.info("验证支付宝支付回调签名：订单号={}", callbackData.get("out_trade_no"));
            
            // 解析配置
            PaymentProperties.AlipayConfig config = parseAlipayConfig(configJson);
            
            // 获取签名
            String sign = callbackData.get("sign");
            if (!StringUtils.hasText(sign)) {
                log.error("支付宝回调数据中缺少签名");
                return false;
            }
            
            // 获取签名类型
            String signType = callbackData.getOrDefault("sign_type", config.getSignType());
            
            // 移除签名和签名类型，构建待签名字符串
            TreeMap<String, String> sortedParams = new TreeMap<>(callbackData);
            sortedParams.remove("sign");
            sortedParams.remove("sign_type");
            
            StringBuilder content = new StringBuilder();
            for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
                if (StringUtils.hasText(entry.getValue())) {
                    content.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
            }
            if (content.length() > 0) {
                content.deleteCharAt(content.length() - 1); // 移除最后一个&
            }
            
            // 使用支付宝SDK验证签名
            boolean verified = com.alipay.api.internal.util.AlipaySignature.rsaCheckV1(
                callbackData,
                config.getAlipayPublicKey(),
                config.getCharset(),
                signType
            );
            
            if (verified) {
                log.info("支付宝支付回调签名验证通过");
            } else {
                log.error("支付宝支付回调签名验证失败");
            }
            
            return verified;
            
        } catch (Exception e) {
            log.error("验证支付宝支付回调签名失败", e);
            return false;
        }
    }
    
    /**
     * 解析微信支付配置
     * 仅从数据库configJson解析，不使用配置文件兜底
     * @throws BusinessException 如果配置不存在或不完整
     */
    private PaymentProperties.WechatPayConfig parseWechatConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付未配置，请在后台管理系统中进行配置");
        }

        PaymentProperties.WechatPayConfig config = new PaymentProperties.WechatPayConfig();
        
        try {
            JsonNode jsonNode = objectMapper.readTree(configJson);
            
            // 必填字段校验
            if (!jsonNode.has("appId") || !StringUtils.hasText(jsonNode.get("appId").asText())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：缺少AppID");
            }
            if (!jsonNode.has("mchId") || !StringUtils.hasText(jsonNode.get("mchId").asText())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：缺少商户号");
            }
            // 回调地址必填校验
            if (!jsonNode.has("notifyUrl") || !StringUtils.hasText(jsonNode.get("notifyUrl").asText())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：缺少回调地址");
            }

            config.setAppId(jsonNode.get("appId").asText());
            config.setMchId(jsonNode.get("mchId").asText());
            
            // 选填字段
            if (jsonNode.has("mchKey")) config.setMchKey(jsonNode.get("mchKey").asText());
            
            // 敏感字段解密
            if (jsonNode.has("apiV3Key")) config.setApiV3Key(tryDecrypt(jsonNode.get("apiV3Key").asText()));
            if (jsonNode.has("certSerialNo")) config.setCertSerialNo(jsonNode.get("certSerialNo").asText());
            if (jsonNode.has("wechatPayPublicKeyId")) config.setWechatPayPublicKeyId(jsonNode.get("wechatPayPublicKeyId").asText());
            if (jsonNode.has("wechatPayPublicKey")) config.setWechatPayPublicKey(tryDecrypt(jsonNode.get("wechatPayPublicKey").asText()));
            if (jsonNode.has("privateKey")) config.setPrivateKey(tryDecrypt(jsonNode.get("privateKey").asText()));
            if (jsonNode.has("certContent")) config.setCertContent(tryDecrypt(jsonNode.get("certContent").asText()));
            
            // 回调地址
            config.setNotifyUrl(jsonNode.get("notifyUrl").asText());
            
            // 其他配置
            if (jsonNode.has("certPath")) config.setCertPath(jsonNode.get("certPath").asText());
            if (jsonNode.has("useSandbox")) config.setUseSandbox(jsonNode.get("useSandbox").asBoolean());

            if (!StringUtils.hasText(config.getApiV3Key())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：缺少APIv3密钥");
            }
            if (!StringUtils.hasText(config.getCertSerialNo())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：缺少证书序列号");
            }
            if (!StringUtils.hasText(config.getWechatPayPublicKeyId())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：缺少微信支付平台公钥ID");
            }
            if (!StringUtils.hasText(config.getWechatPayPublicKey())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：缺少微信支付平台公钥");
            }
            if (!StringUtils.hasText(config.getPrivateKey())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：缺少商户API私钥");
            }
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信支付配置失败", e);
            throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "解析微信支付配置失败：" + e.getMessage());
        }
        
        return config;
    }
    
    /**
     * 解析支付宝支付配置
     * 仅从数据库configJson解析，不使用配置文件兜底
     * @throws BusinessException 如果配置不存在或不完整
     */
    private PaymentProperties.AlipayConfig parseAlipayConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "支付宝支付未配置，请在后台管理系统中进行配置");
        }

        PaymentProperties.AlipayConfig config = new PaymentProperties.AlipayConfig();
        
        try {
            JsonNode jsonNode = objectMapper.readTree(configJson);
            
            // 必填字段校验
            if (!jsonNode.has("appId") || !StringUtils.hasText(jsonNode.get("appId").asText())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "支付宝配置错误：缺少AppID");
            }
            // 回调地址必填校验
            if (!jsonNode.has("notifyUrl") || !StringUtils.hasText(jsonNode.get("notifyUrl").asText())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "支付宝配置错误：缺少回调地址");
            }
            
            config.setAppId(jsonNode.get("appId").asText());
            
            // 敏感字段解密
            if (jsonNode.has("privateKey")) config.setPrivateKey(tryDecrypt(jsonNode.get("privateKey").asText()));
            if (jsonNode.has("alipayPublicKey")) config.setAlipayPublicKey(tryDecrypt(jsonNode.get("alipayPublicKey").asText()));
            if (jsonNode.has("appCertContent")) config.setAppCertContent(tryDecrypt(jsonNode.get("appCertContent").asText()));
            if (jsonNode.has("alipayRootCertContent")) config.setAlipayRootCertContent(tryDecrypt(jsonNode.get("alipayRootCertContent").asText()));
            if (jsonNode.has("alipayCertContent")) config.setAlipayCertContent(tryDecrypt(jsonNode.get("alipayCertContent").asText()));
            
            // 回调地址
            config.setNotifyUrl(jsonNode.get("notifyUrl").asText());
            
            // 其他配置
            if (jsonNode.has("returnUrl")) config.setReturnUrl(jsonNode.get("returnUrl").asText());
            // 网关地址默认为正式环境，如果配置中有则覆盖
            config.setGatewayUrl(jsonNode.has("gatewayUrl") && StringUtils.hasText(jsonNode.get("gatewayUrl").asText()) 
                ? jsonNode.get("gatewayUrl").asText() 
                : "https://openapi.alipay.com/gateway.do");
                
            if (jsonNode.has("signType")) config.setSignType(jsonNode.get("signType").asText());
            if (jsonNode.has("charset")) config.setCharset(jsonNode.get("charset").asText());
            if (jsonNode.has("format")) config.setFormat(jsonNode.get("format").asText());

            if (!StringUtils.hasText(config.getPrivateKey())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "支付宝配置错误：缺少应用私钥");
            }

            boolean useCertMode = StringUtils.hasText(config.getAppCertContent())
                && StringUtils.hasText(config.getAlipayRootCertContent())
                && StringUtils.hasText(config.getAlipayCertContent());

            if (!useCertMode && !StringUtils.hasText(config.getAlipayPublicKey())) {
                throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "支付宝配置错误：缺少支付宝公钥");
            }
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析支付宝支付配置失败", e);
            throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "解析支付宝支付配置失败：" + e.getMessage());
        }
        
        return config;
    }

    /**
     * 尝试解密敏感信息
     * 如果解密失败（可能本来就是明文），则返回原值
     */
    private String tryDecrypt(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            // 简单判断是否可能是加密字符串（通常AES加密后Base64编码的字符串不包含空格，长度是4的倍数等，但这里直接试着解密最稳妥）
            String decrypted = AesEncryptUtil.decrypt(value);
            return StringUtils.hasText(decrypted) ? decrypted : value;
        } catch (Exception e) {
            // 解密失败，可能是明文
            return value;
        }
    }

    private PublicKey loadWechatPayPublicKey(String publicKeyPem) {
        if (!StringUtils.hasText(publicKeyPem)) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：微信支付平台公钥为空");
        }
        try {
            String normalized = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PAYMENT_CONFIG_ERROR.getCode(), "微信支付配置错误：微信支付平台公钥解析失败");
        }
    }

    private static final class WechatPayPublicKeyVerifier implements Verifier {
        private final String wechatPayPublicKeyId;
        private final PublicKey publicKey;
        private final X509Certificate certificate;

        private WechatPayPublicKeyVerifier(String wechatPayPublicKeyId, PublicKey publicKey) {
            this.wechatPayPublicKeyId = wechatPayPublicKeyId;
            this.publicKey = publicKey;
            this.certificate = new PublicKeyOnlyX509Certificate(publicKey, wechatPayPublicKeyId);
        }

        @Override
        public boolean verify(String serialNumber, byte[] message, String signature) {
            String expectedSerial = normalizeHexSerial(wechatPayPublicKeyId);
            String actualSerial = normalizeHexSerial(serialNumber);
            if (StringUtils.hasText(expectedSerial) && StringUtils.hasText(actualSerial) && !expectedSerial.equals(actualSerial)) {
                return false;
            }
            try {
                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(publicKey);
                verifier.update(message);
                return verifier.verify(Base64.getDecoder().decode(signature));
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public X509Certificate getValidCertificate() {
            return certificate;
        }

        private static String normalizeHexSerial(String value) {
            if (!StringUtils.hasText(value)) {
                return "";
            }
            String trimmed = value.trim();
            int i = 0;
            while (i < trimmed.length() && trimmed.charAt(i) == '0') {
                i++;
            }
            String normalized = trimmed.substring(i);
            return normalized.isEmpty() ? "0" : normalized.toUpperCase(Locale.ROOT);
        }
    }

    private static final class PublicKeyOnlyX509Certificate extends X509Certificate {
        private final PublicKey publicKey;
        private final BigInteger serialNumber;
        private final Date notBefore;
        private final Date notAfter;

        private PublicKeyOnlyX509Certificate(PublicKey publicKey, String publicKeyId) {
            this.publicKey = publicKey;
            this.serialNumber = parseHexOrDefault(publicKeyId);
            this.notBefore = Date.from(Instant.EPOCH);
            this.notAfter = Date.from(Instant.parse("9999-12-31T23:59:59Z"));
        }

        private static BigInteger parseHexOrDefault(String value) {
            if (!StringUtils.hasText(value)) {
                return BigInteger.ZERO;
            }
            try {
                return new BigInteger(value, 16);
            } catch (Exception e) {
                return BigInteger.ZERO;
            }
        }

        @Override
        public void checkValidity() {
        }

        @Override
        public void checkValidity(Date date) {
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public BigInteger getSerialNumber() {
            return serialNumber;
        }

        @Override
        public java.security.Principal getIssuerDN() {
            return () -> "CN=WechatPayPublicKey";
        }

        @Override
        public java.security.Principal getSubjectDN() {
            return () -> "CN=WechatPayPublicKey";
        }

        @Override
        public Date getNotBefore() {
            return notBefore;
        }

        @Override
        public Date getNotAfter() {
            return notAfter;
        }

        @Override
        public byte[] getTBSCertificate() {
            return new byte[0];
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "SHA256withRSA";
        }

        @Override
        public String getSigAlgOID() {
            return "";
        }

        @Override
        public byte[] getSigAlgParams() {
            return new byte[0];
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return null;
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException {
            return new byte[0];
        }

        @Override
        public void verify(PublicKey key) throws CertificateException {
        }

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException {
        }

        @Override
        public String toString() {
            return "WechatPayPublicKeyCertificate";
        }

        @Override
        public PublicKey getPublicKey() {
            return publicKey;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs() {
            return null;
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return null;
        }
    }
}

