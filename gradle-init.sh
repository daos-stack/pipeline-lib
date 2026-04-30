#!/usr/bin/env bash
#
# Copyright 2026, Hewlett Packard Enterprise Development LP
#
# SPDX-License-Identifier: BSD-2-Clause-Patent
#
set -euo pipefail

DIST_FILE='gradle-9.0-bin.zip'
DIST_URL="https://artifactory.daos.hpc.amslabs.hpecorp.net/artifactory/gradle-services-proxy/distributions/$DIST_FILE"
DIST_DIR='gradle-9.0.0' # it must match the name of the directory stored inside the ZIP

GRADLE_USER_HOME='.gradle'

mkdir -p "$GRADLE_USER_HOME"

DIR="$GRADLE_USER_HOME/$DIST_DIR"
BIN="$DIR/bin/gradle"
if [ -f "$BIN" ]; then
        echo "WARN: '$BIN' is already there."
        echo
        echo "Remove '$DIR' if you want to download it again."
else
        # download and unzip
        cd "$GRADLE_USER_HOME"
        wget "$DIST_URL"
        unzip "$DIST_FILE"
        rm "$DIST_FILE"
        cd -

        if [ ! -f "$BIN" ]; then
                echo "ERROR: There is no '$BIN' file in '$DIST_URL'."
                exit 1
        fi
fi

SYM=gradle
rm -f $SYM
ln -s "$BIN" $SYM
