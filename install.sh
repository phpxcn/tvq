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
PKG="com.android.tvlock"
WD_PKG="com.android.tvlockwd"

# 优先用正式命名的 TVLock-*.apk（CI 发版包 TVLock-v*.apk / 本地测试包 TVLock-dev.apk）
APK="$(ls -t "$ROOT"/TVLock-*.apk 2>/dev/null | head -1)"
[ -z "$APK" ] && APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"

if [ "${1:-}" = "--uninstall" ]; then
  TV_IP="${2:?用法: ./install.sh --uninstall <电视IP>}"
  adb connect "$TV_IP:5555" || true
  adb uninstall "$PKG" || true
  adb uninstall "$WD_PKG" || true
  echo "==> 已卸载（主 App + 看门狗）"
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
    echo "==> 安装主 App ..."
    # -d 允许版本号降级（debug 包专用）：发版包可覆盖设备上的高版本测试包
    adb install -r -g -d "$APK"

    echo "==> 授予悬浮窗权限（SYSTEM_ALERT_WINDOW）..."
    adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow || \
      echo "   （个别系统不支持 appops，跳过不影响基本功能）"

    echo "==> 启动常驻服务..."
    adb shell am start-foreground-service -n "$PKG/com.tvlock.app.service.LockCoreService" 2>/dev/null || \
      adb shell am startservice -n "$PKG/com.tvlock.app.service.LockCoreService" 2>/dev/null || true

    # 看门狗双包仅作可选增强（仓库 watchdog module），默认不装：
    # hippo 伪装后主 App 已不被强杀；设备上已装旧看门狗的会继续双向互保

    # 打开一次设置页（部分电视需要前台出现过应用）
    adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1 || true

    echo ""
    echo "=========================================="
    echo " 安装完成！"
    echo " 手机管理面板（同一 WiFi）："
    echo "   http://tvlock.local:8080"
    echo "   http://$TV_IP:8080"
    echo ""
    echo " 防逃提示（若设备提供）："
    echo "   在电视设置里找「无障碍」入口，开启「电视答题锁·守卫」"
    echo "   （开启后按主页键锁屏不退；部分机型如小米激光投影仪实测无此入口，"
    echo "    可忽略——按主页键退桌面后重新打开应用即恢复锁屏）"
    echo "=========================================="
    echo " 别忘了在电视设置里允许本应用【开机自启动】"
    echo " （详见 docs/安装指南.md，否则断电后需手动打开应用）"
    ;;
esac
