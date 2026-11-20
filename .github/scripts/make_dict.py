"""Turn a raw 'word count' frequency list into a clean one-word-per-line dictionary (most frequent first).
usage: make_dict.py LANG MAX_WORDS RAW_FILE OUT_FILE"""
import re, sys

lang, max_words, src, out = sys.argv[1], int(sys.argv[2]), sys.argv[3], sys.argv[4]

# Words we never offer as a completion (they can still be typed). Exact matches only.
BLOCK = {
    "en": set("fuck fucked fucker fucking motherfucker shit shitty bullshit bitch bitches asshole assholes dick dicks cock pussy cunt slut whore bastard nigga nigger faggot fag retard retarded".split()),
    "fa": set("کیر کیری کون کونی کونده جنده جاکش کسکش کسشر گایید گاییدن".split()),
}[lang if lang in ("en", "fa") else "en"]

# Subtitle lists split contractions at the apostrophe ("don't" -> "don", "t"): drop the fragments and
# add the real contractions back, spread over the top ranks so they sort like everyday words.
CONTRACTIONS = ("i'm don't it's that's i'll can't you're didn't i've what's i'd he's she's we're they're doesn't isn't "
                "won't let's there's you'll wasn't aren't haven't couldn't wouldn't shouldn't you've who's here's "
                "weren't hasn't hadn't how's where's we'll they'll he'll she'll it'll we've they've you'd he'd "
                "she'd we'd they'd ain't mustn't needn't y'all o'clock").split()
FRAGMENTS = set("s t m d ll ve re don didn doesn isn aren wasn weren haven hasn hadn won wouldn couldn shouldn ain mustn needn".split())

def norm_fa(w):
    w = w.replace("ي", "ی").replace("ى", "ی").replace("ك", "ک")
    w = re.sub("[ً-ٰٟـ]", "", w)           # diacritics, tatweel
    return w.strip("‌")

FA_OK = re.compile("^[آابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهیئءؤ‌]+$")
EN_OK = re.compile(r"^[a-z]+(?:'[a-z]+)?$")

seen, words = set(), []
for line in open(src, encoding="utf-8", errors="ignore"):
    parts = line.strip().split()
    if not parts:
        continue
    w = parts[0].lower()
    if lang == "fa":
        w = norm_fa(parts[0])
        if not FA_OK.match(w) or len(w) < 2:
            continue
    else:
        if w in FRAGMENTS or not EN_OK.match(w):
            continue
        if len(w) == 1 and w not in ("a", "i"):
            continue
    if re.search(r"(.)\1\1", w) or w in BLOCK or w in seen:     # "hahaha", "aaah", blocked, duplicates
        continue
    seen.add(w); words.append(w)
    if len(words) >= max_words:
        break

if lang != "fa":
    for i, c in enumerate(CONTRACTIONS):
        if c not in seen:
            words.insert(min(len(words), 12 + i * 6), c)

with open(out, "w", encoding="utf-8") as f:
    f.write("\n".join(words) + "\n")
print(f"{lang}: {len(words)} words, first: {' '.join(words[:8])}")
