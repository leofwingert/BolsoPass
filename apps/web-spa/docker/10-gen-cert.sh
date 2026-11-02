#!/bin/sh
# Gera um certificado HTTPS autoassinado de desenvolvimento na primeira subida do nginx.
# Fica no volume "certs": o celular só precisa aceitar o aviso de segurança uma vez.
# HOST_IP (opcional) entra no certificado, ex.: HOST_IP=192.168.2.102.
set -e
DIR=/etc/nginx/certs
CERT="$DIR/bolsopass.crt"
KEY="$DIR/bolsopass.key"

if [ -s "$CERT" ] && [ -s "$KEY" ]; then
  echo "bolsopass: certificado HTTPS já existe"
  exit 0
fi

mkdir -p "$DIR"
SAN="DNS:localhost,IP:127.0.0.1"
[ -n "$HOST_IP" ] && SAN="$SAN,IP:$HOST_IP"
openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -subj "/CN=BolsoPass dev" -addext "subjectAltName=$SAN" \
  -keyout "$KEY" -out "$CERT" 2>/dev/null
echo "bolsopass: certificado HTTPS de desenvolvimento gerado ($SAN)"
