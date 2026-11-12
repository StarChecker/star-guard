#!/usr/bin/env bash
# Собирает лендинг для GitHub Pages в _site/ (или в каталог из аргумента):
# site/ + интерфейс расширения (ui.js, theme.js, примеры данных) для живого превью + свежий zip.
#   scripts/build-site.sh [out-dir]
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
out="$(realpath -m "${1:-$root/_site}")"
rm -rf "$out"
mkdir -p "$out/ext"
cp -r "$root/site/." "$out/"
cp "$root/extension/src/ui.js" "$root/extension/src/theme.js" "$root/extension/preview/samples.js" \
   "$root/extension/icons/icon-32.png" "$out/ext/"
"$root/scripts/pack-extension.sh" "$out/star-guard-extension.zip" >/dev/null
# адрес сервера по умолчанию — тот же, что попал в сборку
unzip -p "$out/star-guard-extension.zip" src/config.js > "$out/ext/config.js"
echo "$out"
