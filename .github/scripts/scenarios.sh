shoot fa_dark   --es psi_kv default_lang=fa,theme_mode=dark
shoot en_dark   --es psi_kv default_lang=en,theme_mode=dark
TYPE=hel shoot en_dark_typing --es psi_kv default_lang=en,theme_mode=dark
shoot en_light  --es psi_kv default_lang=en,theme_mode=light
shoot fa_light  --es psi_kv default_lang=fa,theme_mode=light
shoot en_amoled --es psi_kv default_lang=en,theme_mode=dark,amoled=true
HOLD=183,1118 shoot en_popup --es psi_kv default_lang=en,theme_mode=light
HOLD=242,1308 shoot fa_popup --es psi_kv default_lang=fa,theme_mode=dark
TAP=0 shoot settings_light --es psi_kv theme_mode=light
NIGHT=1 TAP=0 shoot settings_dark --es psi_kv theme_mode=dark
POST_TAP=120,945 shoot clip_panel --es psi_clip "https://example.com/a/long/link/to/test|Meeting_at_5_pm_in_room_12|+98_912_000_0000|Hello_world" --es psi_kv default_lang=en,theme_mode=dark
POST_TAP=100,1405 shoot emoji_panel --es psi_kv default_lang=en,theme_mode=light
