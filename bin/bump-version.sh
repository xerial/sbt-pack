#!/bin/sh

newVersion=`perl -npe "s/version in ThisBuild\s+:=\s+\"(.*)\"/\1/" version.sbt | sed -e "/^$/d"`

find src/sbt-test/sbt-pack -name "plugins.sbt" -print0 | while read -d $'\0' f; do
  echo $f;
  perl -i -npe "s/addSbtPlugin\(\"org.xerial.sbt\".*/addSbtPlugin\(\"org.xerial.sbt\" % \"sbt-pack\" % \"$newVersion\"\)/" "$f";
done;
