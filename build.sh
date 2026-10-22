#!/usr/bin/env bash
# Erzeugt aus dem Original-LootChest-JAR eine Version, die auch auf neuen Minecraft-/Paper-Versionen laeuft.
#
#   ./build.sh                       nimmt das (einzige) JAR in input/
#   ./build.sh pfad/zu/Lootchest.jar
#
# Ergebnis: dist/<name>-compat.jar
set -euo pipefail
cd "$(dirname "$0")"

ASM_VERSION=9.9
MAVEN=https://repo.maven.apache.org/maven2/org/ow2/asm
CACHE=.cache
OUT=build

if [ $# -ge 1 ]; then
  INPUT=$1
else
  shopt -s nullglob
  jars=(input/*.jar)
  [ ${#jars[@]} -eq 1 ] || { echo "Genau ein JAR in input/ ablegen oder den Pfad als Argument angeben." >&2; exit 1; }
  INPUT=${jars[0]}
fi

mkdir -p "$CACHE" "$OUT" dist
for artifact in asm asm-tree; do
  jar="$CACHE/$artifact-$ASM_VERSION.jar"
  if [ ! -s "$jar" ]; then
    for attempt in 1 2 3 4 5; do
      code=$(curl -sS -m 60 -o "$jar" -w '%{http_code}' "$MAVEN/$artifact/$ASM_VERSION/$artifact-$ASM_VERSION.jar" || true)
      [ "$code" = 200 ] && break
      rm -f "$jar"; sleep $((attempt * 5))
    done
    [ -s "$jar" ] || { echo "Download von $artifact fehlgeschlagen" >&2; exit 1; }
  fi
done
ASM_CP="$CACHE/asm-$ASM_VERSION.jar:$CACHE/asm-tree-$ASM_VERSION.jar"

rm -rf "$OUT/helpers" "$OUT/patcher"
mkdir -p "$OUT/helpers" "$OUT/patcher"
# Original-Klassen sind Java 8 (Klassenversion 52), Helfer muessen dazu passen.
javac -nowarn --release 8 -cp "$INPUT" -d "$OUT/helpers" $(find src/helpers -name '*.java')
javac -nowarn -cp "$ASM_CP" -d "$OUT/patcher" src/patcher/Patcher.java

name=$(basename "$INPUT" .jar)
RESULT="dist/$name-compat.jar"
java -cp "$OUT/patcher:$ASM_CP" Patcher "$INPUT" "$OUT/helpers" "$RESULT"
echo "Fertig: $RESULT"
