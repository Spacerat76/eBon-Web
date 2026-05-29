#!/usr/bin/env bash
set -euo pipefail

if command -v mvn >/dev/null 2>&1; then
  echo "Maven already available: $(mvn -v)"
  exit 0
fi

if command -v apt-get >/dev/null 2>&1; then
  echo "Attempting apt-get install maven..."
  sudo apt-get update && sudo apt-get install -y maven
  exit 0
fi

if command -v brew >/dev/null 2>&1; then
  echo "Attempting brew install maven..."
  brew install maven
  exit 0
fi

echo "Downloading Maven binary to user home..."
VERSION=3.9.4
FILE=apache-maven-${VERSION}-bin.zip
URL=https://dlcdn.apache.org/maven/maven-3/${VERSION}/binaries/${FILE}
TMPDIR=$(mktemp -d)
ZIPPATH=${TMPDIR}/${FILE}
curl -fSL "$URL" -o "$ZIPPATH"
unzip -q "$ZIPPATH" -d "$HOME"
echo "Add $HOME/apache-maven-${VERSION}/bin to your PATH (e.g. export PATH=\"$HOME/apache-maven-${VERSION}/bin:$PATH\")"
exit 0
