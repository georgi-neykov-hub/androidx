#!/bin/bash
set -e

cd "$(dirname $0)"

impl/build.sh --no-daemon buildTestApks \
    -Pandroidx.enableAffectedModuleDetection \
    -Pandroidx.changedProjects \
    -Pandroidx.allWarningsAsErrors --offline "$@"
