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
  "$APP_SRC"/com/goldsrc/maker/scripting/ScriptSimulator.java \
  "$APP_SRC"/com/goldsrc/maker/editor/Brush.java \
  "$APP_SRC"/com/goldsrc/maker/editor/BrushEntity.java \
  "$APP_SRC"/com/goldsrc/maker/editor/EntityData.java \
  "$APP_SRC"/com/goldsrc/maker/editor/EntityCatalog.java \
  "$APP_SRC"/com/goldsrc/maker/editor/MapData.java \
  "$APP_SRC"/com/goldsrc/maker/render/GeometryBuilder.java \
  "$APP_SRC"/com/goldsrc/maker/core/TextureImage.java \
  "$APP_SRC"/com/goldsrc/maker/core/TextureStore.java \
  "$APP_SRC"/com/goldsrc/maker/core/ModelData.java \
  "$APP_SRC"/com/goldsrc/maker/core/ModelStore.java \
  "$APP_SRC"/com/goldsrc/maker/core/MdlWriter.java \
  "$APP_SRC"/com/goldsrc/maker/core/WadReader.java \
  "$APP_SRC"/com/goldsrc/maker/core/WadWriter.java

java -cp "$OUT" HybridRoundTripTest
java -cp "$OUT" HybridExtraBlocksTest
java -cp "$OUT" ScriptSimulationTest
java -cp "$OUT" MapExportTest
java -cp "$OUT" WadReaderTest
java -cp "$OUT" WadWriterTest
java -cp "$OUT" TextureStoreTest
java -cp "$OUT" ModelStoreTest
java -cp "$OUT" MdlWriterTest
java -cp "$OUT" EntityCatalogTest
java -cp "$OUT" GeometryTest

echo "SELFTEST OK"
