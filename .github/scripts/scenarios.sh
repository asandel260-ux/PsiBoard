shoot fa_dark   --es psi_kv default_lang=fa,theme_mode=dark
shoot en_dark   --es psi_kv default_lang=en,theme_mode=dark
TYPE=hel shoot en_dark_typing --es psi_kv default_lang=en,theme_mode=dark
shoot en_light  --es psi_kv default_lang=en,theme_mode=light
shoot fa_light  --es psi_kv default_lang=fa,theme_mode=light
shoot en_amoled --es psi_kv default_lang=en,theme_mode=dark,amoled=true
TAP=0 shoot settings_light --es psi_kv theme_mode=light
NIGHT=1 TAP=0 shoot settings_dark --es psi_kv theme_mode=dark
