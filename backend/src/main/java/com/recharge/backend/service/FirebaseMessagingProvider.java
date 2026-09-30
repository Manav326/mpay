package com.recharge.backend.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Keeps Firebase Admin SDK initialization in Java because the Admin SDK's
 * FirebaseApp type is not resolving reliably through the Kotlin compiler in
 * this project. The public surface exposed to Kotlin is only FirebaseMessaging.
 */
public final class FirebaseMessagingProvider {

    private FirebaseMessagingProvider() {
    }

    public static FirebaseMessaging get(String firebaseServiceAccountJsonBase64) {
        try {
            synchronized (FirebaseApp.class) {
                if (FirebaseApp.getApps().isEmpty()) {
                    String encoded = firebaseServiceAccountJsonBase64 == null
                            ? ""
                            : firebaseServiceAccountJsonBase64.trim();

                    if (!encoded.isEmpty()) {
                        byte[] jsonBytes = Base64.getDecoder().decode(encoded);
                        GoogleCredentials credentials = GoogleCredentials.fromStream(
                                new ByteArrayInputStream(jsonBytes)
                        );
                        FirebaseApp.initializeApp(
                                FirebaseOptions.builder()
                                        .setCredentials(credentials)
                                        .build()
                        );
                    } else {
                        FirebaseApp.initializeApp();
                    }
                }
            }
            return FirebaseMessaging.getInstance();
        } catch (Exception ignored) {
            return null;
        }
    }
}
