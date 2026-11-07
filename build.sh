#!/bin/zsh
# TVLock 构建脚本：编译 APK + 跑单元测试，产出正式命名的 TVLock-v*.apk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
if [ ! -f "$ROOT/.env/env.sh" ]; then
  echo "==> 构建环境不存在，先运行 ./setup_env.sh"
  exit 1
fi
source "$ROOT/.env/env.sh"
cd "$ROOT"

echo "==> 编译 APK（本地测试包 dev；发版走 git tag 触发 CI，产物名 TVLock-v*.apk）（首次会联网下载依赖，耐心等待）..."
gradle --console=plain -q assembleDebug

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "!! APK 未生成，请检查上方错误"
  exit 1
fi

# 正式命名产物（版本号走 CI tag；本地包固定 dev）
OFFICIAL="$ROOT/TVLock-dev.apk"
cp -f "$APK" "$OFFICIAL"
SIZE=$(du -h "$OFFICIAL" | cut -f1)
echo "==> APK 编译成功: $OFFICIAL ($SIZE)"

echo "==> 运行单元测试..."
gradle --console=plain -q test

echo ""
echo "==> 全部完成！"
echo "    APK   : $OFFICIAL"
echo "    下一步: ./install.sh <电视IP>"
