#!/usr/bin/env bash
set -euo pipefail

# 基础路径配置
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )" && pwd)"
BUILD_DIR="$ROOT_DIR/build"

# AetherGate 项目结构配置
PLUGIN_MODULE="$ROOT_DIR/plugin"
PLUGIN_POM="$PLUGIN_MODULE/pom.xml"
RESOURCE_PACK_DIR="$ROOT_DIR/resource_pack" # AetherGate 使用 'resource_pack' 目录

# 清理并初始化构建目录
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# ---------------------------------------------------------
# 1. 提取版本号及构建标识 (分支名 + 短哈希)
# ---------------------------------------------------------

# 提取 Maven 版本号
if [[ -f "$PLUGIN_POM" ]]; then
  # 尝试从 plugin/pom.xml 中提取版本号
  VERSION="$(grep -m 1 -oP '(?<=<version>).*?(?=</version>)' "$PLUGIN_POM" 2>/dev/null || true)"
else
  echo "警告: 未找到 plugin/pom.xml。"
  VERSION=""
fi

if [[ -z "$VERSION" ]]; then
  VERSION="unversioned"
  echo "无法检测到版本号，将使用 'unversioned'。"
fi

# 获取 Git 构建标识
# 使用 sed 's/\//-/g' 将分支名中的 / 替换为 -，防止路径错误
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null | sed 's/\//-/g' || echo "unknown")
GIT_HASH=$(git rev-parse --short HEAD 2>/dev/null || echo "nohash")
BUILD_ID="${GIT_BRANCH}_${GIT_HASH}"

echo "检测到项目版本: $VERSION"
echo "构建标识: $BUILD_ID"

# ---------------------------------------------------------
# 2. 构建插件 (Plugin)
# ---------------------------------------------------------
echo "正在构建插件..."
( cd "$PLUGIN_MODULE" && mvn -q -DskipTests package )

# 根据 Maven 约定构造预期的 Jar 包路径
# 根据提供的 pom.xml，artifactId 为 'aether-gate'
EXPECTED_JAR_NAME="aether-gate-${VERSION}.jar"
UNSHADED_JAR_PATH="$PLUGIN_MODULE/target/original-$EXPECTED_JAR_NAME"

# 优先使用未着色的 jar (original-*) 以保持依赖项不在最终构件中
if [[ ! -f "$UNSHADED_JAR_PATH" ]]; then
  UNSHADED_JAR_PATH=$(find "$PLUGIN_MODULE/target" -maxdepth 1 -name "original-*.jar" | head -n 1 || true)
fi

# 如果失败，则退而求其次使用任何找到的 jar
if [[ -z "$UNSHADED_JAR_PATH" || ! -f "$UNSHADED_JAR_PATH" ]]; then
  UNSHADED_JAR_PATH=$(find "$PLUGIN_MODULE/target" -maxdepth 1 -name "*.jar" ! -name "original-*" | head -n 1 || true)
fi

if [[ -z "$UNSHADED_JAR_PATH" || ! -f "$UNSHADED_JAR_PATH" ]]; then
  echo "错误: 在 $PLUGIN_MODULE/target/ 中未找到插件 Jar 包" >&2
  exit 1
fi

# 定义最终插件文件名：版本号_分支名_短哈希
PLUGIN_FINAL_NAME="AetherGate_Plugin_${VERSION}_${BUILD_ID}.jar"
cp "$UNSHADED_JAR_PATH" "$BUILD_DIR/$PLUGIN_FINAL_NAME"

# ---------------------------------------------------------
# 3. 构建资源包 (Resource Pack)
# ---------------------------------------------------------
# 定义最终资源包文件名：版本号_分支名_短哈希
RESOURCE_PACK_FINAL_NAME="AetherGate_ResourcePack_${VERSION}_${BUILD_ID}.zip"

if [[ -d "$RESOURCE_PACK_DIR" && -f "$RESOURCE_PACK_DIR/pack.mcmeta" ]]; then
    echo "正在构建资源包..."
    rm -f "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME"
    ( cd "$RESOURCE_PACK_DIR" && zip -rq "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME" . )
else
    echo "警告: 未在 $RESOURCE_PACK_DIR 找到资源包目录或 pack.mcmeta。跳过构建。"
fi

# ---------------------------------------------------------
# 4. 验证构建结果
# ---------------------------------------------------------
# 检查 zip 包根目录下是否存在 pack.mcmeta
ensure_packmeta_in_zip() {
  local zipfile="$1"
  if [[ ! -f "$zipfile" ]]; then return 1; fi

  if command -v zipinfo >/dev/null 2>&1; then
    zipinfo -1 "$zipfile" | grep -qx "pack.mcmeta" || return 1
  else
    unzip -l "$zipfile" | awk 'NR>3 {print $4}' | grep -qx "pack.mcmeta" || return 1
  fi
  return 0
}

# 仅在资源包文件生成后进行验证
if [[ -f "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME" ]]; then
    if ! ensure_packmeta_in_zip "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME"; then
      echo "错误: $BUILD_DIR/$RESOURCE_PACK_FINAL_NAME 根目录下不包含 pack.mcmeta" >&2
      exit 1
    fi
fi

# ---------------------------------------------------------
# 5. 构建总结
# ---------------------------------------------------------
echo "------------------------------------------------"
echo "构建完成。输出文件位于 build/ 目录:"
echo " - 插件文件:   $PLUGIN_FINAL_NAME"
if [[ -f "$BUILD_DIR/$RESOURCE_PACK_FINAL_NAME" ]]; then
    echo " - 资源包文件: $RESOURCE_PACK_FINAL_NAME"
fi
echo "------------------------------------------------"