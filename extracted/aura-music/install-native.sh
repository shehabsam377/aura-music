#!/bin/sh
# Usage (no chmod needed):  sh ./install-native.sh
set -e
cd "$(dirname "$0")"
command -v node >/dev/null 2>&1 || { echo "node not found"; exit 1; }
node scripts/install-native.js
