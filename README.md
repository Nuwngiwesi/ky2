# LYNKO Keyboard

Offline-first Android keyboard: English + Sinhala (Singlish → සිංහල), themes, swipe typing, emoji,
clipboard, stylish text, voice typing, translate and AI tools.

## Files (folders නැහැ)
settings.gradle · build.gradle · gradle.properties · AndroidManifest.xml · method.xml ·
LynkoIME.java · MainActivity.java · README.md · **logo.jpg** (ඔයාගේ image එක)

`logo.jpg` repo root එකට දැම්මාම app icon + "LYNKO" keyboard theme + app UI logo එකට auto use වෙනවා.
(≤ 512×512 නම් හොඳයි.) නැතිනම් default gradient logo එක පෙන්වනවා.

## Build
GitHub Actions workflow එකෙන් (`.github/workflows/build.yml`) APK එක build වෙනවා,
නැත්නම් Android Studio වලින් Open කරලා Run.

## Notes
- Translate: Google Translate (unofficial endpoint, internet ඕන, නතර වෙන්න පුළුවන්).
- AI tools: ඔයාගේම Anthropic API key එක Settings වල දාන්න.
- Voice typing: phone එකේ speech service (Google) ඕන.
- Swipe typing: built-in English word list එකෙන් (LynkoIME.java → Engine.WORDS වලට words එකතු කරන්න පුළුවන්).
- "Compact" mode = narrow keyboard (left/right/center). Android වලට keyboard එකක් real floating window එකක් කරන්න බැහැ.
