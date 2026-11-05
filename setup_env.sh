#!/bin/zsh
# TVLock 构建环境一键搭建
# 全部安装在项目内 .env/ 目录，不污染系统
set -euo pipefail
setopt null_glob 2>/dev/null || true

ROOT="$(cd "$(dirname "$0")" && pwd)"
ENV_DIR="$ROOT/.env"
JDK_DIR="$ENV_DIR/jdk"
SDK_DIR="$ENV_DIR/android-sdk"
GRADLE_DIR="$ENV_DIR/gradle"
DL_DIR="$ENV_DIR/downloads"
mkdir -p "$JDK_DIR" "$SDK_DIR" "$GRADLE_DIR" "$DL_DIR"

GRADLE_VER="8.9"
CMDTOOLS_ZIP="commandlinetools-mac-11076708_latest.zip"

ARCH="$(uname -m)"
case "$ARCH" in
  arm64) ADOPT_ARCH="aarch64" ;;
  *)     ADOPT_ARCH="x64" ;;
esac
echo "==> CPU 架构: $ARCH (Adoptium: $ADOPT_ARCH)"

# ---------- 1. JDK 17 (Temurin) ----------
if [ ! -x "$JDK_DIR/current/Contents/Home/bin/java" ]; then
  if [ ! -f "$DL_DIR/jdk17.tar.gz" ] || ! tar -tzf "$DL_DIR/jdk17.tar.gz" > /dev/null 2>&1; then
    echo "==> [1/3] 下载 JDK 17 (Temurin) ..."
    curl -L --retry 3 --progress-bar -o "$DL_DIR/jdk17.tar.gz" \
      "https://api.adoptium.net/v3/binary/latest/17/ga/mac/${ADOPT_ARCH}/jdk/hotspot/normal/eclipse"
  else
    echo "==> [1/3] JDK 压缩包已存在，直接解压"
  fi
  rm -rf "$JDK_DIR"/jdk-*
  tar -xzf "$DL_DIR/jdk17.tar.gz" -C "$JDK_DIR"
  JDK_REAL="$(find "$JDK_DIR" -maxdepth 1 -type d -name 'jdk-*' | head -1)"
  ln -sfn "$JDK_REAL" "$JDK_DIR/current"
else
  echo "==> [1/3] JDK 已存在，跳过下载"
fi
export JAVA_HOME="$JDK_DIR/current/Contents/Home"
"$JAVA_HOME/bin/java" -version 2>&1 | head -2

# ---------- 2. Android SDK ----------
SDKM="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKM" ]; then
  echo "==> [2/3] 下载 Android cmdline-tools ..."
  curl -L --retry 3 --progress-bar -o "$DL_DIR/$CMDTOOLS_ZIP" \
    "https://dl.google.com/android/repository/$CMDTOOLS_ZIP"
  rm -rf "$SDK_DIR/tmpcmd" "$SDK_DIR/cmdline-tools"
  unzip -q -o "$DL_DIR/$CMDTOOLS_ZIP" -d "$SDK_DIR/tmpcmd"
  mkdir -p "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/tmpcmd/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$SDK_DIR/tmpcmd"
else
  echo "==> [2/3] cmdline-tools 已存在，跳过下载"
fi

if [ ! -d "$SDK_DIR/platform-tools" ] || [ ! -d "$SDK_DIR/platforms/android-34" ]; then
  echo "==> 接受 SDK 许可证 ..."
  yes | "$SDKM" --licenses > /dev/null 2>&1 || true
  echo "==> 安装 platform-tools / platforms;android-34 / build-tools;34.0.0 ..."
  "$SDKM" --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" > /dev/null
else
  echo "==> SDK 组件已存在，跳过安装"
fi

# ---------- 3. Gradle ----------
if [ ! -x "$GRADLE_DIR/gradle-$GRADLE_VER/bin/gradle" ]; then
  echo "==> [3/3] 下载 Gradle $GRADLE_VER ..."
  curl -L --retry 3 --progress-bar -o "$DL_DIR/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VER-bin.zip"
  unzip -q -o "$DL_DIR/gradle.zip" -d "$GRADLE_DIR"
else
  echo "==> [3/3] Gradle 已存在，跳过下载"
fi

# ---------- 生成 env.sh ----------
cat > "$ENV_DIR/env.sh" <<EOF
# TVLock 构建环境（由 setup_env.sh 生成）
export TVLOCK_ROOT="$ROOT"
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
export GRADLE_USER_HOME="$ENV_DIR/gradle-home"
export PATH="$JAVA_HOME/bin:$SDK_DIR/platform-tools:$GRADLE_DIR/gradle-$GRADLE_VER/bin:$PATH"
EOF

echo ""
echo "==> 环境验证："
source "$ENV_DIR/env.sh"
java -version 2>&1 | head -1
"$SDKM" --version | head -1
gradle -v 2>/dev/null | grep -E "Gradle|JVM:" | head -3
adb version | head -1

echo ""
echo "==> 环境搭建完成！接下来："
echo "    1. ./build.sh        # 编译 APK + 跑单元测试"
echo "    2. ./install.sh <电视IP>   # 安装到电视"
