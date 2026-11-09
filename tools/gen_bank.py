#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
TVLock 初始题库生成器（小学一年级：口算 + 拼音 + 英语，约 4200 题）
输出：app/src/main/assets/questions.json

格式：纯题目数组（不分学科）；
  带 opts  = 选择题（ans 为 1 起始序号）
  不带 opts = 填空题（ans 为文本；注意锁屏键盘只支持数字输入，填空题答案必须是纯数字）
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
english_qs = []
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
    elif cat == "english":
        english_qs.append(item)
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
    # ---- 扩充字表（补齐声母/韵母覆盖） ----
    # a 组
    ("拉", "lā", 1, "l", "a", False),
    ("拿", "ná", 2, "n", "a", False),
    ("把", "bǎ", 3, "b", "a", False),
    ("怕", "pà", 4, "p", "a", False),
    ("爬", "pá", 2, "p", "a", False),
    ("发", "fā", 1, "f", "a", False),
    ("沙", "shā", 1, "sh", "a", False),
    ("茶", "chá", 2, "ch", "a", False),
    # o 组
    ("波", "bō", 1, "b", "o", False),
    ("泼", "pō", 1, "p", "o", False),
    ("婆", "pó", 2, "p", "o", False),
    ("摸", "mō", 1, "m", "o", False),
    ("末", "mò", 4, "m", "o", False),
    # e 组
    ("饿", "è", 4, "", "e", False),
    ("鹅", "é", 2, "", "e", False),
    ("喝", "hē", 1, "h", "e", False),
    ("客", "kè", 4, "k", "e", False),
    ("乐", "lè", 4, "l", "e", False),
    ("特", "tè", 4, "t", "e", False),
    ("遮", "zhē", 1, "zh", "e", False),
    ("鸽", "gē", 1, "g", "e", False),
    # i 组
    ("皮", "pí", 2, "p", "i", False),
    ("比", "bǐ", 3, "b", "i", False),
    ("低", "dī", 1, "d", "i", False),
    ("提", "tí", 2, "t", "i", False),
    ("泥", "ní", 2, "n", "i", False),
    ("梨", "lí", 2, "l", "i", False),
    ("里", "lǐ", 3, "l", "i", False),
    ("鸡", "jī", 1, "j", "i", False),
    ("机", "jī", 1, "j", "i", False),
    ("起", "qǐ", 3, "q", "i", False),
    ("旗", "qí", 2, "q", "i", False),
    ("习", "xí", 2, "x", "i", False),
    ("洗", "xǐ", 3, "x", "i", False),
    ("戏", "xì", 4, "x", "i", False),
    # u 组
    ("布", "bù", 4, "b", "u", False),
    ("路", "lù", 4, "l", "u", False),
    ("湖", "hú", 2, "h", "u", False),
    ("苦", "kǔ", 3, "k", "u", False),
    ("哭", "kū", 1, "k", "u", False),
    ("图", "tú", 2, "t", "u", False),
    ("猪", "zhū", 1, "zh", "u", False),
    ("竹", "zhú", 2, "zh", "u", False),
    ("主", "zhǔ", 3, "zh", "u", False),
    ("住", "zhù", 4, "zh", "u", False),
    ("如", "rú", 2, "r", "u", False),
    ("入", "rù", 4, "r", "u", False),
    ("粗", "cū", 1, "c", "u", False),
    ("苏", "sū", 1, "s", "u", False),
    ("组", "zǔ", 3, "z", "u", False),
    ("服", "fú", 2, "f", "u", False),
    ("古", "gǔ", 3, "g", "u", False),
    ("读", "dú", 2, "d", "u", False),
    # ü 组
    ("驴", "lǘ", 2, "l", "ü", False),
    ("旅", "lǚ", 3, "l", "ü", False),
    ("居", "jū", 1, "j", "ü", False),
    ("举", "jǔ", 3, "j", "ü", False),
    ("去", "qù", 4, "q", "ü", False),
    ("曲", "qǔ", 3, "q", "ü", False),
    ("需", "xū", 1, "x", "ü", False),
    ("许", "xǔ", 3, "x", "ü", False),
    # ai 组
    ("爱", "ài", 4, "", "ai", False),
    ("奶", "nǎi", 3, "n", "ai", False),
    ("买", "mǎi", 3, "m", "ai", False),
    ("卖", "mài", 4, "m", "ai", False),
    ("带", "dài", 4, "d", "ai", False),
    ("太", "tài", 4, "t", "ai", False),
    ("台", "tái", 2, "t", "ai", False),
    ("该", "gāi", 1, "g", "ai", False),
    ("孩", "hái", 2, "h", "ai", False),
    ("才", "cái", 2, "c", "ai", False),
    ("猜", "cāi", 1, "c", "ai", False),
    ("赛", "sài", 4, "s", "ai", False),
    ("晒", "shài", 4, "sh", "ai", False),
    ("拆", "chāi", 1, "ch", "ai", False),
    # ei 组
    ("美", "měi", 3, "m", "ei", False),
    ("妹", "mèi", 4, "m", "ei", False),
    ("肥", "féi", 2, "f", "ei", False),
    ("给", "gěi", 3, "g", "ei", False),
    ("泪", "lèi", 4, "l", "ei", False),
    ("内", "nèi", 4, "n", "ei", False),
    # ui 组
    ("谁", "shuí", 2, "sh", "ui", False),
    ("翠", "cuì", 4, "c", "ui", False),
    ("岁", "suì", 4, "s", "ui", False),
    ("灰", "huī", 1, "h", "ui", False),
    ("回", "huí", 2, "h", "ui", False),
    ("会", "huì", 4, "h", "ui", False),
    ("追", "zhuī", 1, "zh", "ui", False),
    ("吹", "chuī", 1, "ch", "ui", False),
    # ao 组补充
    ("泡", "pào", 4, "p", "ao", False),
    ("饱", "bǎo", 3, "b", "ao", False),
    ("烧", "shāo", 1, "sh", "ao", False),
    ("超", "chāo", 1, "ch", "ao", False),
    ("找", "zhǎo", 3, "zh", "ao", False),
    # ou 组补充
    ("又", "yòu", 4, "y", "ou", False),
    ("有", "yǒu", 3, "y", "ou", False),
    ("收", "shōu", 1, "sh", "ou", False),
    ("周", "zhōu", 1, "zh", "ou", False),
    ("抽", "chōu", 1, "ch", "ou", False),
    ("柔", "róu", 2, "r", "ou", False),
    ("猴", "hóu", 2, "h", "ou", False),
    ("豆", "dòu", 4, "d", "ou", False),
    ("够", "gòu", 4, "g", "ou", False),
    # iu 组补充
    ("丢", "diū", 1, "d", "iu", False),
    ("修", "xiū", 1, "x", "iu", False),
    ("柳", "liǔ", 3, "l", "iu", False),
    ("流", "liú", 2, "l", "iu", False),
    ("就", "jiù", 4, "j", "iu", False),
    ("久", "jiǔ", 3, "j", "iu", False),
    # ie 组补充
    ("别", "bié", 2, "b", "ie", False),
    ("爹", "diē", 1, "d", "ie", False),
    ("灭", "miè", 4, "m", "ie", False),
    ("街", "jiē", 1, "j", "ie", False),
    ("鞋", "xié", 2, "x", "ie", False),
    # üe 组补充
    ("缺", "quē", 1, "q", "üe", False),
    ("确", "què", 4, "q", "üe", False),
    ("决", "jué", 2, "j", "üe", False),
    ("略", "lüè", 4, "l", "üe", False),
    # er
    ("儿", "ér", 2, "", "er", False),
    # an 组补充
    ("安", "ān", 1, "", "an", False),
    ("男", "nán", 2, "n", "an", False),
    ("谈", "tán", 2, "t", "an", False),
    ("篮", "lán", 2, "l", "an", False),
    ("感", "gǎn", 3, "g", "an", False),
    ("汗", "hàn", 4, "h", "an", False),
    ("弯", "wān", 1, "w", "an", False),
    ("完", "wán", 2, "w", "an", False),
    # en 组补充
    ("人", "rén", 2, "r", "en", False),
    ("真", "zhēn", 1, "zh", "en", False),
    ("陈", "chén", 2, "ch", "en", False),
    ("深", "shēn", 1, "sh", "en", False),
    ("森", "sēn", 1, "s", "en", False),
    ("根", "gēn", 1, "g", "en", False),
    ("恩", "ēn", 1, "", "en", False),
    # in 组补充
    ("亲", "qīn", 1, "q", "in", False),
    ("琴", "qín", 2, "q", "in", False),
    ("新", "xīn", 1, "x", "in", False),
    ("信", "xìn", 4, "x", "in", False),
    ("民", "mín", 2, "m", "in", False),
    ("您", "nín", 2, "n", "in", False),
    ("拼", "pīn", 1, "p", "in", False),
    ("品", "pǐn", 3, "p", "in", False),
    # un/ün 组补充
    ("轮", "lún", 2, "l", "un", False),
    ("蹲", "dūn", 1, "d", "un", False),
    ("晕", "yūn", 1, "", "", True),
    # ian 组补充
    ("点", "diǎn", 3, "d", "ian", False),
    ("尖", "jiān", 1, "j", "ian", False),
    ("见", "jiàn", 4, "j", "ian", False),
    ("连", "lián", 2, "l", "ian", False),
    ("脸", "liǎn", 3, "l", "ian", False),
    ("念", "niàn", 4, "n", "ian", False),
    ("棉", "mián", 2, "m", "ian", False),
    ("变", "biàn", 4, "b", "ian", False),
    ("片", "piàn", 4, "p", "ian", False),
    ("甜", "tián", 2, "t", "ian", False),
    ("先", "xiān", 1, "x", "ian", False),
    ("线", "xiàn", 4, "x", "ian", False),
    ("烟", "yān", 1, "y", "an", False),
    ("言", "yán", 2, "y", "an", False),
    # iao 组补充
    ("标", "biāo", 1, "b", "iao", False),
    ("表", "biǎo", 3, "b", "iao", False),
    ("飘", "piāo", 1, "p", "iao", False),
    ("票", "piào", 4, "p", "iao", False),
    ("教", "jiāo", 1, "j", "iao", False),
    ("叫", "jiào", 4, "j", "iao", False),
    ("角", "jiǎo", 3, "j", "iao", False),
    ("消", "xiāo", 1, "x", "iao", False),
    ("笑", "xiào", 4, "x", "iao", False),
    ("料", "liào", 4, "l", "iao", False),
    ("描", "miáo", 2, "m", "iao", False),
    # ia/ua 组补充
    ("虾", "xiā", 1, "x", "ia", False),
    ("蛙", "wā", 1, "w", "a", False),
    # uo 组补充
    ("拖", "tuō", 1, "t", "uo", False),
    ("落", "luò", 4, "l", "uo", False),
    ("错", "cuò", 4, "c", "uo", False),
    ("锁", "suǒ", 3, "s", "uo", False),
    ("桌", "zhuō", 1, "zh", "uo", False),
    ("过", "guò", 4, "g", "uo", False),
    # uan 组
    ("穿", "chuān", 1, "ch", "uan", False),
    ("船", "chuán", 2, "ch", "uan", False),
    ("乱", "luàn", 4, "l", "uan", False),
    ("酸", "suān", 1, "s", "uan", False),
    ("算", "suàn", 4, "s", "uan", False),
    ("关", "guān", 1, "g", "uan", False),
    ("欢", "huān", 1, "h", "uan", False),
    # ang 组补充
    ("帮", "bāng", 1, "b", "ang", False),
    ("忙", "máng", 2, "m", "ang", False),
    ("网", "wǎng", 3, "w", "ang", False),
    ("汤", "tāng", 1, "t", "ang", False),
    ("糖", "táng", 2, "t", "ang", False),
    ("狼", "láng", 2, "l", "ang", False),
    ("浪", "làng", 4, "l", "ang", False),
    ("桑", "sāng", 1, "s", "ang", False),
    ("唱", "chàng", 4, "ch", "ang", False),
    ("厂", "chǎng", 3, "ch", "ang", False),
    ("让", "ràng", 4, "r", "ang", False),
    ("放", "fàng", 4, "f", "ang", False),
    ("房", "fáng", 2, "f", "ang", False),
    # eng 组补充
    ("灯", "dēng", 1, "d", "eng", False),
    ("冷", "lěng", 3, "l", "eng", False),
    ("声", "shēng", 1, "sh", "eng", False),
    ("成", "chéng", 2, "ch", "eng", False),
    ("朋", "péng", 2, "p", "eng", False),
    ("梦", "mèng", 4, "m", "eng", False),
    ("更", "gēng", 1, "g", "eng", False),
    # ing 组补充
    ("清", "qīng", 1, "q", "ing", False),
    ("请", "qǐng", 3, "q", "ing", False),
    ("精", "jīng", 1, "j", "ing", False),
    ("定", "dìng", 4, "d", "ing", False),
    ("铃", "líng", 2, "l", "ing", False),
    ("名", "míng", 2, "m", "ing", False),
    ("平", "píng", 2, "p", "ing", False),
    ("冰", "bīng", 1, "b", "ing", False),
    ("病", "bìng", 4, "b", "ing", False),
    ("英", "yīng", 1, "", "", True),
    # ong 组补充
    ("动", "dòng", 4, "d", "ong", False),
    ("同", "tóng", 2, "t", "ong", False),
    ("通", "tōng", 1, "t", "ong", False),
    ("龙", "lóng", 2, "l", "ong", False),
    ("送", "sòng", 4, "s", "ong", False),
    ("钟", "zhōng", 1, "zh", "ong", False),
    ("冲", "chōng", 1, "ch", "ong", False),
    ("孔", "kǒng", 3, "k", "ong", False),
    # 整体认读补充
    ("也", "yě", 3, "", "", True),
    ("叶", "yè", 4, "", "", True),
    ("语", "yǔ", 3, "", "", True),
    ("玉", "yù", 4, "", "", True),
    ("约", "yuē", 1, "", "", True),
    ("阅", "yuè", 4, "", "", True),
]

