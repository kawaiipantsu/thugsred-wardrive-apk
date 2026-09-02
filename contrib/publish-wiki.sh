#!/usr/bin/env bash
# Publish the GitHub Wiki from docs/. docs/ is the single source of truth; the
# wiki is a derived mirror.
#
# ONE-TIME: the wiki's git repo only exists after the first page is created in
# the browser. Open
#   https://github.com/kawaiipantsu/thugsred-wardrive-apk/wiki
# click "Create the first page", save anything, then run this script.
#
# Usage:  contrib/publish-wiki.sh
set -euo pipefail

REPO_SSH="git@github.com:kawaiipantsu/thugsred-wardrive-apk.wiki.git"
RAW="https://raw.githubusercontent.com/kawaiipantsu/thugsred-wardrive-apk/main"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

git clone --quiet "$REPO_SSH" "$WORK/wiki" 2>/dev/null || {
  echo "Wiki repo not found. Create the first page in the browser first:" >&2
  echo "  https://github.com/kawaiipantsu/thugsred-wardrive-apk/wiki" >&2
  exit 1
}

cd "$WORK/wiki"
git rm -q -rf . 2>/dev/null || true

# docs file  ->  wiki page name
declare -A MAP=(
  ["README.md"]="Home.md"
  ["Using-the-App.md"]="Using-the-App.md"
  ["Optimising-your-phone.md"]="Optimising-your-phone.md"
  ["Deploy-macOS.md"]="Deploy-on-macOS.md"
  ["Deploy-Linux.md"]="Deploy-on-Linux.md"
  ["Deploy-Windows.md"]="Deploy-on-Windows.md"
  ["screenshots/README.md"]="Screenshots.md"
)

for src in "${!MAP[@]}"; do
  cp "$ROOT/docs/$src" "${MAP[$src]}"
done

# Rewrite intra-doc links to wiki page names, and asset paths to raw URLs.
sed -i -E \
  -e 's#\]\(Using-the-App\.md\)#](Using-the-App)#g' \
  -e 's#\]\(Optimising-your-phone\.md\)#](Optimising-your-phone)#g' \
  -e 's#\]\(Deploy-macOS\.md\)#](Deploy-on-macOS)#g' \
  -e 's#\]\(Deploy-Linux\.md\)#](Deploy-on-Linux)#g' \
  -e 's#\]\(Deploy-Windows\.md\)#](Deploy-on-Windows)#g' \
  -e 's#\]\(README\.md\)#](Home)#g' \
  -e 's#\]\(screenshots/(README\.md)?\)#](Screenshots)#g' \
  -e 's#\]\(docs/([A-Za-z-]+)\.md\)#](\1)#g' \
  -e "s#\\((docs/screenshots/[a-z_]+\\.png)\\)#($RAW/\\1)#g" \
  -e "s#\\(assets/banner\\.png\\)#($RAW/assets/banner.png)#g" \
  -e 's#\]\(LICENSE\)#](https://github.com/kawaiipantsu/thugsred-wardrive-apk/blob/main/LICENSE)#g' \
  ./*.md

# Banner at the top of Home.
printf '<p align="center"><img src="%s/assets/banner.png" alt="THUGS Wardrive" width="100%%"></p>\n\n' "$RAW" \
  | cat - Home.md > Home.tmp && mv Home.tmp Home.md

# Screenshots page: embed the images instead of just listing filenames.
cat > Screenshots.md <<EOF
# Screenshots

Rendered from the real Jetpack Compose UI on the JVM (Robolectric + Roborazzi)
with sample data — regenerate with \`./gradlew :app:recordRoborazziDebug\`.

## List
![List]($RAW/docs/screenshots/list.png)

## Map
![Map]($RAW/docs/screenshots/map.png)

## Stats
![Stats]($RAW/docs/screenshots/stats.png)

## Scope
![Scope]($RAW/docs/screenshots/scope.png)

## First-run onboarding
![Onboarding]($RAW/docs/screenshots/onboarding.png)

## Scanning (empty state)
![Scanning]($RAW/docs/screenshots/list_empty.png)

## About
![About]($RAW/docs/screenshots/about.png)
EOF

cat > _Sidebar.md <<'EOF'
### THUGS Wardrive

- [Home](Home)
- [Using the App](Using-the-App)
- [Optimising your phone](Optimising-your-phone)
- [Screenshots](Screenshots)

**Deploy the APK**
- [macOS](Deploy-on-macOS)
- [Linux](Deploy-on-Linux)
- [Windows](Deploy-on-Windows)

**Links**
- [Latest release](https://github.com/kawaiipantsu/thugsred-wardrive-apk/releases/latest)
- [wardrive.thugs.red](https://wardrive.thugs.red)
EOF

cat > _Footer.md <<'EOF'
Generated from [`docs/`](https://github.com/kawaiipantsu/thugsred-wardrive-apk/tree/main/docs) by `contrib/publish-wiki.sh` — edit there, not here.
EOF

git add -A
if git diff --cached --quiet; then
  echo "Wiki already up to date."
else
  git commit -q -m "Sync wiki from docs/"
  git push -q origin HEAD
  echo "Wiki published."
fi
