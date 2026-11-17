#!/bin/bash
# Check JDK packages or explicit files (paths relative to the module source root).
# Usage: JDK_SRC=/path/to/jdk checker/bin-devel/typecheck-jdk.sh CHECKER MODULE PACKAGE_OR_FILE...
# JAVA_HOME must identify a compiler compatible with this JDK's sources (JDK 17).
# JDK_GENERATE_CHARACTER_DATA=1 generates java.base's six Unicode classes from
# the selected JDK's templates/data, using its Java build tool (no native build).
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 CHECKER MODULE PACKAGE_OR_FILE..." >&2
  exit 2
fi
CHECKERFRAMEWORK=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
JDK_SRC=$(cd -- "${JDK_SRC:-$CHECKERFRAMEWORK/../jdk}" && pwd)
checker=$1
module=$2
shift 2
module_root="$JDK_SRC/src/$module/share/classes"
# Extra module source roots let CF-owned callers exercise package-private JDK APIs.
# Ordinary JDK source runs leave this unset and retain their original patch context.
patch_roots=("$module_root")
if [[ -n ${JDK_EXTRA_SOURCE_ROOTS:-} ]]; then
  IFS=: read -r -a extra_roots <<< "$JDK_EXTRA_SOURCE_ROOTS"
  for root in "${extra_roots[@]}"; do
    [[ -d "$root" ]] || { echo "No extra source root: $root" >&2; exit 2; }
    patch_roots+=("$(cd -- "$root" && pwd)")
  done