TONE_OPTS = ["一声", "二声", "三声", "四声"]
SIMPLE_FINALS = ["a", "o", "e", "i", "u", "ü"]
COMPOUND_FINALS = ["ai", "ei", "ui", "ao", "ou", "iu", "ie", "üe",
                   "an", "en", "in", "un", "ün", "ang", "eng", "ing", "ong",
                   "ia", "ua", "uo", "iao", "ian", "uan", "uang"]
# 复韵母（不含鼻音尾）与鼻韵母，韵母分类题型用
FU_FINALS = ["ai", "ei", "ui", "ao", "ou", "iu", "ie", "üe", "uo", "ia", "ua", "iao"]
BI_FINALS = ["an", "en", "in", "un", "ün", "ang", "eng", "ing", "ong",
             "ian", "uan", "uang"]
ALL_INITIALS = ["b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
                "j", "q", "x", "zh", "ch", "sh", "r", "z", "c", "s", "y", "w"]
FLAT_INITIALS = {"z", "c", "s"}       # 平舌音
CURL_INITIALS = {"zh", "ch", "sh", "r"}  # 翘舌音
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

# ---- 字表自检：拼音与声母/韵母拆分必须能拼合（防手误）----
def _strip_tone(s):
    return "".join(ACCENTED.get(c, (c, ""))[0] for c in s)


