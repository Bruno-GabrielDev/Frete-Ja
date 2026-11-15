#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
rm -f "plugins-ativos/plugin-$1.jar"
echo "plugin-$1.jar removido de plugins-ativos/"