fi
javac="$CHECKERFRAMEWORK/checker/bin/javac"
qualifiers="$CHECKERFRAMEWORK/checker/dist/checker-qual.jar"
[[ -x "$javac" && -f "$qualifiers" && -d "$module_root" ]] || {
  echo "Build Checker Framework and select a valid JDK module first." >&2
  exit 2
}
if [[ -n ${JAVA_HOME:-} ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi
mkdir -p "$CHECKERFRAMEWORK/checker/build/jdk-typecheck"
output=$(mktemp -d "$CHECKERFRAMEWORK/checker/build/jdk-typecheck/run.XXXXXX")
echo "Type-checking logs: $output"
{
  git -C "$JDK_SRC" rev-parse HEAD
  git -C "$CHECKERFRAMEWORK" rev-parse HEAD
  java -version
} >"$output/toolchain.log" 2>&1

case ${JDK_GENERATE_CHARACTER_DATA:-0} in
  0) ;;
  1)
    [[ "$module" == java.base && -n ${JAVA_HOME:-} ]] || {
      echo "CharacterData generation requires java.base and JAVA_HOME." >&2
      exit 2
    }
    generation="$output/character-data"
    generated_root="$generation/gensrc/java.base"
    tool_classes="$generation/tool-classes"
    mkdir -p "$generated_root/java/lang" "$tool_classes"
    names=(CharacterDataLatin1 CharacterData00 CharacterData01
      CharacterData02 CharacterData03 CharacterData0E)
    # Do not silently select an old generated copy ahead of the fresh output.
    for root in "${patch_roots[@]}"; do
      for name in "${names[@]}"; do
        [[ ! -f "$root/java/lang/$name.java" ]] || {
          echo "CharacterData source would shadow generation: $root/java/lang/$name.java" >&2
          exit 2
        }
      done
    done
    tool_sources=("$JDK_SRC"/make/jdk/src/classes/build/tools/generatecharacter/*.java)
    data="$JDK_SRC/make/data/unicodedata"
    templates="$JDK_SRC/make/data/characterdata"
    inputs=("${tool_sources[@]}" "$data/UnicodeData.txt" "$data/SpecialCasing.txt"
      "$data/PropList.txt" "$data/DerivedCoreProperties.txt"
      "$JDK_SRC/make/modules/java.base/gensrc/GensrcCharacterData.gmk"
      "$JDK_SRC/make/ToolsJdk.gmk")
    for name in "${names[@]}"; do inputs+=("$templates/$name.java.template"); done
    hash_command=(sha256sum)
    command -v sha256sum >/dev/null || hash_command=(shasum -a 256)
    "${hash_command[@]}" "${inputs[@]}" >"$generation/inputs.sha256"

    generate_step() {
      local label=$1 rc=0
      shift
      printf '%q ' "$@" >"$generation/$label.command.txt"
      printf '\n' >>"$generation/$label.command.txt"
      "$@" >"$generation/$label.log" 2>&1 || rc=$?
      printf '%s\n' "$rc" >"$generation/$label.status"
      cat "$generation/$label.log"
      return "$rc"
    }
    generate_step compile-tools "$JAVA_HOME/bin/javac" -encoding UTF-8 \
      -d "$tool_classes" "${tool_sources[@]}"
    # Match make/modules/java.base/gensrc/GensrcCharacterData.gmk.
    for name in "${names[@]}"; do
      case "$name" in
        CharacterDataLatin1) generation_options=(-latin1 8) ;;
        *)
          case "$name" in
            CharacterData00) plane=0 ;; CharacterData01) plane=1 ;;
            CharacterData02) plane=2 ;; CharacterData03) plane=3 ;;
            CharacterData0E) plane=14 ;;
          esac
          generation_options=(-string -plane "$plane" 11 4 1)
          ;;
      esac
      generate_step "$name" "$JAVA_HOME/bin/java" -cp "$tool_classes" \
        build.tools.generatecharacter.GenerateCharacter \
        -template "$templates/$name.java.template" -spec "$data/UnicodeData.txt" \
        -specialcasing "$data/SpecialCasing.txt" -proplist "$data/PropList.txt" \
        -derivedprops "$data/DerivedCoreProperties.txt" \
        -o "$generated_root/java/lang/$name.java" -usecharforbyte "${generation_options[@]}"
      [[ -s "$generated_root/java/lang/$name.java" ]] || {
        echo "Missing generated source: $name" >&2
        exit 1
      }
    done
    "${hash_command[@]}" "${inputs[@]}" >"$generation/inputs-after.sha256"
    cmp -s "$generation/inputs.sha256" "$generation/inputs-after.sha256" || {
      echo "CharacterData inputs changed during generation." >&2
      exit 1
    }
    "${hash_command[@]}" "$generated_root"/java/lang/*.java >"$generation/outputs.sha256"
    patch_roots+=("$generated_root")
    ;;
  *) echo "JDK_GENERATE_CHARACTER_DATA must be 0 or 1." >&2; exit 2 ;;
esac
module_patch=$(IFS=:; printf '%s' "${patch_roots[*]}")

options=(-processor "$checker" -proc:only -implicit:none -Afilenames
  -AprintVerboseGenerics -Xmaxerrs 5000 -Xmaxwarns 5000 -Werror
  --add-reads "$module=ALL-UNNAMED" -classpath "$qualifiers")
# java.base deliberately references internal JDK APIs.
if [[ "$module" == java.base ]]; then
  options+=(-XDignore.symbol.file=true)
fi
if [[ "$checker" == nullness ]]; then
  options+=(-Amode=jspecify -AwarnUnneededSuppressions)
fi

check_file() {
  local source=$1 name run status
  name=$(basename "$source" .java)
  run="$output/$package/$name"
  mkdir -p "$run/classes" "$run/generated"
  # This catches a missing opt-in; complete declaration scope still needs review.
  if [[ "$checker" == nullness ]] && ! tr '\n' ' ' <"$source" |
    grep -E '@AnnotatedFor\([^)]*"nullness"' >/dev/null; then
    echo "Missing nullness opt-in: $source" >&2
    return 1
  fi
  local command=("$javac" "${options[@]}" --patch-module "$module=$module_patch"
    -d "$run/classes" -s "$run/generated" "$source")
  printf '%q ' "${command[@]}" >"$run/command.txt"
  printf '\n' >>"$run/command.txt"
  status=0
  "${command[@]}" >"$run/compile.log" 2>&1 || status=$?
  printf '%s\n' "$status" >"$run/exit-status.txt"
  cat "$run/compile.log"
  if ! grep -Fq "is type-checking $source" "$run/compile.log"; then
    echo "Checker did not visit: $source" >&2
    return 1
  fi
  if grep -Eq 'crashed|Exception in thread|An exception has occurred in the compiler|only showing the first|warning:' "$run/compile.log"; then
    return 1
  fi
  return "$status"
}

for target in "$@"; do
  if [[ "$target" == *.java ]]; then
    source=""
    for root in "${patch_roots[@]}"; do
      if [[ -f "$root/$target" ]]; then
        source="$root/$target"
        break
      fi
    done
    [[ -n "$source" ]] || {
      echo "No source file: $target" >&2
      exit 2
    }
    package=${target%/*}
    package=${package//\//.}
    printf '%s\n' "$source" >>"$output/selected.sources"
    check_file "$source"
  else
    package=$target
    package_path=${package//.//}
    package_root="$module_root/$package_path"
    [[ -d "$package_root" ]] || {
      echo "No package: $package" >&2
      exit 2
    }
    list="$output/$package.sources"
    find "$package_root" -maxdepth 1 -name '*.java' ! -name package-info.java | sort >"$list"
    [[ -s "$list" ]] || {
      echo "No source files: $package" >&2
      exit 2
    }
    while IFS= read -r source; do
      check_file "$source"
    done <"$list"
  fi
done
