# Password reset OTP provider modes

The password reset flow supports two explicit delivery modes:

```yaml
app:
  auth:
    password-reset:
      provider: mock # mock | twilio
```

## Mock mode

Use `provider: mock` while Twilio credentials are not ready.

- No Twilio credentials are required.
- A random 6-digit OTP is generated.
- The OTP is stored hashed in `password_reset_otps`.
- The forgot-password response includes `demoOtp` only in mock mode.
- Android displays the mock OTP so the complete reset flow can be tested.

## Twilio mode

Set `provider: twilio` after adding these backend-only secrets to `config/application-secrets.yml`:

```yaml
app:
  twilio:
    account-sid: ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
    auth-token: YOUR_TWILIO_AUTH_TOKEN
    verify-service-sid: VAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

In Twilio mode `demoOtp` is always null. The OTP is delivered only by Twilio Verify SMS.

If Twilio credentials are missing/invalid or Twilio is unavailable, the backend returns HTTP 503 with a safe configuration/provider error. It does **not** silently fall back to mock, because silently falling back could make a production reset flow appear successful even though no SMS was delivered.

To continue development without Twilio, switch the provider explicitly back to `mock`.
