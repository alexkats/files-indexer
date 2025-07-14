#!/bin/bash

to_run="files-indexer.jar"

if [ -e "target/files-indexer-1.0-SNAPSHOT.jar" ]; then
    to_run="target/files-indexer-1.0-SNAPSHOT.jar"
fi

java -jar "${to_run}" 2> files-indexer.log
