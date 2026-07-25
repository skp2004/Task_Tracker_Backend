# TaskTracker — Deployment Guide

> **⚠️ TEMPLATE ONLY. Never put real credentials here.**
> All secrets are set in the Render dashboard (Environment Variables) — never in git.

---

## 🚀 Hosting on Render (Current Setup)

TaskTracker backend is now hosted on **Render.com** using the `render.yaml` blueprint in this repo.

### One-time Setup on Render
1. Go to [render.com](https://render.com) → **New** → **Blueprint**
2. Connect your GitHub repo → select `main` branch
3. Render will detect `render.yaml` and configure the service automatically
4. Set the following **Environment Variables** in the Render dashboard:

```
DB_URL        = jdbc:postgresql://<host>:5432/postgres?sslmode=require&options=endpoint%3D<id>
DB_USERNAME   = <from-supabase>
DB_PASSWORD   = <from-supabase>
JWT_SECRET    = <generate: openssl rand -base64 48>
```

### Auto-Deploy
Every push to `main` triggers Render to rebuild and redeploy automatically.
No SSH keys, EC2 instances, or systemd involved.

### Health Check
Render monitors: `GET /api/health` → should return `{"status":"UP"}`

---

## 💻 Running Locally (Development)

Create a `.env` file in the `backend/` directory (already in `.gitignore`):

```env
DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require&options=endpoint%3D<id>
DB_USERNAME=<from-supabase>
DB_PASSWORD=<from-supabase>
JWT_SECRET=<generate-with-openssl-rand>
CORS_ALLOWED_ORIGINS=http://localhost:8081,http://localhost:3000
SERVER_PORT=8081
```

Then run:
```bash
cd backend
./mvnw spring-boot:run
```

Swagger UI → http://localhost:8081/swagger-ui.html

---

## ⚡ Troubleshooting Connection Pooler (`EMAXCONNSESSION`)

If Render deployment fails with:
`FATAL: (EMAXCONNSESSION) max clients reached in session mode - max clients are limited to pool_size: 15`

### Cause:
Render perform zero-downtime (rolling) deployments where a **new container** starts up before the **old container** is shut down.
If `maximum-pool-size` was set to 10:
`10 (old instance) + 10 (new instance) = 20 connections > 15 max limit`

### Solution:
1. **Hikari Pool Sizing**: `maximum-pool-size` is configured to `4` in `application.yml` (and `minimum-idle: 1`, `initialization-fail-timeout: 30000`).
   - `4 (old) + 4 (new) = 8 connections`, comfortably below Supabase's 15 connection cap.
   - Java 21 Virtual Threads efficiently process concurrent requests with this small pool without thread starvation.
2. **Supabase Direct Connection (Alternative)**:
   - For direct database connection without PgBouncer session limits, use Supabase Direct Connection on port `5432` (`db.xxx.supabase.co:5432`) or Transaction mode Pooler with appropriate URL options.

---

## 🔄 Rotating Credentials

1. **Supabase DB password**: Supabase Dashboard → Project Settings → Database → Reset password → update on Render dashboard.
2. **JWT secret**: Generate new value (`openssl rand -base64 48`) → update on Render dashboard → Render auto-redeploys. All sessions invalidated.

---

## 🗑️ AWS EC2 Cleanup (Previous Hosting)

TaskTracker was previously hosted on AWS EC2. To clean up the old server:

```bash
# SSH into your EC2 instance
ssh -i your-key.pem ec2-user@54.79.100.142

# Stop and disable the systemd service
sudo systemctl stop tasktracker
sudo systemctl disable tasktracker

# Remove the service file
sudo rm /etc/systemd/system/tasktracker.service
sudo systemctl daemon-reload

# Remove the application files
rm -rf ~/tasktracker

# Remove Nginx config (if no other apps use it)
sudo rm /etc/nginx/conf.d/tasktracker.conf
sudo nginx -t && sudo systemctl reload nginx

# Optionally revoke the Let's Encrypt cert
sudo certbot delete --cert-name api-tasktracker.duckdns.org
```

Then from the **AWS Console**:
- Remove inbound rule for port 8081 in the EC2 Security Group
- If this EC2 instance has no other purpose, you can terminate it
