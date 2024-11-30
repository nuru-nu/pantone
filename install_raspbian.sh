#!/bin/bash

set -e

if [[ ! -f /etc/os-release ]]; then
    echo "Error: /etc/os-release file not found. Are you running a Linux distribution?"
    exit 1
fi

source /etc/os-release

if [[ "$ID" != "raspbian" ]] || [[ "$VERSION_CODENAME" != "bookworm" ]]; then
    echo "Error: This script must be run on Raspbian Bookworm. Detected:"
    echo "  OS: $NAME"
    echo "  Version: $VERSION"
    exit 1
fi

# libopenblas-dev required for numpy
sudo apt -y install ola libopenblas-dev