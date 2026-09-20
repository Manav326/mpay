# mPay authentication additions

## Registration
The register request now accepts `name` and `email` in addition to the existing mobile/password fields. Name is stored in `users.name`; email is normalized to lowercase when supplied.

## Forgot password
Two public endpoints are available:

`POST /api/v1/auth/forgot-password`
```json
{"mobile":"9876543210"}
```

`POST /api/v1/auth/reset-password`
```json
{"mobile":"9876543210","otp":"123456","newPassword":"new-password"}
```

The password-reset flow uses **Twilio Verify v2** for real SMS OTP delivery and verification. The Android client never receives the OTP from the backend.

OTP verifications are configured for a 10-minute local validity window, and a verification can be used only once. The server also limits local failed attempts to five before requiring a new OTP.

### Twilio configuration
Put the following secrets in the external, gitignored `config/application-secrets.yml`:

```yaml
app:
  twilio:
    account-sid: ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
    auth-token: your_twilio_auth_token
    verify-service-sid: VAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

The non-secret switch in `src/main/resources/application.yml` is:

```yaml
app:
  auth:
    password-reset:
      otp-ttl-seconds: 600
      twilio:
        enabled: true
```

The backend sends the user's 10-digit Indian mobile to Twilio Verify in E.164 form (`+91XXXXXXXXXX`) and uses Twilio's verification-check endpoint when the user submits the OTP. Twilio Verify manages the actual OTP generation, SMS delivery and verification lifecycle.

For a Twilio trial account, Twilio requires non-Twilio recipient phone numbers to be verified in the Twilio Console before test SMS messages can be sent.

Keep all Twilio credentials on the backend only; never put them in the Android application.
