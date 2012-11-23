#!/bin/bash

set -e

cd ~/projects/ants/src
make
cd ~/projects/ants/tools
./test_bot.sh "java -jar /Users/daniel/projects/ants/src/MyBot.jar"
