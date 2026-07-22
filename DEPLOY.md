# TaskTracker Backend — GitHub Secrets Setup

## Required Secrets (Settings → Secrets and variables → Actions)

| Secret Name | Value |
|-------------|-------|
| `VM_HOST` | `54.79.100.142` |
| `VM_SECRET` | Contents of `smartTools.pem` key file |

## Env file on EC2 Server

SSH into the server and create the env file **once**:

```bash
ssh -i smartTools.pem ec2-user@54.79.100.142

# Create secrets directory
mkdir -p ~/tasktracker/secrets

# Create the env file
cat > ~/tasktracker/secrets/tasktracker.env << 'EOF'
DB_URL=jdbc:postgresql://aws-1-ap-northeast-1.pooler.supabase.com:5432/postgres?sslmode=require&options=endpoint%3Dtjtqnxuuvaeaemwrumky
DB_USERNAME=postgres.tjtqnxuuvaeaemwrumky
DB_PASSWORD=Skpatel@1604
JWT_SECRET=TaskTracker$ecretK3y!9f2mXqR8pL7nV5sYwD4jA6hC1bG0eI3uO
CORS_ALLOWED_ORIGINS=*
SERVER_PORT=8081
EOF
```

## How CI/CD Works

1. **Push to `main`** → GitHub Actions triggers
2. **Build** → Maven builds `tasktracker-backend-1.0.0.jar`
3. **SCP** → JAR is copied to `/home/ec2-user/tasktracker/`
4. **SSH** → Old process is gracefully stopped, new JAR started with `--spring.profiles.active=prod`
5. **Verify** → Health check confirms process is running

## Local vs Production

| Setting | Local | Production |
|---------|-------|-----------|
| `API_BASE_URL` (mobile) | `http://192.168.9.3:8081` | `http://54.79.100.142:8081` |
| Spring profile | default | `prod` |
| SQL logging | `true` | `false` |
| CORS origins | localhost only | `*` |

## Update Mobile App for Production

In `mobile-app/constants/api.ts`, update the prod URL:
```ts
const PROD_BASE_URL = 'http://54.79.100.142:8081';
```

## View Logs

```bash
ssh -i smartTools.pem ec2-user@54.79.100.142 'tail -f ~/tasktracker/app.log'
```
