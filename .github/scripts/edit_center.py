import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r'class="android\.widget\.EditText"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
if m:
    l, t, r, b = map(int, m.groups())
    print((l + r) // 2, (t + b) // 2)
else:
    print("360 400")
