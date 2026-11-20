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
POST_TAP=120,945 shoot clip_panel --es psi_clip "https://example.com/a/long/link/to/test,Meeting_at_5_pm_in_room_12,+98_912_000_0000,Hello_world" --es psi_kv default_lang=en,theme_mode=dark
POST_TAP=100,1405 shoot emoji_panel --es psi_kv default_lang=en,theme_mode=light
HOLD=111,1118 shoot en_preview --es psi_kv default_lang=en,theme_mode=light
POST_TAP=41,1405 shoot en_symbols --es psi_kv default_lang=en,theme_mode=dark
POST_TAP=59,1308 shoot en_shift --es psi_kv default_lang=en,theme_mode=dark
POST_TAP=120,945 shoot fa_clip_panel --es psi_clip "کد_تایید:_۱۲۳۴۵۶,https://example.com,Hello_world" --es psi_kv default_lang=fa,theme_mode=light
SCROLL=1 TAP=0 shoot settings_theme --es psi_kv theme_mode=light
TAPS="124,1213 300,1213 359,1213 536,1213" shoot fa_typing --es psi_kv default_lang=fa,theme_mode=dark
ROTATE=1 shoot en_land --es psi_kv default_lang=en,theme_mode=dark
ROTATE=1 shoot fa_land --es psi_kv default_lang=fa,theme_mode=light
ROTATE=1 shoot en_land_num --es psi_kv default_lang=en,theme_mode=dark,number_row_land=true
ROTATE=1 TAP=0 shoot settings_land --es psi_kv theme_mode=light
