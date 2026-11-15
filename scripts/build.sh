#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

mvn -q clean package

mkdir -p plugins-disponiveis plugins-ativos
rm -f plugins-disponiveis/*.jar plugins-ativos/*.jar
cp plugins/*/target/plugin-*.jar plugins-disponiveis/
cp plugins-disponiveis/plugin-correios.jar plugins-disponiveis/plugin-jadlog.jar plugins-ativos/

echo
echo "Plugins disponíveis: $(ls plugins-disponiveis | tr '\n' ' ')"
echo "Plugins ativos:      $(ls plugins-ativos | tr '\n' ' ')"
