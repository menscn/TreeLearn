"""Restore the 32 lost top-level classes from CFR decompiled output into src/main/java.

- Strips the CFR header comment block.
- Skips restore if the target file already exists (safety).
- Prints what it did.
"""
import os
import re

ROOT = r"D:\Project\Android\Treelearn"
DEC = os.path.join(ROOT, "artifacts", "recovery", "decompiled")
DST = os.path.join(ROOT, "android", "app", "src", "main", "java")

ADDED = [
    "com/treelearn/app/artifact/HomeDerivedPort.java",
    "com/treelearn/app/artifact/TitleArtifactApplier.java",
    "com/treelearn/app/generation/MessageTranslator.java",
    "com/treelearn/app/generation/QuickModelGateway.java",
    "com/treelearn/app/screen/ModelSettingsScreen.java",
    "com/treelearn/app/screen/ProviderAdminScreen.java",
    "com/treelearn/app/screen/SearchServiceSettingsScreen.java",
    "com/treelearn/app/screen/VoiceSettingsScreen.java",
    "com/treelearn/app/search/web/ChatSearchStep.java",
    "com/treelearn/app/search/web/HttpWebSearchGateway.java",
    "com/treelearn/app/search/web/KeystoreSearchCredentials.java",
    "com/treelearn/app/search/web/SearchCredentials.java",
    "com/treelearn/app/search/web/SearchInjection.java",
    "com/treelearn/app/search/web/WebSearchGateway.java",
    "com/treelearn/app/ui/ChatSpeaker.java",
    "com/treelearn/app/ui/HeatmapLayout.java",
    "com/treelearn/app/ui/HeatmapView.java",
    "com/treelearn/app/ui/HomeStats.java",
    "com/treelearn/app/ui/ModelSettingsPresenter.java",
    "com/treelearn/app/ui/ProviderAdminPresenter.java",
    "com/treelearn/app/ui/SearchSettingsPresenter.java",
    "com/treelearn/app/ui/VoiceSettingsPresenter.java",
    "com/treelearn/app/voice/SystemTtsEngine.java",
    "com/treelearn/app/voice/TtsEngine.java",
    "com/treelearn/app/wiring/PreferenceSearchSettingsStore.java",
    "com/treelearn/app/wiring/PreferenceVoiceSettingsStore.java",
    "com/treelearn/app/wiring/QuietTelemetry.java",
    "com/treelearn/app/wiring/SqliteModelSettingsStore.java",
    "com/treelearn/app/wiring/SqliteProviderAdminStore.java",
    "com/treelearn/app/wiring/SqliteQuickModelRoutes.java",
    "com/treelearn/app/wiring/SqliteTranslationRoutes.java",
    "com/treelearn/app/wiring/TelemetrySink.java",
]

CFR_HEADER = re.compile(r"^/\*\s*\* Decompiled with CFR.*?\*/\s*", re.DOTALL)


def clean(text):
    return CFR_HEADER.sub("", text, count=1)


def main():
    restored, skipped = [], []
    for rel in ADDED:
        src = os.path.join(DEC, rel.replace("/", os.sep))
        dst = os.path.join(DST, rel.replace("/", os.sep))
        if not os.path.isfile(src):
            print(f"MISSING decompiled: {rel}")
            continue
        if os.path.exists(dst):
            skipped.append(rel)
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(src, "r", encoding="utf-8") as fh:
            text = fh.read()
        with open(dst, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(clean(text))
        restored.append(rel)
    print(f"restored: {len(restored)}")
    for r in restored:
        print("  +", r)
    if skipped:
        print(f"SKIPPED (already exist): {len(skipped)}")
        for s in skipped:
            print("  =", s)


if __name__ == "__main__":
    main()
