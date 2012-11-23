#!/bin/bash

set -e

cd ~/projects/ants/src
make
cd ~/projects/ants/tools
#./play_one_game.sh -So -E | java -jar visualizer.jar
./play_one_game.sh -E
