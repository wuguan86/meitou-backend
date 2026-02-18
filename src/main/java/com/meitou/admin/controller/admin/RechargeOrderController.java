package com.meitou.admin.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.meitou.admin.annotation.SiteScope;
import com.meitou.admin.common.Result;
import com.meitou.admin.entity.RechargeOrder;
import com.meitou.admin.dto.admin.RechargeOrderQueryRequest;
import com.meitou.admin.service.admin.RechargeOrderAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/recharge-orders")
public class RechargeOrderController {

    @Autowired
    private RechargeOrderAdminService rechargeOrderService;

    @GetMapping
    @SiteScope
    public Result<IPage<RechargeOrder>> getOrders(
            @RequestParam(required = true) Long siteId,
            RechargeOrderQueryRequest request) {
        request.setSiteId(siteId);
        return Result.success(rechargeOrderService.getRechargeOrders(request));
    }

    @GetMapping("/stats")
    @SiteScope
    public Result<Map<String, Object>> getStats(
            @RequestParam(required = true) Long siteId,
            RechargeOrderQueryRequest request) {
        request.setSiteId(siteId);
        BigDecimal total = rechargeOrderService.getTotalAmount(request);
        return Result.success(Map.of("totalAmount", total));
    }

    /**
     * 导出充值订单
     */
    @GetMapping("/export")
    @SiteScope
    public void exportOrders(
            @RequestParam(required = true) Long siteId,
            RechargeOrderQueryRequest request,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        request.setSiteId(siteId);
        java.util.List<RechargeOrder> list = rechargeOrderService.getExportList(request);

        response.setContentType("text/csv; charset=UTF-8");
        String filename = "recharge_orders_" + System.currentTimeMillis() + ".csv";
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        
        try (java.io.OutputStream os = response.getOutputStream()) {
            // UTF-8 BOM for Excel compatibility
            os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            
            try (java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(os, java.nio.charset.StandardCharsets.UTF_8);
                 java.io.PrintWriter writer = new java.io.PrintWriter(osw)) {
                 
                writer.println("充值时间,用户,手机号,充值金额,获得积分,支付渠道,状态,订单号");

                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                for (RechargeOrder order : list) {
                    // Add \t to prevent Excel from formatting as scientific notation or #####
                    String time = order.getCreatedAt() != null ? "\t" + order.getCreatedAt().format(formatter) : "";
                    String username = order.getUser() != null ? order.getUser().getUsername() : "";
                    String phone = (order.getUser() != null && order.getUser().getPhone() != null) ? "\t" + order.getUser().getPhone() : "";
                    String amount = order.getAmount() != null ? order.getAmount().toString() : "0";
                    String points = order.getPoints() != null ? order.getPoints().toString() : "0";
                    
                    String paymentType = order.getPaymentType();
                    if ("wechat".equals(paymentType)) paymentType = "微信支付";
                    else if ("alipay".equals(paymentType)) paymentType = "支付宝支付";
                    else if ("system".equals(paymentType)) paymentType = "系统赠送";
                    
                    String status = order.getStatus();
                    if ("paid".equals(status)) status = "已支付";
                    else if ("pending".equals(status)) status = "待支付";
                    else if ("paying".equals(status)) status = "支付中";
                    else if ("cancelled".equals(status)) status = "已取消";
                    else if ("refunded".equals(status)) status = "已退款";
                    else if ("failed".equals(status)) status = "支付失败";

                    String orderNo = order.getOrderNo() != null ? "\t" + order.getOrderNo() : "";
                    username = escapeCsv(username);
                    
                    writer.println(String.join(",", time, username, phone, amount, points, paymentType, status, orderNo));
                }
                writer.flush();
            }
        }
    }
    
    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
