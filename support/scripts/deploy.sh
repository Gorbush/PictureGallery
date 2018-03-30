mvn3 clean install
ERR=$?
echo "Build complete with error code $ERR"
[[ ! "$ERR" == "0" ]] && exit 1

scp  target/gallery-mine-*.jar  homel:/home/gorbush/opt/galleryMine/lib
ERR=$?
echo "Copy JAR complete with error code $ERR"
[[ ! "$ERR" == "0" ]] && exit 2
scp -r src/main/resources/static  homel:/home/gorbush/opt/galleryMine/static
ERR=$?
echo "Copy STATIC complete with error code $ERR"
[[ ! "$ERR" == "0" ]] && exit 3


ssh homel -C '/home/gorbush/opt/galleryMine/daemon.sh restart'
[[ ! "$ERR" == "0" ]] && exit 10

ssh homel -C '/home/gorbush/opt/galleryMine/daemon.sh tail'