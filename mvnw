#!/bin/sh
# Maven Wrapper Script
# This allows running Maven without having it installed system-wide

set -e

# Maven Wrapper properties
MAVEN_VERSION=3.9.6
BASE_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven
MAVEN_DOWNLOAD_URL="${BASE_URL}/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip"

# Determine the Java command
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Maven Wrapper directory
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$(pwd)}"
WRAPPER_DIR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
JAR_PATH="$WRAPPER_DIR/maven-wrapper.jar"

# Download Maven Wrapper if needed
if [ ! -f "$JAR_PATH" ]; then
    echo "Maven Wrapper not found. Please run: ./mvnw wrapper:wrapper"
    exit 1
fi

# Execute Maven
exec "$JAVACMD" \
  -classpath "$JAR_PATH" \
  "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
