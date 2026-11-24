#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

set -e

export LANG="${LANG:-C.UTF-8}"
export LC_ALL="${LC_ALL:-C.UTF-8}"

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
export MAVEN_PROJECTBASEDIR="$BASE_DIR"

MAVEN_VERSION="3.9.12"
MAVEN_HOME_DIR="$HOME/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}/6068d197"

if [ ! -x "$MAVEN_HOME_DIR/bin/mvn" ]; then
  DIST_DIR="$HOME/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}/default"
  if [ ! -x "$DIST_DIR/bin/mvn" ]; then
    mkdir -p "$DIST_DIR"
    ZIP_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip"
    curl -fsSL "$ZIP_URL" -o "$DIST_DIR/maven.zip"
    unzip -q "$DIST_DIR/maven.zip" -d "$DIST_DIR"
    mv "$DIST_DIR/apache-maven-${MAVEN_VERSION}"/* "$DIST_DIR/"
    rm -rf "$DIST_DIR/apache-maven-${MAVEN_VERSION}" "$DIST_DIR/maven.zip"
  fi
  MAVEN_HOME_DIR="$DIST_DIR"
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="$(command -v java)"
fi

CLASSWORLDS_JAR="$(echo "$MAVEN_HOME_DIR"/boot/plexus-classworlds-*.jar)"

if "$JAVACMD" --enable-native-access=ALL-UNNAMED -version >/dev/null 2>&1; then
  MAVEN_OPTS="--enable-native-access=ALL-UNNAMED $MAVEN_OPTS"
fi

exec "$JAVACMD" \
  -Dfile.encoding=UTF-8 \
  -Dsun.jnu.encoding=UTF-8 \
  $MAVEN_OPTS \
  -classpath "$CLASSWORLDS_JAR" \
  "-Dclassworlds.conf=${MAVEN_HOME_DIR}/bin/m2.conf" \
  "-Dmaven.home=${MAVEN_HOME_DIR}" \
  "-Dlibrary.jansi.path=${MAVEN_HOME_DIR}/lib/jansi-native" \
  "-Dmaven.multiModuleProjectDirectory=${MAVEN_PROJECTBASEDIR}" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
