#!/bin/bash
mvn clean
mvn package
cat src/stub.sh target/jack-apple-midi-bridge-1.0-SNAPSHOT.jar > jamb
chmod +x jamb
