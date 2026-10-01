#!/usr/bin/env bash
# Publica o Pixi no GitHub e dispara o build do APK.
# Uso:
#   export GH_TOKEN="ghp_..."
#   bash tools/publish.sh

set -euo pipefail

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "Defina: export GH_TOKEN=\"ghp_...\""
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Token: ${GH_TOKEN:0:6}…"

# Usuário
OWNER=$(curl -sS -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user 2>/dev/null \
  | tr ',' '\n' | sed -n 's/.*"login": *"\([^"]*\)".*/\1/p' | head -1)

if [[ -z "$OWNER" ]]; then
  echo "Token inválido ou sem rede."
  exit 1
fi
echo "Owner: $OWNER"

REPO_NAME="${REPO_NAME:-Pixi}"
REPO_FULL="$OWNER/$REPO_NAME"
echo "Repo:  $REPO_FULL"

# Cria repo (não falha se já existir)
curl -sS -X POST \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO_NAME\",\"private\":false}" >/dev/null 2>&1 || true

# Git
command -v git >/dev/null || { echo "Instale: pkg install git -y"; exit 1; }

if [[ ! -d .git ]]; then
  git init -b main
fi
git config user.email "${GIT_EMAIL:-pixi@local}"
git config user.name  "${GIT_NAME:-Pixi}"

git add -A
if ! git diff --cached --quiet 2>/dev/null; then
  git commit -m "Pixi: companion Android com sprites e interacoes" || true
elif [[ -z "$(git log -1 --oneline 2>/dev/null)" ]]; then
  git commit --allow-empty -m "Pixi initial" || true
fi

REMOTE="https://x-access-token:${GH_TOKEN}@github.com/${REPO_FULL}.git"
git remote remove origin 2>/dev/null || true
git remote add origin "$REMOTE"

echo "Push..."
git push -u origin main --force

git remote set-url origin "https://github.com/${REPO_FULL}.git"

echo ""
echo "OK → https://github.com/${REPO_FULL}"
echo "APK: Actions → Build APK → Artifacts → Pixi-debug-apk"
