#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION=3.9.9
DIST_ROOT="$BASE_DIR/.mvn/wrapper/dists"
MAVEN_HOME="$DIST_ROOT/apache-maven-$MAVEN_VERSION"
ARCHIVE="$DIST_ROOT/apache-maven-$MAVEN_VERSION-bin.zip"
URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$DIST_ROOT"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "$URL" --output "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget --quiet "$URL" --output-document="$ARCHIVE"
  else
    echo "Maven Wrapper requires curl or wget to download Maven $MAVEN_VERSION." >&2
    exit 1
  fi
  (cd "$DIST_ROOT" && "${JAVA_HOME:+$JAVA_HOME/bin/}jar" xf "$ARCHIVE")
fi

exec "$MAVEN_HOME/bin/mvn" "$@"
