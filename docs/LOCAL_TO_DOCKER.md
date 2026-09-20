# Local -> unified Docker checklist

1. Stop any old standalone backend/web processes if you want to free ports 3000/8080.
2. Take a PostgreSQL dump before moving the existing database into the new Compose volume.
3. Stop the old standalone PostgreSQL/Redis containers only after the backup is safe.
4. Copy the current backend secrets file into `backend/config/application-secrets.yml`.
5. Copy `.env.example` to `.env` and keep `MPAY_LAN_IP=192.168.31.47`.
6. Run `docker compose up -d --build`.
7. Check `http://localhost:8080/actuator/health`.
8. Open `http://localhost:3000` for the Admin Web.
9. From a phone on the same Wi-Fi, use the Android build whose API base is `http://192.168.31.47:8080/`.
10. Verify admin login, manager permissions, client app login, recharge, wallet and password reset.
11. Only after local verification, configure DNS and run the production Caddy profile.
