# mPay authentication and branding

- App label: `mPay`
- Version: `1.0.0` (versionCode 10)
- The canonical PNG brand mark is `admin-web/public/branding/mpay-logo.png`.
- Android builds generate `app/src/main/res/drawable-nodpi/mpay_logo.png` and the Play Store asset from that canonical file before compilation.
- Authentication branding is a coded unit: logo mark + `mPay` + `SECURE • SIMPLE • SMART`.
- The same generated Android PNG is used by the launcher and PayU merchant branding, where the native SDK accepts an image-only logo.
- Login provides Forgot password.
- Sign Up contains Full name, Email address, Mobile number, Password, and Confirm password.
