#!/bin/sh

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Maven Wrapper JAR is missing: $WRAPPER_JAR" >&2
  echo "Download it from the wrapperUrl declared in .mvn/wrapper/maven-wrapper.properties." >&2
  exit 1
fi

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXE="$JAVA_HOME/bin/java"
else
  JAVA_EXE=java
fi

exec "$JAVA_EXE" \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