def _check_chars():
    for ch, py, tone, sm, ym, zt in CHARS:
        if zt:
            assert sm == "" and ym == "", f"整体认读字 {ch} 不应拆声母/韵母"
            continue
        nude = _strip_tone(py)
        expect = sm + ym
        # 拼写规则：ü 遇 j/q/x/y 去两点
        if expect and expect[0] in "jqxy":
            expect = expect.replace("ü", "u")
        assert nude == expect, f"字表错误：{ch} {py} 声母={sm!r} 韵母={ym!r} 拼合={expect!r}"
        marks = sum(1 for c in py if c in ACCENTED)
        assert marks == 1, f"字表错误：{ch} {py} 声调符号数量应为 1，实际 {marks}"


_check_chars()

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


# 1. 声调判断（全字表）
tone_chars = list(CHARS)
random.shuffle(tone_chars)
for ch, py, tone, sm, ym, zt in tone_chars:
    make_choice(f"「{ch}」的声调是第几声？", TONE_OPTS[tone - 1],
                [t for t in TONE_OPTS if t != TONE_OPTS[tone - 1]])

# 2. 声母（全量非零声母字）
sm_chars = [c for c in CHARS if c[3]]
random.shuffle(sm_chars)
for ch, py, tone, sm, ym, zt in sm_chars:
    others = pick_others(ALL_INITIALS, sm)
    make_choice(f"「{ch}」的声母是？", sm, others)

