#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

APP_SRC=../../app/src/main/java
SELFTEST=../selftest
OUT=build

rm -rf "$OUT"
mkdir -p "$OUT"

javac -encoding UTF-8 -d "$OUT" \
  "$SELFTEST"/org/json/*.java \
  GenWar.java \
  "$APP_SRC"/com/goldsrc/maker/scripting/BlockType.java \
  "$APP_SRC"/com/goldsrc/maker/scripting/BlockNode.java \
  "$APP_SRC"/com/goldsrc/maker/scripting/BlockGraph.java \
  "$APP_SRC"/com/goldsrc/maker/scripting/CodeGenerator.java \
  "$APP_SRC"/com/goldsrc/maker/editor/Brush.java \
  "$APP_SRC"/com/goldsrc/maker/editor/EntityData.java \
  "$APP_SRC"/com/goldsrc/maker/editor/MapData.java \
  "$APP_SRC"/com/goldsrc/maker/core/TextureImage.java \
  "$APP_SRC"/com/goldsrc/maker/core/TextureStore.java \
  "$APP_SRC"/com/goldsrc/maker/core/ModelData.java \
  "$APP_SRC"/com/goldsrc/maker/core/ModelStore.java \
  "$APP_SRC"/com/goldsrc/maker/core/WadReader.java \
  "$APP_SRC"/com/goldsrc/maker/core/WadWriter.java

java -cp "$OUT" GenWar "${1:-/storage/emulated/0/Documents/GoldSrcMaker/projects/warzone}"
