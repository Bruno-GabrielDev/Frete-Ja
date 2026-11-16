#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

case "${1:-}" in
  completa)  ALVO=http://nucleo:8080;           NOME="completa (cache + circuit breaker)" ;;
  sem-cache) ALVO=http://nucleo-sem-cache:8080; NOME="sem cache" ;;
  sem-cb)    ALVO=http://nucleo-sem-cb:8080;    NOME="sem circuit breaker (só tempo limite)" ;;
  *) echo "Uso: $0 <completa|sem-cache|sem-cb> [usuarios] [duracao]"; exit 1 ;;
esac

docker compose --profile carga run --rm k6 run --quiet \
  -e ALVO="$ALVO" -e NOME="$NOME" \
  -e USUARIOS="${2:-20}" -e DURACAO="${3:-30s}" \
  /carga/cotacoes.js