# 3. 单韵母（全量）
single_chars = [c for c in CHARS if c[4] in SIMPLE_FINALS and not c[5]]
random.shuffle(single_chars)
for ch, py, tone, sm, ym, zt in single_chars:
    others = pick_others(SIMPLE_FINALS, ym)
    make_choice(f"「{ch}」的韵母是？", ym, others)

# 4. 复韵母 / 鼻韵母（全量）
compound_chars = [c for c in CHARS if c[4] in COMPOUND_FINALS]
random.shuffle(compound_chars)
for ch, py, tone, sm, ym, zt in compound_chars:
    others = pick_others(COMPOUND_FINALS, ym)
    make_choice(f"「{ch}」的韵母是？", ym, others)

# 5. 整体认读音节（每个音节 4 轮不同干扰）
for r in range(4):
    for i in range(len(ZHENG)):
        correct = ZHENG[i]
        others = pick_others(NOT_ZHENG, correct)
        random.shuffle(others)
        make_choice("下面哪一个是整体认读音节？", correct, others)

# 6. 拼写选择（全字表）
spell_chars = list(CHARS)
random.shuffle(spell_chars)
for ch, py, tone, sm, ym, zt in spell_chars:
    ds = py_distractors(py)
    if len(ds) < 3:
        continue
    make_choice(f"「{ch}」的拼音是？", py, ds[:3])

