#!/usr/bin/env bash
# Fetches the Vazirmatn Persian UI font (SIL OFL 1.1) at build time. App falls back to the system font if absent.
set -u
dest=app/src/main/assets/fonts
mkdir -p "$dest" /tmp/vz
if gh release download -R rastikerdar/vazirmatn --pattern '*.zip' -D /tmp/vz 2>/tmp/vz/err; then
  (cd /tmp/vz && unzip -q -o ./*.zip 2>/dev/null || true)
  for w in Medium; do
    f=$(find /tmp/vz -name "Vazirmatn-UI-$w.ttf" | head -1)
    [ -z "$f" ] && f=$(find /tmp/vz -path '*ttf*' -name "Vazirmatn-$w.ttf" | head -1)
    [ -n "$f" ] && cp "$f" "$dest/Vazirmatn-$w.ttf"
  done
  o=$(find /tmp/vz -name 'OFL.txt' | head -1); [ -n "$o" ] && cp "$o" "$dest/OFL-Vazirmatn.txt"
else
  echo "font download failed:"; cat /tmp/vz/err
fi
ls -la "$dest" || true
