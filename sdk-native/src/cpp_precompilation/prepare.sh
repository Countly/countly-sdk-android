#!/bin/bash
mkdir ./libs
mkdir ./obj
mkdir ../../libs  # sdk-native/libs
# git submodule init 
# git submodule update
# missing from the commit we are using. if we build latest breakpad, might be removed
cp -r ./lss ./breakpad/src/third_party
echo "Preparation done. You can now execute build.sh."
