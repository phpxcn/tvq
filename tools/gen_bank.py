#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TVLock 初始题库生成器（小学一年级：口算 + 拼音，约 1800 题）
输出：app/src/main/assets/questions.json

格式：纯题目数组（不分学科）；
  带 opts  = 选择题（ans 为 1 起始序号）
  不带 opts = 填空题（ans 为文本）
"""
import json
import random
import sys
import os

random.seed(20261105)

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                   "..", "app", "src", "main", "assets", "questions.json")

math_qs = []
pinyin_qs = []
seen = set()


def add(cat, q, ans, opts=None):
    key = cat + "|" + q + ("|" + ",".join(opts) if opts else "")
    if key in seen:
        return False
    seen.add(key)
    item = {"q": q}
    if opts:
        item["opts"] = opts
    item["ans"] = ans
    if cat == "math":
        math_qs.append(item)
    else:
        pinyin_qs.append(item)
    return True


# ============================================================
# 一、数学口算（约 1200 题）
# ============================================================

# 1. 20 以内加法填空（全部组合）
add_pairs = [(a, b) for a in range(0, 21) for b in range(0, 21) if a + b <= 20]
for a, b in add_pairs:
    add("math", f"{a}+{b}等于几？", str(a + b))

# 2. 20 以内减法填空（全部组合，差非负）
sub_pairs = [(a, b) for a in range(0, 21) for b in range(0, a + 1)]
random.shuffle(sub_pairs)
for a, b in sub_pairs[:200]:
    add("math", f"{a}-{b}等于几？", str(a - b))

# 3. 连加填空（a+b+c <= 20）
lian = [(a, b, c) for a in range(0, 13) for b in range(0, 13) for c in range(0, 13)
        if 3 <= a + b + c <= 20]
random.shuffle(lian)
n = 0
for a, b, c in lian:
    if n >= 150:
        break
    if add("math", f"{a}+{b}+{c}等于几？", str(a + b + c)):
        n += 1

# 4. 连减填空（x-a-b >= 0）
lsub = [(x, a, b) for x in range(6, 21) for a in range(1, 13) for b in range(1, 13)
        if 2 <= x - a - b <= 20]
random.shuffle(lsub)
n = 0
for x, a, b in lsub:
    if n >= 100:
        break
    if add("math", f"{x}-{a}-{b}等于几？", str(x - a - b)):
        n += 1

# 5. 填未知数（a+( )=c 等）
unknown = []
for a in range(0, 16):
    for c in range(a, a + 11):
        if c > 20:
            break
        b = c - a
        if b < 0:
            continue
        unknown.append(("a+()=c", a, b, c))
        unknown.append(("()+b=c", a, b, c))
        if c >= a:
            unknown.append(("c-()=b", a, b, c))
        if a + b <= 20:
            unknown.append(("()-a=b", a, b, a + b))
random.shuffle(unknown)
n = 0
for form, a, b, c in unknown:
    if n >= 180:
        break
    if form == "a+()=c":
        q, ans = f"{a}+( )={c}，括号里填几？", str(b)
    elif form == "()+b=c":
        q, ans = f"( )+{b}={c}，括号里填几？", str(a)
    elif form == "c-()=b":
        q, ans = f"{c}-( )={b}，括号里填几？", str(a)
    else:
        q, ans = f"( )-{a}={b}，括号里填几？", str(a + b)
    if add("math", q, ans):
        n += 1


def num_distractors(ans, count=3):
    """数值干扰项：答案 +-1/2/10 附近，保证合法且不重复"""
    cands = set()
    for d in (1, 2, 3, 10):
        for v in (ans - d, ans + d):
            if 0 <= v <= 40 and v != ans:
                cands.add(v)
    cands = sorted(cands)
    random.shuffle(cands)
    return cands[:count]


# 6. 加法选择题
add_choice = [(a, b) for a in range(1, 18) for b in range(1, 18) if a + b <= 20 and a + b >= 5]
random.shuffle(add_choice)
n = 0
for a, b in add_choice:
    if n >= 120:
        break
    ans = a + b
    ds = num_distractors(ans)
    if len(ds) < 3:
        continue
    opts = [str(ans)] + [str(d) for d in ds]
    random.shuffle(opts)
    add("math", f"{a}+{b}等于几？", str(opts.index(str(ans)) + 1), opts)
    n += 1

# 7. 减法选择题
sub_choice = [(a, b) for a in range(5, 21) for b in range(1, a) if a - b >= 0]
random.shuffle(sub_choice)
n = 0
for a, b in sub_choice:
    if n >= 120:
        break
    ans = a - b
    ds = num_distractors(ans)
    if len(ds) < 3:
        continue
    opts = [str(ans)] + [str(d) for d in ds]
    random.shuffle(opts)
    add("math", f"{a}-{b}等于几？", str(opts.index(str(ans)) + 1), opts)
    n += 1

# 8. 比大小选择题（＞ ＜ ＝）
cmp_pairs = []
for a in range(0, 17):
    for b in range(0, 17):
        if a + b > 20:
            continue
        c = random.randint(0, 20)
        cmp_pairs.append((a, b, c))
random.shuffle(cmp_pairs)
n = 0
for a, b, c in cmp_pairs:
    if n >= 110:
        break
    s = a + b
    rel = "＞" if s > c else ("＜" if s < c else "＝")
    opts = ["＞", "＜", "＝"]
    if add("math", f"比一比：{a}+{b} ○ {c}", str(opts.index(rel) + 1), opts):
        n += 1

# ============================================================
# 二、拼音（约 620 题）
# ============================================================

# 字表：(字, 拼音含声调, 声调, 声母, 韵母, 是否整体认读音节)
# 声母为空串 = 整体认读音节（无拼音拆分）或 er 特殊韵母
CHARS = [
    ("一", "yī", 1, "", "", True),
    ("五", "wǔ", 3, "", "", True),
    ("午", "wǔ", 3, "", "", True),
    ("雨", "yǔ", 3, "", "", True),
    ("鱼", "yú", 2, "", "", True),
    ("月", "yuè", 4, "", "", True),
    ("夜", "yè", 4, "", "", True),
    ("圆", "yuán", 2, "", "", True),
    ("云", "yún", 2, "", "", True),
    ("音", "yīn", 1, "", "", True),
    ("阴", "yīn", 1, "", "", True),
    ("银", "yín", 2, "", "", True),
    ("四", "sì", 4, "", "", True),
    ("字", "zì", 4, "", "", True),
    ("子", "zǐ", 3, "", "", True),
    ("词", "cí", 2, "", "", True),
    ("只", "zhī", 1, "", "", True),
    ("十", "shí", 2, "", "", True),
    ("是", "shì", 4, "", "", True),
    ("师", "shī", 1, "", "", True),
    ("时", "shí", 2, "", "", True),
    ("石", "shí", 2, "", "", True),
    ("日", "rì", 4, "", "", True),
    ("二", "èr", 2, "", "er", False),
    ("耳", "ěr", 3, "", "er", False),
    ("三", "sān", 1, "s", "an", False),
    ("山", "shān", 1, "sh", "an", False),
    ("爸", "bà", 4, "b", "a", False),
    ("妈", "mā", 1, "m", "a", False),
    ("大", "dà", 4, "d", "a", False),
    ("花", "huā", 1, "h", "ua", False),
    ("画", "huà", 4, "h", "ua", False),
    ("瓜", "guā", 1, "g", "ua", False),
    ("下", "xià", 4, "x", "ia", False),
    ("家", "jiā", 1, "j", "ia", False),
    ("米", "mǐ", 3, "m", "i", False),
    ("地", "dì", 4, "d", "i", False),
    ("笔", "bǐ", 3, "b", "i", False),
    ("七", "qī", 1, "q", "i", False),
    ("西", "xī", 1, "x", "i", False),
    ("你", "nǐ", 3, "n", "i", False),
    ("木", "mù", 4, "m", "u", False),
    ("土", "tǔ", 3, "t", "u", False),
    ("兔", "tù", 4, "t", "u", False),
    ("不", "bù", 4, "b", "u", False),
    ("书", "shū", 1, "sh", "u", False),
    ("树", "shù", 4, "sh", "u", False),
    ("出", "chū", 1, "ch", "u", False),
    ("足", "zú", 2, "z", "u", False),
    ("禾", "hé", 2, "h", "e", False),
    ("哥", "gē", 1, "g", "e", False),
    ("车", "chē", 1, "ch", "e", False),
    ("热", "rè", 4, "r", "e", False),
    ("蛇", "shé", 2, "sh", "e", False),
    ("绿", "lǜ", 4, "l", "ü", False),
    ("女", "nǚ", 3, "n", "ü", False),
    ("句", "jù", 4, "j", "ü", False),
    ("雪", "xuě", 3, "x", "üe", False),
    ("学", "xué", 2, "x", "üe", False),
    ("水", "shuǐ", 3, "sh", "ui", False),
    ("嘴", "zuǐ", 3, "z", "ui", False),
    ("对", "duì", 4, "d", "ui", False),
    ("腿", "tuǐ", 3, "t", "ui", False),
    ("杯", "bēi", 1, "b", "ei", False),
    ("黑", "hēi", 1, "h", "ei", False),
    ("北", "běi", 3, "b", "ei", False),
    ("飞", "fēi", 1, "f", "ei", False),
    ("雷", "léi", 2, "l", "ei", False),
    ("猫", "māo", 1, "m", "ao", False),
    ("包", "bāo", 1, "b", "ao", False),
    ("桃", "táo", 2, "t", "ao", False),
    ("草", "cǎo", 3, "c", "ao", False),
    ("好", "hǎo", 3, "h", "ao", False),
    ("老", "lǎo", 3, "l", "ao", False),
    ("高", "gāo", 1, "g", "ao", False),
    ("早", "zǎo", 3, "z", "ao", False),
    ("少", "shǎo", 3, "sh", "ao", False),
    ("桥", "qiáo", 2, "q", "iao", False),
    ("鸟", "niǎo", 3, "n", "iao", False),
    ("小", "xiǎo", 3, "x", "iao", False),
    ("跳", "tiào", 4, "t", "iao", False),
    ("苗", "miáo", 2, "m", "iao", False),
    ("牛", "niú", 2, "n", "iu", False),
    ("九", "jiǔ", 3, "j", "iu", False),
    ("球", "qiú", 2, "q", "iu", False),
    ("秋", "qiū", 1, "q", "iu", False),
    ("姐", "jiě", 3, "j", "ie", False),
    ("写", "xiě", 3, "x", "ie", False),
    ("谢", "xiè", 4, "x", "ie", False),
    ("门", "mén", 2, "m", "en", False),
    ("本", "běn", 3, "b", "en", False),
    ("分", "fēn", 1, "f", "en", False),
    ("前", "qián", 2, "q", "ian", False),
    ("天", "tiān", 1, "t", "ian", False),
    ("田", "tián", 2, "t", "ian", False),
    ("面", "miàn", 4, "m", "ian", False),
    ("边", "biān", 1, "b", "ian", False),
    ("电", "diàn", 4, "d", "ian", False),
    ("年", "nián", 2, "n", "ian", False),
    ("千", "qiān", 1, "q", "ian", False),
    ("半", "bàn", 4, "b", "an", False),
    ("万", "wàn", 4, "w", "an", False),
    ("南", "nán", 2, "n", "an", False),
    ("蓝", "lán", 2, "l", "an", False),
    ("看", "kàn", 4, "k", "an", False),
    ("伞", "sǎn", 3, "s", "an", False),
    ("反", "fǎn", 3, "f", "an", False),
    ("班", "bān", 1, "b", "an", False),
    ("心", "xīn", 1, "x", "in", False),
    ("林", "lín", 2, "l", "in", False),
    ("金", "jīn", 1, "j", "in", False),
    ("春", "chūn", 1, "ch", "un", False),
    ("村", "cūn", 1, "c", "un", False),
    ("孙", "sūn", 1, "s", "un", False),
    ("群", "qún", 2, "q", "ün", False),
    ("军", "jūn", 1, "j", "ün", False),
    ("方", "fāng", 1, "f", "ang", False),
    ("上", "shàng", 4, "sh", "ang", False),
    ("长", "cháng", 2, "ch", "ang", False),
    ("羊", "yáng", 2, "y", "ang", False),
    ("光", "guāng", 1, "g", "uang", False),
    ("黄", "huáng", 2, "h", "uang", False),
    ("王", "wáng", 2, "w", "ang", False),
    ("风", "fēng", 1, "f", "eng", False),
    ("虫", "chóng", 2, "ch", "ong", False),
    ("红", "hóng", 2, "h", "ong", False),
    ("东", "dōng", 1, "d", "ong", False),
    ("冬", "dōng", 1, "d", "ong", False),
    ("中", "zhōng", 1, "zh", "ong", False),
    ("松", "sōng", 1, "s", "ong", False),
    ("星", "xīng", 1, "x", "ing", False),
    ("明", "míng", 2, "m", "ing", False),
    ("听", "tīng", 1, "t", "ing", False),
    ("青", "qīng", 1, "q", "ing", False),
    ("晴", "qíng", 2, "q", "ing", False),
    ("用", "yòng", 4, "y", "ong", False),
    ("工", "gōng", 1, "g", "ong", False),
    ("空", "kōng", 1, "k", "ong", False),
    ("坐", "zuò", 4, "z", "uo", False),
    ("左", "zuǒ", 3, "z", "uo", False),
    ("火", "huǒ", 3, "h", "uo", False),
    ("果", "guǒ", 3, "g", "uo", False),
    ("多", "duō", 1, "d", "uo", False),
    ("说", "shuō", 1, "sh", "uo", False),
    ("河", "hé", 2, "h", "e", False),
    ("海", "hǎi", 3, "h", "ai", False),
    ("白", "bái", 2, "b", "ai", False),
    ("菜", "cài", 4, "c", "ai", False),
    ("百", "bǎi", 3, "b", "ai", False),
    ("来", "lái", 2, "l", "ai", False),
    ("开", "kāi", 1, "k", "ai", False),
    ("走", "zǒu", 3, "z", "ou", False),
    ("口", "kǒu", 3, "k", "ou", False),
    ("手", "shǒu", 3, "sh", "ou", False),
    ("后", "hòu", 4, "h", "ou", False),
    ("狗", "gǒu", 3, "g", "ou", False),
    ("楼", "lóu", 2, "l", "ou", False),
    ("头", "tóu", 2, "t", "ou", False),
    ("六", "liù", 4, "l", "iu", False),
    ("马", "mǎ", 3, "m", "a", False),
    ("跑", "pǎo", 3, "p", "ao", False),
]

TONE_OPTS = ["一声", "二声", "三声", "四声"]
SIMPLE_FINALS = ["a", "o", "e", "i", "u", "ü"]
COMPOUND_FINALS = ["ai", "ei", "ui", "ao", "ou", "iu", "ie", "üe",
                   "an", "en", "in", "un", "ün", "ang", "eng", "ing", "ong",
                   "ia", "ua", "uo", "iao", "ian", "uang"]
ALL_INITIALS = ["b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
                "j", "q", "x", "zh", "ch", "sh", "r", "z", "c", "s", "y", "w"]
ZHENG = ["zhi", "chi", "shi", "ri", "zi", "ci", "si",
         "yi", "wu", "yu", "ye", "yue", "yuan", "yin", "yun", "ying"]
NOT_ZHENG = ["zha", "zhe", "chu", "shu", "ru", "re", "za", "cu", "su",
             "sa", "ya", "wa", "wo", "yan", "yang", "yong", "zhu", "chai", "shai"]

# 声调符号变换表（用于拼写选择题的干扰项）
VOWEL_TONES = {
    "a": "āáǎà", "o": "ōóǒò", "e": "ēéěè", "i": "īíǐì",
    "u": "ūúǔù", "ü": "ǖǘǚǜ",
}
ACCENTED = {}
for base, marks in VOWEL_TONES.items():
    for m in marks:
        ACCENTED[m] = (base, marks)

INITIAL_SWAP = {
    "sh": "s", "ch": "c", "zh": "z", "x": "sh", "q": "j", "p": "b",
    "t": "d", "l": "n", "g": "k", "m": "n", "f": "h", "d": "t",
    "b": "p", "n": "l", "j": "q", "z": "zh", "c": "ch", "s": "sh",
    "r": "l", "k": "g", "h": "f",
}


def tone_variant(py):
    """换声调的干扰项"""
    out = []
    for ch in py:
        if ch in ACCENTED:
            base, marks = ACCENTED[ch]
            cur = marks.index(ch)
            out.append(marks[(cur + 1) % len(marks)])
        else:
            out.append(ch)
    return "".join(out)


def initial_variant(py):
    """换声母的干扰项"""
    for ini in ("zh", "ch", "sh"):
        if py.startswith(ini) and ini in INITIAL_SWAP:
            return INITIAL_SWAP[ini] + py[len(ini):]
    ini = py[0]
    if ini in INITIAL_SWAP:
        return INITIAL_SWAP[ini] + py[1:]
    return py


def py_distractors(py):
    ds = []
    for f in (tone_variant, initial_variant, lambda p: tone_variant(initial_variant(p)),
              lambda p: initial_variant(tone_variant(p))):
        v = f(py)
        if v and v != py and v not in ds:
            ds.append(v)
    return ds


def pick_others(pool, correct, count=3):
    cands = [x for x in pool if x != correct]
    random.shuffle(cands)
    return cands[:count]


def make_choice(q, correct, others):
    opts = [correct] + others
    random.shuffle(opts)
    add("pinyin", q, str(opts.index(correct) + 1), opts)


# 1. 声调判断
tone_chars = list(CHARS)
random.shuffle(tone_chars)
for ch, py, tone, sm, ym, zt in tone_chars[:120]:
    make_choice(f"「{ch}」的声调是第几声？", TONE_OPTS[tone - 1],
                [t for t in TONE_OPTS if t != TONE_OPTS[tone - 1]])

# 2. 声母
sm_chars = [c for c in CHARS if c[3]]
random.shuffle(sm_chars)
for ch, py, tone, sm, ym, zt in sm_chars[:90]:
    others = pick_others(ALL_INITIALS, sm)
    make_choice(f"「{ch}」的声母是？", sm, others)

# 3. 单韵母
single_chars = [c for c in CHARS if c[4] in SIMPLE_FINALS and not c[5]]
random.shuffle(single_chars)
for ch, py, tone, sm, ym, zt in single_chars[:60]:
    others = pick_others(SIMPLE_FINALS, ym)
    make_choice(f"「{ch}」的韵母是？", ym, others)

# 4. 复韵母 / 鼻韵母
compound_chars = [c for c in CHARS if c[4] in COMPOUND_FINALS]
random.shuffle(compound_chars)
for ch, py, tone, sm, ym, zt in compound_chars[:90]:
    others = pick_others(COMPOUND_FINALS, ym)
    make_choice(f"「{ch}」的韵母是？", ym, others)

# 5. 整体认读音节
for i in range(80):
    correct = ZHENG[i % len(ZHENG)]
    others = pick_others(NOT_ZHENG, correct)
    make_choice("下面哪一个是整体认读音节？", correct, others)

# 6. 拼写选择
spell_chars = list(CHARS)
random.shuffle(spell_chars)
for ch, py, tone, sm, ym, zt in spell_chars[:120]:
    ds = py_distractors(py)
    if len(ds) < 3:
        continue
    make_choice(f"「{ch}」的拼音是？", py, ds[:3])

# 7. 前鼻韵母 / 后鼻韵母
front = ["an", "en", "in", "un", "ün"]
back = ["ang", "eng", "ing", "ong"]
nose_chars = [c for c in CHARS if c[4] in front + back]
random.shuffle(nose_chars)
for ch, py, tone, sm, ym, zt in nose_chars[:60]:
    is_front = ym in front
    make_choice(f"「{ch}」的韵母是前鼻韵母还是后鼻韵母？",
                "前鼻韵母" if is_front else "后鼻韵母",
                ["后鼻韵母"] if is_front else ["前鼻韵母"])

# ============================================================
# 输出（纯题目数组，不分学科）
# ============================================================
bank = math_qs + pinyin_qs
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(bank, f, ensure_ascii=False, separators=(",", ":"))

total = len(bank)
print(f"题库生成完成 -> {os.path.normpath(OUT)}")
print(f"  合计   : {total} 题")
if total < 1500:
    print("警告：题量偏少，请检查生成参数", file=sys.stderr)
    sys.exit(1)
