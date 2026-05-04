#!/usr/bin/env bash
# Prints the best-guess LAN IPv4 for sharing with phones, plus commands to start backend + Next (dev:lan).
# Does not write files. Override detection with PLANTED_LAN_IP if this Mac has several private subnets.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

detect_lan_ip() {
  if [[ -n "${PLANTED_LAN_IP:-}" ]]; then
    printf '%s' "$PLANTED_LAN_IP"
    return 0
  fi
  local ip iface
  # Prefer the interface used for the default route (usually the active Wi‑Fi subnet).
  iface="$(route -n get default 2>/dev/null | awk '/interface:/{print $2; exit}')"
  if [[ -n "${iface:-}" ]]; then
    ip="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    if [[ -n "$ip" ]]; then
      printf '%s' "$ip"
      return 0
    fi
  fi
  for cand in en0 en1 en2; do
    ip="$(ipconfig getifaddr "$cand" 2>/dev/null || true)"
    if [[ -n "$ip" ]]; then
      printf '%s' "$ip"
      return 0
    fi
  done
  return 1
}

collect_private_ipv4() {
  ifconfig 2>/dev/null | awk '$1 == "inet" && $2 ~ /^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$/ {
    if ($2 ~ /^192\.168\./ || $2 ~ /^10\./ || $2 ~ /^172\.(1[6-9]|2[0-9]|3[01])\./) print $2
  }' | sort -u
}

IP="$(detect_lan_ip)" || {
  echo "Could not detect a LAN IP (tried default-route interface, then en0–en2)." >&2
  echo "Set PLANTED_LAN_IP and re-run." >&2
  exit 1
}

MULTI="$(collect_private_ipv4 || true)"
UNIQ_COUNT="$(printf '%s\n' "$MULTI" | sed '/^$/d' | wc -l | tr -d ' ')"
if [[ "$UNIQ_COUNT" -gt 1 ]]; then
  echo "Note: multiple private IPv4 addresses on this Mac:"
  printf '%s\n' "$MULTI" | sed '/^$/d' | sed 's/^/  /'
  echo "Using: $IP (if wrong for your Wi‑Fi, re-run with PLANTED_LAN_IP=<address>)"
  echo ""
fi

echo "LAN IP: $IP"
echo ""
echo "Start backend (new terminal):"
echo "  cd $ROOT/backend && mvn spring-boot:run -Dspring-boot.run.profiles=local"
echo ""
echo "Start frontend (new terminal):"
echo "  cd $ROOT/frontend && npm run dev:lan"
echo ""
echo "API calls use the Next dev proxy (/api → 127.0.0.1:8080), so do not set NEXT_PUBLIC_API_URL for LAN."
echo "Remove NEXT_PUBLIC_API_URL from frontend/.env.local if present so the proxy is used."
echo ""
echo "On this Mac:     http://localhost:3000"
echo "Other devices:   http://${IP}:3000"
echo "(Use http:// not https:// — do not use WAN port forwarding.)"
