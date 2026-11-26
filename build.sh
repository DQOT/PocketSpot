#!/usr/bin/env bash
# Builds PocketSpot.apk with the plain Android SDK tools (no Gradle).
#
#   ./build.sh            -> build/PocketSpot.apk
#   ./build.sh install    -> build, then `adb install -r`
#
# Needs a JDK (17+) and an Android SDK with platforms/android-36 and any recent build-tools.
# Tool locations, in this order:
#   JAVA_HOME                      or ../android-build-tools/jdk-*
#   ANDROID_HOME / ANDROID_SDK_ROOT or ../android-build-tools/sdk
#   adb from ADB, from PATH, or from the SDK's platform-tools
#
# libs/ holds the Shizuku client libraries (MIT, dev.rikka.shizuku 13.1.5 from Maven Central,
# classes.jar of each AAR); they are dexed into the app. See libs/README.md.
#
# Signing: the first build creates keys/release.jks (kept out of git). Set KEYSTORE_PASSWORD to
# choose its password; keep the keystore safe, updates must be signed with the same key.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
LOCAL_TOOLS="$HERE/../android-build-tools"

JDK="${JAVA_HOME:-$(ls -d "$LOCAL_TOOLS"/jdk-* 2>/dev/null | head -1 || true)}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$LOCAL_TOOLS/sdk}}"
[ -n "$JDK" ] && [ -d "$JDK" ] || { echo "JDK not found: set JAVA_HOME" >&2; exit 1; }
[ -d "$SDK" ] || { echo "Android SDK not found: set ANDROID_HOME" >&2; exit 1; }

ANDROID_JAR="$SDK/platforms/android-36/android.jar"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
[ -f "$ANDROID_JAR" ] || { echo "Missing $ANDROID_JAR (install platforms;android-36)" >&2; exit 1; }

exe() { if [ -f "$1.exe" ]; then echo "$1.exe"; else echo "$1"; fi; }
AAPT2="$(exe "$BT/aapt2")"
ZIPALIGN="$(exe "$BT/zipalign")"
JAVA="$(exe "$JDK/bin/java")"
JAVAC="$(exe "$JDK/bin/javac")"
JAR="$(exe "$JDK/bin/jar")"
KEYTOOL="$(exe "$JDK/bin/keytool")"

# On Windows (Git Bash) the JDK wants Windows paths and ';' separators.
winpath() { if command -v cygpath >/dev/null; then cygpath -m -f -; else cat; fi; }
if command -v cygpath >/dev/null; then SEP=";"; else SEP=":"; fi

OUT="$HERE/build"
KEYSTORE="$HERE/keys/release.jks"
PASS="${KEYSTORE_PASSWORD:-pocketspot}"
LIBS=("$HERE"/libs/shizuku-*.jar)

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen" "$HERE/keys"

echo "== resources"
"$AAPT2" compile --dir "$HERE/res" -o "$OUT/res.zip"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
    --manifest "$HERE/AndroidManifest.xml" \
    --min-sdk-version 30 --target-sdk-version 36 \
    --java "$OUT/gen" \
    "$OUT/res.zip"

echo "== java"
CP="$(printf '%s\n' "$ANDROID_JAR" "${LIBS[@]}" | winpath | paste -sd "$SEP")"
{ find "$HERE/src" -name '*.java'; find "$OUT/gen" -name '*.java'; } | winpath > "$OUT/sources.txt"
"$JAVAC" --release 17 -encoding UTF-8 -Xlint:-options -nowarn \
    -classpath "$CP" -d "$OUT/classes" @"$OUT/sources.txt"

echo "== dex"
{ find "$OUT/classes" -name '*.class'; printf '%s\n' "${LIBS[@]}"; } | winpath > "$OUT/classes.txt"
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 30 \
    --lib "$ANDROID_JAR" --output "$OUT/dex" @"$OUT/classes.txt"

echo "== package"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && "$JAR" uf "$OUT/unsigned.apk" classes*.dex)
"$ZIPALIGN" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    echo "== new signing key at keys/release.jks"
    "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -alias release \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$PASS" -keypass "$PASS" -dname "CN=PocketSpot" >/dev/null
fi
"$JAVA" -jar "$BT/lib/apksigner.jar" sign --ks "$KEYSTORE" --ks-pass "pass:$PASS" \
    --out "$OUT/PocketSpot.apk" "$OUT/aligned.apk"
echo "built build/PocketSpot.apk"

if [ "${1:-}" = "install" ]; then
    ADB_BIN="${ADB:-$(command -v adb || echo "$(exe "$SDK/platform-tools/adb")")}"
    "$ADB_BIN" install -r "$OUT/PocketSpot.apk"
fi