# 7. 前鼻韵母 / 后鼻韵母（全量，含 ian/uan）
front = ["an", "en", "in", "un", "ün", "ian", "uan"]
back = ["ang", "eng", "ing", "ong", "uang"]
nose_chars = [c for c in CHARS if c[4] in front + back]
random.shuffle(nose_chars)
for ch, py, tone, sm, ym, zt in nose_chars:
    is_front = ym in front
    make_choice(f"「{ch}」的韵母是前鼻韵母还是后鼻韵母？",
                "前鼻韵母" if is_front else "后鼻韵母",
                ["后鼻韵母"] if is_front else ["前鼻韵母"])

# 8. 平舌音 / 翘舌音（声母为 z/c/s/zh/ch/sh/r 的字，全量）
zcs_chars = [c for c in CHARS if c[3] in FLAT_INITIALS or c[3] in CURL_INITIALS]
random.shuffle(zcs_chars)
for ch, py, tone, sm, ym, zt in zcs_chars:
    is_flat = sm in FLAT_INITIALS
    make_choice(f"「{ch}」的声母是平舌音还是翘舌音？",
                "平舌音" if is_flat else "翘舌音",
                ["翘舌音"] if is_flat else ["平舌音"])

# 9. 韵母分类：单韵母 / 复韵母 / 鼻韵母（er 字跳过）
YM_CLASS = [(SIMPLE_FINALS, "单韵母"), (FU_FINALS, "复韵母"), (BI_FINALS, "鼻韵母")]
class_chars = [c for c in CHARS if not c[5] and c[4] != "er"]
random.shuffle(class_chars)
for ch, py, tone, sm, ym, zt in class_chars:
    for pool, label in YM_CLASS:
        if ym in pool:
            others = [lab for _, lab in YM_CLASS if lab != label]
            make_choice(f"「{ch}」的韵母 {ym} 属于哪一类？", label, others)
            break

# 10. 同韵母找字：和 X 韵母相同的字是？（仅韵母至少 2 字的；正确答案排除同音字）
ym_groups = {}
for c in CHARS:
    if c[5] or not c[4] or c[4] == "er":
        continue
    ym_groups.setdefault(c[4], []).append(c)
same_chars = [c for grp in ym_groups.values() if len(grp) >= 2 for c in grp]
random.shuffle(same_chars)
for ch, py, tone, sm, ym, zt in same_chars:
    partners = [c for c in ym_groups[ym] if c[0] != ch and c[1] != py]
    if not partners:
        continue
    correct = random.choice(partners)[0]
    diff_pool = [c[0] for g, grp in ym_groups.items() if g != ym for c in grp]
    others = pick_others(diff_pool, correct)
    make_choice(f"和「{ch}」韵母相同的字是？", correct, others)

# 11. 拼读音节选字：「bǐ 是下面哪个字的读音？」（干扰项排除同音字，防双正确）
read_chars = list(CHARS)
random.shuffle(read_chars)
for ch, py, tone, sm, ym, zt in read_chars:
    diff_pool = [c[0] for c in CHARS if c[1] != py]
    others = pick_others(diff_pool, ch)
    make_choice(f"{py} 是下面哪个字的读音？", ch, others)

# ============================================================
# 三、英语（一年级起点：颜色/数字/动物/水果/文具/身体/家庭/动作，全选择题）
# 注意：锁屏键盘只支持数字输入，英语题只能做选择题
# ============================================================

