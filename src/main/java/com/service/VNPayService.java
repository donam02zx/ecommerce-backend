package com.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class VNPayService {

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${vnpay.payment-url}")
    private String paymentUrl;

    @Value("${vnpay.return-url}")
    private String returnUrl;

    /**
     * Tạo VNPay payment URL cho order
     */
    public String createPaymentUrl(Long orderId, long amountVnd, String orderInfo, String ipAddress) {
        String vnpVersion = "2.1.0";
        String vnpCommand = "pay";
        String vnpCurrCode = "VND";
        String vnpLocale = "vn";
        String vnpOrderType = "other";

        // VNPay yêu cầu amount * 100
        String vnpAmount = String.valueOf(amountVnd * 100);
        String vnpTxnRef = orderId + "_" + System.currentTimeMillis();

        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
        fmt.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String vnpCreateDate = fmt.format(new Date());

        // Build params — phải sort theo thứ tự alphabet
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", vnpVersion);
        params.put("vnp_Command", vnpCommand);
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", vnpAmount);
        params.put("vnp_CurrCode", vnpCurrCode);
        params.put("vnp_TxnRef", vnpTxnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", vnpOrderType);
        params.put("vnp_Locale", vnpLocale);
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_CreateDate", vnpCreateDate);

        // Build query string để tạo chữ ký
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            hashData.append(entry.getKey()).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
            hashData.append("&");
            query.append("&");
        }
        // Xóa dấu & cuối
        hashData.deleteCharAt(hashData.length() - 1);
        query.deleteCharAt(query.length() - 1);

        String secureHash = hmacSHA512(hashSecret, hashData.toString());
        String finalUrl = paymentUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        log.info("VNPay URL created for orderId={}, txnRef={}", orderId, vnpTxnRef);
        return finalUrl;
    }

    /**
     * Verify chữ ký từ VNPay callback
     */
    public boolean verifySignature(Map<String, String> params) {
        String receivedHash = params.get("vnp_SecureHash");
        if (receivedHash == null) return false;

        // Loại bỏ vnp_SecureHash và vnp_SecureHashType trước khi verify
        Map<String, String> verifyParams = new TreeMap<>(params);
        verifyParams.remove("vnp_SecureHash");
        verifyParams.remove("vnp_SecureHashType");

        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : verifyParams.entrySet()) {
            hashData.append(entry.getKey()).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII))
                    .append("&");
        }
        hashData.deleteCharAt(hashData.length() - 1);

        String computedHash = hmacSHA512(hashSecret, hashData.toString());
        return computedHash.equalsIgnoreCase(receivedHash);
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC SHA512", e);
        }
    }
}