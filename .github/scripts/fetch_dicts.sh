#!/usr/bin/env bash
# Fetches bigger word lists at build time. The small lists committed in app/src/main/assets stay as the fallback,
# so a failed download never breaks the build.
# Source: FrequencyWords by Hermit Dave (built from OpenSubtitles), CC BY-SA 4.0 - https://github.com/hermitdave/FrequencyWords
set -u
dest=app/src/main/assets
mkdir -p shots
fetch() {   # LANG MAX_WORDS
  local lang=$1 max=$2 raw=/tmp/dict_$1.raw yr ok=0
  for yr in 2018 2016; do
    if curl -fsSL --retry 3 --max-time 60 "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/$yr/$lang/${lang}_50k.txt" -o "$raw" 2>/dev/null; then ok=1; break; fi
  done
  if [ $ok = 0 ]; then echo "dict $lang: download failed, keeping the bundled list" | tee -a shots/dict.txt; return; fi
  if python3 .github/scripts/make_dict.py "$lang" "$max" "$raw" "/tmp/dict_$lang.txt" | tee -a shots/dict.txt \
     && [ "$(wc -l < /tmp/dict_$lang.txt)" -ge 5000 ]; then
    cp "/tmp/dict_$lang.txt" "$dest/dict_$lang.txt"
  else
    echo "dict $lang: list too short, keeping the bundled list" | tee -a shots/dict.txt
  fi
}
fetch en 20000
fetch fa 30000
wc -lc $dest/dict_*.txt | tee -a shots/dict.txt
