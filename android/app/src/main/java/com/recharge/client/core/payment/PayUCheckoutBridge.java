package com.recharge.client.core.payment;

import android.app.Activity;
import android.webkit.WebView;

import java.util.HashMap;
import java.util.Map;

import in.payu.checkoutpro.PayUCheckoutPro;
import in.payu.checkoutpro.PayUCheckoutProListener;
import in.payu.checkoutpro.constants.PayUCheckoutProConstants;
import in.payu.checkoutpro.models.PayUPaymentParams;
import in.payu.checkoutpro.utils.ErrorResponse;
import in.payu.checkoutpro.utils.PayUHashGenerationListener;

public final class PayUCheckoutBridge {
    private PayUCheckoutBridge() {}

    public interface Callback {
        void onPaymentSuccess(Object response);
        void onPaymentFailure(Object response);
        void onPaymentCancel(boolean isTxnInitiated);
        void onError(String message);
        void onGenerateHash(String hashName, String hashString, PayUHashCallback callback);
    }

    public interface PayUHashCallback {
        void onHashGenerated(String hash);
    }

    public static void open(
            Activity activity,
            String amount,
            boolean isProduction,
            String productInfo,
            String key,
            String phone,
            String transactionId,
            String firstName,
            String email,
            String surl,
            String furl,
            String userCredential,
            Callback callback
    ) {
        PayUPaymentParams params = new PayUPaymentParams.Builder()
                .setAmount(amount)
                .setIsProduction(isProduction)
                .setProductInfo(productInfo)
                .setKey(key)
                .setPhone(phone)
                .setTransactionId(transactionId)
                .setFirstName(firstName)
                .setEmail(email)
                .setSurl(surl)
                .setFurl(furl)
                .setUserCredential(userCredential)
                .build();

        PayUCheckoutPro.open(activity, params, new PayUCheckoutProListener() {
            @Override public void onPaymentSuccess(Object response) { callback.onPaymentSuccess(response); }
            @Override public void onPaymentFailure(Object response) { callback.onPaymentFailure(response); }
            @Override public void onPaymentCancel(boolean isTxnInitiated) { callback.onPaymentCancel(isTxnInitiated); }
            @Override public void onError(ErrorResponse errorResponse) {
                callback.onError(errorResponse == null ? "PayU checkout error" : errorResponse.getErrorMessage());
            }
            @Override public void generateHash(HashMap<String, String> valueMap, PayUHashGenerationListener listener) {
                String hashName = valueMap == null ? null : valueMap.get(PayUCheckoutProConstants.CP_HASH_NAME);
                String hashString = valueMap == null ? null : valueMap.get(PayUCheckoutProConstants.CP_HASH_STRING);
                if (hashName == null || hashName.trim().isEmpty() || hashString == null || hashString.trim().isEmpty()) {
                    callback.onError("PayU requested an invalid payment hash");
                    return;
                }
                callback.onGenerateHash(hashName, hashString, hash -> {
                    HashMap<String, String> hashMap = new HashMap<>();
                    hashMap.put(hashName, hash);
                    listener.onHashGenerated(hashMap);
                });
            }
            @Override public void setWebViewProperties(WebView webView, Object bank) { }
        });
    }

    public static String getResponseValue(Object response, String key) {
        if (!(response instanceof Map)) return null;
        Object value = ((Map<?, ?>) response).get(key);
        return value instanceof String ? (String) value : null;
    }
}
