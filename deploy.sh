#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# TaskTracker Backend — AWS EC2 Deploy Script
# Usage: bash deploy.sh
# ─────────────────────────────────────────────────────────────

EC2_IP="54.79.100.142"
EC2_USER="ubuntu"        # change to ec2-user if needed
PEM_KEY="D:/SmartTools/smartTools.pem"
JAR_PATH="target/tasktracker-backend-1.0.0.jar"
REMOTE_DIR="/home/$EC2_USER/tasktracker"
ENV_FILE=".env"

echo "==> Building JAR..."
./mvnw.cmd package -DskipTests -q

echo "==> Creating remote directory..."
ssh -i "$PEM_KEY" -o StrictHostKeyChecking=no "$EC2_USER@$EC2_IP" "mkdir -p $REMOTE_DIR"

echo "==> Uploading JAR..."
scp -i "$PEM_KEY" "$JAR_PATH" "$EC2_USER@$EC2_IP:$REMOTE_DIR/app.jar"

echo "==> Uploading .env file..."
scp -i "$PEM_KEY" "$ENV_FILE" "$EC2_USER@$EC2_IP:$REMOTE_DIR/.env"

echo "==> Setting up systemd service and starting..."
ssh -i "$PEM_KEY" "$EC2_USER@$EC2_IP" <<'ENDSSH'
# Install Java 21 if not present
if ! java -version 2>&1 | grep -q "21"; then
  sudo apt-get update -q
  sudo apt-get install -y openjdk-21-jre-headless
fi

# Create systemd service
sudo tee /etc/systemd/system/tasktracker.service > /dev/null <<EOF
[Unit]
Description=TaskTracker Spring Boot Backend
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/tasktracker
EnvironmentFile=/home/ubuntu/tasktracker/.env
ExecStart=/usr/bin/java -jar /home/ubuntu/tasktracker/app.jar --spring.profiles.active=prod
Restart=always
RestartSec=10
StandardOutput=syslog
StandardError=syslog
SyslogIdentifier=tasktracker

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable tasktracker
sudo systemctl restart tasktracker
sleep 3
sudo systemctl status tasktracker --no-pager
ENDSSH

echo "==> Deploy complete! Backend running on http://$EC2_IP:8081"
echo "==> Logs: ssh -i $PEM_KEY $EC2_USER@$EC2_IP 'journalctl -u tasktracker -f'"
