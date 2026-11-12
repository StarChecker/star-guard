#!/usr/bin/env bash
# Собирает zip расширения (без preview/).
#   scripts/pack-extension.sh [out.zip]
# STAR_GUARD_API_URL — адрес сервера по умолчанию в сборке (иначе http://localhost:8080).
# Для внешнего адреса origin добавляется в host_permissions, чтобы браузер не спрашивал разрешение.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(realpath -m "${1:-$root/star-guard-extension.zip}")"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cp -r "$root/extension" "$tmp/ext"
rm -rf "$tmp/ext/preview"

api="${STAR_GUARD_API_URL:-}"
if [ -n "$api" ]; then
  node - "$tmp/ext" "$api" <<'JS'
const fs = require('fs');
const [dir, raw] = process.argv.slice(2);
const url = new URL(raw);
if (!/^https?:$/.test(url.protocol)) throw new Error('STAR_GUARD_API_URL: нужен http(s)-адрес');
const apiBase = url.toString().replace(/\/+$/, '');
const config = dir + '/src/config.js';
const src = fs.readFileSync(config, 'utf8');
const next = src.replace(/apiBase: '[^']*'/, `apiBase: ${JSON.stringify(apiBase).replace(/"/g, "'")}`);
if (next === src && !src.includes(apiBase)) throw new Error('config.js: не нашёл apiBase');
fs.writeFileSync(config, next);
const manifestPath = dir + '/manifest.json';
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const origin = url.protocol + '//' + url.hostname + '/*';
if (!manifest.host_permissions.includes(origin)) manifest.host_permissions.push(origin);
fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2) + '\n');
console.log('apiBase по умолчанию: ' + apiBase);
JS
fi

rm -f "$out"
(cd "$tmp/ext" && zip -qr -X "$out" .)
echo "$out"
