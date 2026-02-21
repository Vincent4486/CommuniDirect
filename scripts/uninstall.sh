#!/bin/bash
echo "🗑️ Uninstalling CommuniDirect..."
OS_TYPE=$(uname -s)

if [[ "$OS_TYPE" == "Darwin" ]]; then
    launchctl unload "$HOME/Library/LaunchAgents/net.vincent.communidirect.plist" 2>/dev/null
    rm -f "$HOME/Library/LaunchAgents/net.vincent.communidirect.plist"
elif [[ "$OS_TYPE" == "FreeBSD" ]]; then
    sudo service communidirect stop 2>/dev/null
    sudo rm -f /usr/local/etc/rc.d/communidirect
elif [[ "$OS_TYPE" == "Linux" ]]; then
    sudo systemctl stop communidirect-server 2>/dev/null
    sudo rm -f /etc/systemd/system/communidirect-server.service
    sudo systemctl daemon-reload
fi

sudo rm -f /usr/local/bin/cd-server /usr/local/bin/cd-client
echo "✅ Binaries and services removed."