# (英文, 中文, 类别)
ENGLISH_WORDS = [
    # 颜色
    ("red", "红色", "颜色"), ("blue", "蓝色", "颜色"), ("yellow", "黄色", "颜色"),
    ("green", "绿色", "颜色"), ("black", "黑色", "颜色"), ("white", "白色", "颜色"),
    ("pink", "粉色", "颜色"), ("brown", "棕色", "颜色"),
    # 数字
    ("one", "一", "数字"), ("two", "二", "数字"), ("three", "三", "数字"),
    ("four", "四", "数字"), ("five", "五", "数字"), ("six", "六", "数字"),
    ("seven", "七", "数字"), ("eight", "八", "数字"), ("nine", "九", "数字"),
    ("ten", "十", "数字"),
    # 动物
    ("cat", "猫", "动物"), ("dog", "狗", "动物"), ("pig", "猪", "动物"),
    ("duck", "鸭子", "动物"), ("bear", "熊", "动物"), ("bird", "鸟", "动物"),
    ("panda", "熊猫", "动物"), ("monkey", "猴子", "动物"), ("elephant", "大象", "动物"),
    ("tiger", "老虎", "动物"), ("lion", "狮子", "动物"), ("zebra", "斑马", "动物"),
    ("rabbit", "兔子", "动物"), ("fish", "鱼", "动物"),
    # 水果
    ("apple", "苹果", "水果"), ("banana", "香蕉", "水果"), ("orange", "橙子", "水果"),
    ("grape", "葡萄", "水果"), ("pear", "梨", "水果"), ("peach", "桃子", "水果"),
    ("watermelon", "西瓜", "水果"), ("strawberry", "草莓", "水果"),
    # 文具
    ("pen", "钢笔", "文具"), ("pencil", "铅笔", "文具"), ("ruler", "尺子", "文具"),
    ("eraser", "橡皮", "文具"), ("book", "书", "文具"), ("bag", "书包", "文具"),
    ("crayon", "蜡笔", "文具"),
    # 身体部位
    ("eye", "眼睛", "身体"), ("ear", "耳朵", "身体"), ("nose", "鼻子", "身体"),
    ("mouth", "嘴", "身体"), ("hand", "手", "身体"), ("arm", "胳膊", "身体"),
    ("leg", "腿", "身体"), ("foot", "脚", "身体"), ("face", "脸", "身体"),
    ("head", "头", "身体"),
    # 家庭
    ("father", "爸爸", "家庭"), ("mother", "妈妈", "家庭"),
    ("brother", "兄弟", "家庭"), ("sister", "姐妹", "家庭"),
    ("grandfather", "爷爷", "家庭"), ("grandmother", "奶奶", "家庭"),
    # 学校
    ("teacher", "老师", "学校"), ("student", "学生", "学校"),
    ("school", "学校", "学校"), ("class", "班级", "学校"),
    # 动作
    ("stand", "站", "动作"), ("sit", "坐", "动作"), ("jump", "跳", "动作"),
    ("run", "跑", "动作"), ("fly", "飞", "动作"), ("swim", "游泳", "动作"),
    ("sing", "唱歌", "动作"), ("dance", "跳舞", "动作"),
]


def en_distractors(words, correct, key):
    """同类优先取干扰项，不足时从全表补"""
    same_cat = [w[key] for w in words if w[key] != correct and w[2] == cat_of(correct, words)]
    all_v = [w[key] for w in words if w[key] != correct]
    others = same_cat[:6]
    random.shuffle(others)
    if len(others) < 3:
        others = pick_others(all_v, correct)
    else:
        others = others[:3]
    return others


def cat_of(value, words):
    for w in words:
        if w[0] == value or w[1] == value:
            return w[2]
    return ""


# 1. 英译中（同类干扰）
for en, zh, cat in ENGLISH_WORDS:
    others = en_distractors(ENGLISH_WORDS, zh, 1)
    make_choice(f"{en} 的中文意思是？", zh, others)

# 2. 中译英（同类干扰）
for en, zh, cat in ENGLISH_WORDS:
    others = en_distractors(ENGLISH_WORDS, en, 0)
    make_choice(f"「{zh}」的英文是？", en, others)

# ============================================================
# 输出（纯题目数组，不分学科）
# ============================================================
bank = math_qs + pinyin_qs + english_qs
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(bank, f, ensure_ascii=False, separators=(",", ":"))

total = len(bank)
print(f"题库生成完成 -> {os.path.normpath(OUT)}")
print(f"  合计   : {total} 题")
if total < 1500:
    print("警告：题量偏少，请检查生成参数", file=sys.stderr)
    sys.exit(1)
