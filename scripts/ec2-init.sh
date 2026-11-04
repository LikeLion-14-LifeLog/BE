#!/usr/bin/env bash
# EC2 최초 1회 실행: 스왑 메모리 → Docker 설치 → 배포 폴더 생성
# 사용법: bash ec2-init.sh [스왑 크기, 기본 2G]
set -euo pipefail

SWAP_SIZE="${1:-2G}"
SWAP_FILE=/swapfile
DEPLOY_DIR="$HOME/lifelog"

# 1. 스왑 메모리 (이미 있으면 건너뜀)
if swapon --show | grep -q "$SWAP_FILE"; then
  echo "[swap] already enabled"
else
  echo "[swap] creating $SWAP_SIZE"
  sudo fallocate -l "$SWAP_SIZE" "$SWAP_FILE"
  sudo chmod 600 "$SWAP_FILE"
  sudo mkswap "$SWAP_FILE"
  sudo swapon "$SWAP_FILE"
  grep -q "$SWAP_FILE" /etc/fstab || echo "$SWAP_FILE none swap sw 0 0" | sudo tee -a /etc/fstab
fi

# 메모리가 부족할 때만 스왑을 쓰도록 설정
echo "vm.swappiness=10" | sudo tee /etc/sysctl.d/99-swappiness.conf > /dev/null
sudo sysctl -p /etc/sysctl.d/99-swappiness.conf

# 2. Docker (이미 있으면 건너뜀)
if command -v docker > /dev/null; then
  echo "[docker] already installed"
else
  echo "[docker] installing"
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

# 3. 배포 폴더와 .env
mkdir -p "$DEPLOY_DIR"
if [ ! -f "$DEPLOY_DIR/.env" ]; then
  echo "DB_PASSWORD=change-me" > "$DEPLOY_DIR/.env"
  chmod 600 "$DEPLOY_DIR/.env"
  echo "[env] created $DEPLOY_DIR/.env (DB_PASSWORD 꼭 변경)"
fi

free -h
echo "Done. 재접속해야 sudo 없이 docker를 쓸 수 있습니다."
