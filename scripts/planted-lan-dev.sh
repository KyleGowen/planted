#!/usr/bin/env bash
# Prepare LAN dev: writes frontend/.env.local and prints how to start backend + Next.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
IP="${PLANTED_LAN_IP:-}"
if [[ -z "$IP" ]]; then
  for iface in en0 en1 en2; do
    IP="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    [[ -n "$IP" ]] && break
  done
fi
if [[ -z "$IP" ]]; then
  echo "Could not detect a LAN IP (tried en0, en1, en2). Set PLANTED_LAN_IP and re-run." >&2
  exit 1
fi

echo "LAN IP: $IP"
echo ""
echo "Start backend (new terminal):"
echo "  cd $ROOT/backend && mvn spring-boot:run -Dspring-boot.run.profiles=local"
echo ""
echo "Start frontend (new terminal):"
echo "  cd $ROOT/frontend && npm run dev:lan"
echo ""
echo "API calls use the Next dev proxy (/api → 127.0.0.1:8080), so you do not need NEXT_PUBLIC_API_URL for LAN."
echo "Remove NEXT_PUBLIC_API_URL from frontend/.env.local if present so the proxy is used."
echo ""
echo "On this Mac:     http://localhost:3000"
echo "Other devices:   http://${IP}:3000"
echo "(Use http:// not https:// — do not use WAN port forwarding.)"
