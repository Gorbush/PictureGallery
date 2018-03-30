#!/usr/bin/env bash
if [[ "$1" == "" ]]; then
    echo "Please specify the name of dump"
    exit 1
fi
echo "Dump file name: $1"
if [[ -f /usr/home/gorbush/opt/galleryMine/dump/$1 ]]; then
    mongodump -d galleryMine -o /usr/home/gorbush/opt/galleryMine/dump/$1
fi

zip -r /usr/home/gorbush/opt/galleryMine/dump/$1.zip /usr/home/gorbush/opt/galleryMine/dump/$1 /usr/home/gorbush/opt/galleryMine/thumbs