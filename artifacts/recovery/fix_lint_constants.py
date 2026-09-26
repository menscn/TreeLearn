"""Replace CFR magic-number View constants with named constants in the 4 recovered settings screens."""
import io
import re

BASE = r"D:/Project/Android/Treelearn/android/app/src/main/java/com/treelearn/app/screen/"
FILES = ["ModelSettingsScreen.java", "ProviderAdminScreen.java",
         "SearchServiceSettingsScreen.java", "VoiceSettingsScreen.java"]

RULES = [
    (re.compile(r"\.setOrientation\(0\)"), ".setOrientation(LinearLayout.HORIZONTAL)"),
    (re.compile(r"\.setOrientation\(1\)"), ".setOrientation(LinearLayout.VERTICAL)"),
    (re.compile(r"\.setVisibility\(0\)"), ".setVisibility(View.VISIBLE)"),
    (re.compile(r"\.setVisibility\(8\)"), ".setVisibility(View.GONE)"),
    (re.compile(r"\.setTypeface\(title\.getTypeface\(\), 1\)"), ".setTypeface(title.getTypeface(), Typeface.BOLD)"),
    (re.compile(r"\.setTypeface\(null, 1\)"), ".setTypeface(null, Typeface.BOLD)"),
    (re.compile(r"\.setImportantForAutofill\(2\)"), ".setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO)"),
    (re.compile(r"\.setImportantForAutofill\(1\)"), ".setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_YES)"),
]


def fix(text):
    # ternary visibility forms first: (cond ? 0 : 8) -> (cond ? View.VISIBLE : View.GONE)
    text = re.sub(r"\? 0 : 8\)", "? View.VISIBLE : View.GONE)", text)
    text = re.sub(r"\? 8 : 0\)", "? View.GONE : View.VISIBLE)", text)
    for pattern, replacement in RULES:
        if replacement == "KEEP-MANUAL":
            continue
        text = pattern.sub(replacement, text)
    return text


for name in FILES:
    path = BASE + name
    with io.open(path, encoding="utf-8") as fh:
        original = fh.read()
    fixed = fix(original)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(fixed)
    print(name, "changed" if fixed != original else "unchanged")
