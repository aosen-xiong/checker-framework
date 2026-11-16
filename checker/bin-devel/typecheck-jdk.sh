#!/bin/bash
# Check JDK packages or explicit files (paths relative to the module source root).
# Usage: JDK_SRC=/path/to/jdk checker/bin-devel/typecheck-jdk.sh CHECKER MODULE PACKAGE_OR_FILE...
# JAVA_HOME must identify a compiler compatible with this JDK's sources (JDK 17).
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
  local command=("$javac" "${options[@]}" --patch-module "$module=$module_root"
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
    source="$module_root/$target"
    [[ -f "$source" ]] || {
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
