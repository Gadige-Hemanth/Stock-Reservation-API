#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper Script for Unix / macOS
# ----------------------------------------------------------------------------

MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.9"
MVN_CMD="$MAVEN_HOME/bin/mvn"

if [ ! -x "$MVN_CMD" ]; then
    echo "Downloading Maven 3.9.9..."
    mkdir -p "$HOME/.m2/wrapper/dists"
    TMP_ZIP="/tmp/apache-maven-3.9.9-bin.zip"
    curl -sSLo "$TMP_ZIP" "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
    unzip -qo "$TMP_ZIP" -d "$HOME/.m2/wrapper/dists"
    rm -f "$TMP_ZIP"
    chmod +x "$MVN_CMD"
    echo "Maven installed successfully."
fi

exec "$MVN_CMD" "$@"

