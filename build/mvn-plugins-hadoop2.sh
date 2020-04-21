#!/usr/bin/env bash
echo 'Dependency Hadoop-2.7.3 Building...'

hadoopversion=$1
if [ ! -n "$hadoopversion" ]
then
    hadoopversion=2.7.3
fi
echo "Dependency ${hadoopversion} Building..."

mvn clean package -DskipTests -Dhadoop.version=${hadoopversion} -Dhivejdbc.version=1.1.1 -pl \
engine-plugins/dummy,\
engine-plugins/flink/flink180-hadoop2,\
engine-plugins/flink/flink180-HW,\
engine-plugins/spark/spark-yarn-hadoop2,\
engine-plugins/dtscript/dtscript-hadoop2/dtscript-client,\
engine-plugins/learning/learning-hadoop2/learning-client,\
engine-plugins/hadoop/hadoop2,\
engine-plugins/kylin,\
engine-plugins/rdbs,\
engine-plugins/odps,\
engine-entrance \
-am -amd