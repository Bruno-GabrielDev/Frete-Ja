#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cp "plugins-disponiveis/plugin-$1.jar" "plugins-ativos/.plugin-$1.jar.tmp"
mv "plugins-ativos/.plugin-$1.jar.tmp" "plugins-ativos/plugin-$1.jar"
echo "plugin-$1.jar copiado para plugins-ativos/ — o núcleo detecta em até ~4 s"
