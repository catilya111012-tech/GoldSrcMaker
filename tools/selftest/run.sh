#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

APP_SRC=../../app/src/main/java
OUT=build

rm -rf "$OUT"
mkdir -p "$OUT"

javac -encoding UTF-8 -d "$OUT" \
  org/json/*.java \
  tests/*.java \
  "$APP_SRC"/com/goldsrc/maker/scripting/BlockType.java \
  "$APP_SRC"/com/goldsrc/maker/scripting/BlockNode.java \
  "$APP_SRC"/com/goldsrc/maker/scripting/BlockGraph.java \
  "$APP_SRC"/com/goldsrc/maker/scripting/CodeGenerator.java \
  "$APP_SRC"/com/goldsrc/maker/editor/Brush.java \
  "$APP_SRC"/com/goldsrc/maker/editor/EntityData.java \
  "$APP_SRC"/com/goldsrc/maker/editor/MapData.java \
  "$APP_SRC"/com/goldsrc/maker/core/WadReader.java

java -cp "$OUT" HybridRoundTripTest
java -cp "$OUT" MapExportTest
java -cp "$OUT" WadReaderTest

echo "SELFTEST OK"
