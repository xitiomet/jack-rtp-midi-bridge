#!/bin/bash
mvn package
java -jar target/jack-apple-midi-bridge-1.0-SNAPSHOT.jar
