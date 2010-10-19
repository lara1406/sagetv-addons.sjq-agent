#!/bin/bash

cd `dirname $0`
CP=
for jar in `ls ../lib/*.jar` ; do
   CP=$jar:$CP
done
java -cp "$CP" com.google.code.sagetvaddons.sjq.agent.Agent
