#!/usr/bin/env bash
ENVIRONMENT=qa
JAVA_HOME=/opt/jdk1.8.0_102
#JMX=" -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=12119 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost "
DEBUG=" -Xdebug -Xrunjdwp:server=y,transport=dt_socket,address=8000,suspend=n "
OPTS=

SCRIPT=$(readlink -f "$0")
HOME_FOLDER=$(dirname "$SCRIPT")
PATH=$JAVA_HOME/bin:$PATH
START_CLASS=gallerymine.GalleryMineApplication

cd $HOME_FOLDER

function start() {
    status
    if [[ "$APP_PID" == "" ]]; then
        echo "Starting..."
        nohup $JAVA_HOME/bin/java $JMX $DEBUG -jar ./lib/* --spring.profiles.active=$ENVIRONMENT $OPTS >/dev/null 2>&1 & export APP_PID=$!
        echo "Run as ($APP_PID)"
        echo "$APP_PID" > $HOME_FOLDER/pid
    else
        echo "Already running ($APP_PID)"
    fi
}

function run() {
    status
    if [[ "$APP_PID" == "" ]]; then
        echo "Starting..."
        rm $HOME_FOLDER/pid
        $JAVA_HOME/bin/java $JMX $DEBUG -jar ./lib/* --spring.profiles.active=$ENVIRONMENT $OPTS
        rm $HOME_FOLDER/pid
    else
        echo "Already running ($APP_PID)"
    fi
}

function tailLog() {
    echo "tail -f $HOME_FOLDER/logs/biIntegration.log"
    tail -f $HOME_FOLDER/logs/biIntegration.log
}

function stop() {
    status
    if [[ ! "$APP_PID" == "" ]]; then
        echo -n "Stopping ($APP_PID)..."
        while kill "$APP_PID"; do
            sleep 1
            echo -n "."
        done
        rm $HOME_FOLDER/pid
    fi
}

function status() {
    RUNNING_APP_PID=$(ps ax | grep "./lib/gallery-mine-" | grep -v grep | awk '{ print $1 }' | head -n 1)

    if [[ -f $HOME_FOLDER/pid ]]; then
        APP_PID=$(cat $HOME_FOLDER/pid)
        if [[ "$APP_PID" == "$RUNNING_APP_PID" ]]; then
            echo "Running ($APP_PID)"
        else
            if [[ "$RUNNING_APP_PID" == "" ]]; then
                echo "Not running but pid file states $APP_PID. Removing pid file."
                rm $HOME_FOLDER/pid
                APP_PID=
            else
                echo "Running ($RUNNING_APP_PID) but pid file states $APP_PID. Fixing pid file."
                echo "$RUNNING_APP_PID" > $HOME_FOLDER/pid
                APP_PID=$RUNNING_APP_PID
            fi
        fi
    else
        if [[ ! "$RUNNING_APP_PID" == "" ]]; then
            echo "Running ($RUNNING_APP_PID), but no pid file found. Fixing pid file"
            echo "$RUNNING_APP_PID" > $HOME_FOLDER/pid
            APP_PID=$RUNNING_APP_PID
        else
            echo "Not running"
        fi
    fi
}

CMD=$1
case "$CMD" in

    status)
        status
        ;;

    stop)
        stop
        ;;

    start)
        start
        ;;

    restart)
        stop
        start
        ;;

    run)
        run
        ;;

    tail)
        tailLog
        ;;

    *)
        echo "Allowed commands: start|run|stop|status "
        echo "In order to run the application standalone in foreground use command:"
        echo "daemon.sh run"
        echo "In order to run the application standalone in background use command:"
        echo "daemon.sh start"
    ;;
esac