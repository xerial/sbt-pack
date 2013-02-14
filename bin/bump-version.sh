#!/bin/sh

newVersion=`perl -npe "s/version in ThisBuild\s+:=\s+\"(.*)\"/\1/" version.sbt | sed -e "/^$/d"`

for f in $(/bin/ls src/sbt-test/sbt-pack/*/project/plugins.sbt); do \
echo $f; \
perl -npe "s/addSbtPlugin\(\"org.xerial.sbt\".*/addSbtPlugin\(\"org.xerial.sbt\" % \"sbt-pack\" %  \"$newVersion\"\)/" $f; \
done;

