# Admin Web CORS setup

The backend now allows browser requests from the local Next.js admin portal by default:

- http://localhost:3000
- http://127.0.0.1:3000

CORS is enabled through Spring Security and OPTIONS preflight requests are permitted.

For deployment, override `MPAY_CORS_ALLOWED_ORIGINS` with a comma-separated list, for example:

`MPAY_CORS_ALLOWED_ORIGINS=https://admin.example.com`

Do not use `*` together with credentials. The current JWT Authorization-header setup does not require browser cookies, so `allowCredentials` is disabled.
