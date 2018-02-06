#!/bin/sh
source /etc/profile

SH_DIR=$(cd "$(dirname "$0")"; pwd)
WORK_DIR=${SH_DIR}/..
umask 037


MAIN_CLASS=uyun.indian.tester.IndianTestingApp
CLASSPATH=${JAVA_HOME}/lib/*:${BASE_DIR}/lib/*:${WORK_DIR}/custom/*:${WORK_DIR}/custom/:${WORK_DIR}/patch/*:${WORK_DIR}/patch/:${WORK_DIR}/lib/*

RUN_OPTS="-Dapp.name=platform-store-metric-benchmarks"
RUN_OPTS="$RUN_OPTS -Duser.dir=$WORK_DIR"
RUN_OPTS="$RUN_OPTS -Dlogs.dir=$WORK_DIR/logs"
RUN_OPTS="$RUN_OPTS -Dlogging.config=$WORK_DIR/config/logback.xml"


CMD="java ${RUN_OPTS} ${JAVA_OPTS} -cp ${CLASSPATH} ${MAIN_CLASS}"

cd ${WORK_DIR}
if [ "$1" = "--daemon" ]
then
        nohup $CMD > /dev/null 2>&1 &
else
        $CMD
fi
