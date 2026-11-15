#!/usr/bin/env bash
set -euo pipefail
ORIGEM=${1:-13560-000}; DESTINO=${2:-01310-100}; PESO=${3:-2.5}
ALTURA=${4:-20}; LARGURA=${5:-30}; COMPRIMENTO=${6:-15}
RESPOSTA=$(curl -s -X POST http://localhost:8080/api/cotacoes \
  -H "Content-Type: application/json" \
  -d "{\"cepOrigem\":\"$ORIGEM\",\"cepDestino\":\"$DESTINO\",\"pesoKg\":$PESO,\"alturaCm\":$ALTURA,\"larguraCm\":$LARGURA,\"comprimentoCm\":$COMPRIMENTO}")
if command -v python3 >/dev/null; then
  echo "$RESPOSTA" | python3 -m json.tool --no-ensure-ascii 2>/dev/null || echo "$RESPOSTA"
else
  echo "$RESPOSTA"
fi
