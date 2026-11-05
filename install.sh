#!/bin/zsh
# TVLock 安装到电视（网络 ADB）
# 用法：
#   ./install.sh <电视IP>          # 安装/升级 + 授权 + 启动
#   ./install.sh <电视IP> --logs   # 实时查看应用日志
#   ./install.sh <电视IP> --reboot # 重启电视（验证断电续题）
#   ./install.sh --uninstall <电视IP>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
source "$ROOT/.env/env.sh" 2>/dev/null || true
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.tvlock.app"

if [ "${1:-}" = "--uninstall" ]; then
  TV_IP="${2:?用法: ./install.sh --uninstall <电视IP>}"
  adb connect "$TV_IP:5555" || true
  adb uninstall "$PKG" || true
  echo "==> 已卸载"
  exit 0
fi

TV_IP="${1:?用法: ./install.sh <电视IP> [选项]  （电视IP见 设置-网络-状态信息）}"
ACTION="${2:-install}"

if [ ! -f "$APK" ]; then
  echo "!! 找不到 APK，先运行 ./build.sh"
  exit 1
fi

echo "==> 连接电视 $TV_IP ..."
adb connect "$TV_IP:5555" || true
adb devices | grep -q "$TV_IP" || { echo "!! 连接失败：确认电视已开启网络调试（见 docs/安装指南.md）"; exit 1; }

case "$ACTION" in
  --logs)
    echo "==> 实时日志（Ctrl+C 退出）"
    adb logcat -s TVLock
    ;;
  --reboot)
    echo "==> 重启电视（重启后应自动恢复锁定状态）"
    adb reboot
    ;;
  *)
    echo "==> 安装 APK ..."
    adb install -r -g "$APK"

    echo "==> 授予看门狗后台拉起权限（SYSTEM_ALERT_WINDOW）..."
    adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow || \
      echo "   （个别系统不支持 appops，跳过不影响基本功能）"

    echo "==> 启动常驻服务..."
    adb shell am start-foreground-service -n "$PKG/com.tvlock.app.service.LockCoreService" 2>/dev/null || \
      adb shell am startservice -n "$PKG/com.tvlock.app.service.LockCoreService" 2>/dev/null || true

    # 打开一次设置页（部分电视需要前台出现过应用）
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1 || true

    echo ""
    echo "=========================================="
    echo " 安装完成！"
    echo " 手机管理面板（同一 WiFi）："
    echo "   http://tvlock.local:8080"
    echo "   http://$TV_IP:8080"
    echo "=========================================="
    echo " 别忘了在电视设置里允许本应用【开机自启动】"
    echo " （详见 docs/安装指南.md，否则断电后需手动打开应用）"
    ;;
esac
