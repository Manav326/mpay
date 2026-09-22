package com.recharge.client.core.payment;

import android.app.Activity;
import android.webkit.WebView;

import java.util.HashMap;
import java.util.Map;

import com.payu.checkoutpro.PayUCheckoutPro;
import com.payu.ui.model.listeners.PayUCheckoutProListener;
import com.payu.checkoutpro.utils.PayUCheckoutProConstants;
import com.payu.base.models.PayUPaymentParams;
import com.payu.base.models.ErrorResponse;
import com.payu.ui.model.listeners.PayUHashGenerationListener;

public final class PayUCheckoutBridge {
    private PayUCheckoutBridge() {}

    public interface Callback {
        void onPaymentSuccess(Object response);
        void onPaymentFailure(Object response);
        void onPaymentCancel(boolean isTxnInitiated);
        void onError(String message);
        void onGenerateHash(String hashName, String hashString, String postSalt, String hashType, PayUHashCallback callback);
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
            String vasForMobileSdkHash,
            String paymentRelatedDetailsHash,
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
                .setAdditionalParams(buildStaticHashes(vasForMobileSdkHash, paymentRelatedDetailsHash))
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
                String postSalt = valueMap == null ? null : valueMap.get(PayUCheckoutProConstants.CP_POST_SALT);
                String hashType = valueMap == null ? null : valueMap.get(PayUCheckoutProConstants.CP_HASH_TYPE);
                if (hashName == null || hashName.trim().isEmpty() || hashString == null || hashString.trim().isEmpty()) {
                    callback.onError("PayU requested an invalid payment hash");
                    return;
                }
                callback.onGenerateHash(hashName, hashString, postSalt, hashType, hash -> {
                    HashMap<String, String> hashMap = new HashMap<>();
                    hashMap.put(hashName, hash);
                    listener.onHashGenerated(hashMap);
                });
            }
            @Override public void setWebViewProperties(WebView webView, Object bank) { }
        });
    }

    private static HashMap<String, Object> buildStaticHashes(
            String vasForMobileSdkHash,
            String paymentRelatedDetailsHash
    ) {
        HashMap<String, Object> additionalParams = new HashMap<>();
        if (vasForMobileSdkHash != null && !vasForMobileSdkHash.trim().isEmpty()) {
            additionalParams.put(PayUCheckoutProConstants.CP_VAS_FOR_MOBILE_SDK, vasForMobileSdkHash);
        }
        if (paymentRelatedDetailsHash != null && !paymentRelatedDetailsHash.trim().isEmpty()) {
            additionalParams.put(PayUCheckoutProConstants.CP_PAYMENT_RELATED_DETAILS_FOR_MOBILE_SDK, paymentRelatedDetailsHash);
        }
        return additionalParams;
    }
    public static String getResponseValue(Object response, String key) {
        if (!(response instanceof Map)) return null;
        Object value = ((Map<?, ?>) response).get(key);
        return value instanceof String ? (String) value : null;
    }
}
