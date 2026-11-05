#!/bin/zsh
# TVLock 构建脚本：编译 APK + 跑单元测试
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
if [ ! -f "$ROOT/.env/env.sh" ]; then
  echo "==> 构建环境不存在，先运行 ./setup_env.sh"
  exit 1
fi
source "$ROOT/.env/env.sh"
cd "$ROOT"

echo "==> 编译 APK（首次会联网下载依赖，耐心等待）..."
gradle --console=plain -q assembleDebug

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
  echo "!! APK 未生成，请检查上方错误"
  exit 1
fi
SIZE=$(du -h "$APK" | cut -f1)
echo "==> APK 编译成功: $APK ($SIZE)"

echo "==> 运行单元测试..."
gradle --console=plain -q test

echo ""
echo "==> 全部完成！"
echo "    APK   : $APK"
echo "    下一步: ./install.sh <电视IP>"
