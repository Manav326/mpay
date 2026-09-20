# Password reset OTP modes

The Android client supports both backend password-reset modes.

- Twilio mode: enter the OTP received by SMS.
- Mock mode: the backend returns a development-only `demoOtp`, which Android displays on the reset screen.

No Twilio credentials are stored in the APK.
