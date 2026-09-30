# mPay voice support calling

mPay voice support is an explicit two-way call between an authorized portal staff member and a customer.

## Call flow
1. An admin gives CALL_CUSTOMER to a portal role such as MANAGER or TEAM_LEAD, or overrides one staff account with ALLOW/DENY.
2. The authorized staff member opens a customer profile and selects Start voice call.
3. The customer receives a native incoming-call notification in Android.
4. The customer explicitly chooses Accept or Decline.
5. Microphone/audio starts only after acceptance.
6. Either participant can end the call. The backend records the call state; audio is not recorded.

## Realtime architecture
- REST API: authorization and authoritative call state.
- WebSocket /ws/calls: short-lived, call-scoped signaling only.
- WebRTC: actual two-way audio between the two endpoints.
- Firebase Cloud Messaging: wakes the Android client for an incoming call.
- PostgreSQL: call state, participants, registered push devices, and access overrides.

The WebSocket does not carry the audio stream.

## Backend configuration
Required in a production deployment:
- MPAY_CALL_ICE_SERVERS: comma/space separated STUN/TURN URLs.
- MPAY_CALL_TURN_USERNAME: TURN username when using TURN.
- MPAY_CALL_TURN_CREDENTIAL: TURN credential when using TURN.
- MPAY_FIREBASE_SERVICE_ACCOUNT_JSON_BASE64: base64-encoded Firebase Admin service-account JSON.

The default STUN server is suitable for development, not a production reliability guarantee. Configure a TURN service before relying on calls across restrictive NATs.

## Android CI configuration
Add these GitHub Actions secrets before producing a release APK/AAB with working FCM:
- MPAY_FIREBASE_API_KEY
- MPAY_FIREBASE_APP_ID
- MPAY_FIREBASE_PROJECT_ID
- MPAY_FIREBASE_SENDER_ID

The app is intentionally able to build without them, but FCM delivery is disabled until they are configured.

## Permissions
Android asks for RECORD_AUDIO only when the customer chooses to answer. Incoming-call notifications use the Android calling notification pattern. No READ_CALL_LOG or WRITE_CALL_LOG permission is used.

## Production checklist
- HTTPS for admin web and API.
- Production Firebase Android app configured for package com.client.mpay.
- Firebase Admin credentials configured on the backend.
- TURN server configured and reachable from both clients.
- Android notification and microphone permissions accepted by the test device.
- At least one portal role explicitly granted CALL_CUSTOMER.
- Test accept, decline, caller cancellation, timeout, and hang-up from both ends.