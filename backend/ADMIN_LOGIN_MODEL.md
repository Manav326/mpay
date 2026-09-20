# Admin Login Model

Admin and Manager accounts use the same authentication endpoint and JWT mechanism as Android clients:

`POST /api/v1/auth/login`

The backend determines the user's role from PostgreSQL and places it into the JWT. Admin portal authorization is enforced server-side by the existing admin services:

- ADMIN can see admins, managers and clients.
- MANAGER can see clients only.
- CLIENT cannot access `/api/v1/admin/**` and is rejected by the admin services.

There is no separate `/api/v1/auth/admin-login` endpoint and no application bootstrap credential mechanism.
