#!/bin/sh

dev="0mv3f-QVdQ_CF3MFrN2I0MO8Wgu_QPqzOKx2GHw"

# for fingerint
# 1E:4B:71:20:73:45:0F:D4:77:A6:B8:18:0C:42:D7:F3
prod="0mv3f-QVdQ_DwBwdfkqD1df8s37Ezhu5qfmJR_A"

if [ "$1" = 'prod' ]; then
    key="$prod"
elif [ "$1" = 'dev' ]; then
    key="$dev"
else
    key="$1"
fi

sed -i -e "s!android:apiKey=\"[^\"]*\"!android:apiKey=\"$key\"!" res/**/*.xml
