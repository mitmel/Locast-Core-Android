#!/bin/sh

if [ -z "$1" ]; then
    echo "usage $0 API_KEY"
    echo
    echo "where API_KEY is the Google Maps API key"
    exit 1
fi

key="$1"

if [ "$1" = "-c" ]; then
    key=""
fi
    

sed -i -e "s!android:apiKey=\"[^\"]*\"!android:apiKey=\"$key\"!" res/**/*.xml
echo "All android:apiKey set to '$key'"
