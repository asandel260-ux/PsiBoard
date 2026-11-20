import re, sys
# usage: edit_center.py ui.xml WIDTH HEIGHT  ->  "x y" of the demo text field, NONE if it is not fully on screen, ERR if no dump
W = int(sys.argv[2]) if len(sys.argv) > 2 else 720
H = int(sys.argv[3]) if len(sys.argv) > 3 else 1560
try:
    s = open(sys.argv[1], encoding="utf-8").read()
except Exception:
    s = ""
if "<hierarchy" not in s:
    print("ERR")
    sys.exit(0)
m = re.search(r'class="android\.widget\.EditText"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
if m:
    l, t, r, b = map(int, m.groups())
    cx, cy = (l + r) // 2, (t + b) // 2
    if 0 <= cx < W and 0 < cy < H * 0.9 and b - t > 40:
        print(cx, cy)
        sys.exit(0)
print("NONE")
