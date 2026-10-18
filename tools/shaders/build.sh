#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

CPP_DIR=../../app/src/main/cpp
SHADER_DIR="$CPP_DIR/shaders"
HEADER="$CPP_DIR/spirv_shaders.h"

if ! command -v glslangValidator >/dev/null 2>&1; then
  echo "нужен glslang: pkg install glslang" >&2
  exit 1
fi

glslangValidator -V "$SHADER_DIR/triangle.vert" -o "$SHADER_DIR/triangle.vert.spv"
glslangValidator -V "$SHADER_DIR/triangle.frag" -o "$SHADER_DIR/triangle.frag.spv"

emit_array() {
  local name="$1"
  local file="$2"
  echo "static const uint32_t $name[] = {"
  od -An -v -tx4 "$file" | tr -s ' \n' '\n' | grep -v '^$' | awk '{printf "    0x%s,\n", $1}'
  echo "};"
  echo
}

{
  echo "// Сгенерировано tools/shaders/build.sh (glslangValidator $SHADER_DIR/triangle.{vert,frag} -> SPIR-V)."
  echo "// Не редактировать вручную: правьте .vert/.frag и запустите tools/shaders/build.sh."
  echo "#ifndef GOLDSRC_SPIRV_SHADERS_H"
  echo "#define GOLDSRC_SPIRV_SHADERS_H"
  echo
  echo "#include <cstdint>"
  echo
  emit_array kTriangleVertSpv "$SHADER_DIR/triangle.vert.spv"
  emit_array kTriangleFragSpv "$SHADER_DIR/triangle.frag.spv"
  echo "#endif  // GOLDSRC_SPIRV_SHADERS_H"
} > "$HEADER"

echo "OK: $HEADER"
