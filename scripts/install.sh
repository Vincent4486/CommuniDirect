#!/bin/bash

# Configuration
BASE_DIR="$HOME/.communidirect"
BIN_DIR="$BASE_DIR/bin"
LOG_DIR="$BASE_DIR/logs"
VERSION="1.1.0"

echo "🚀 Installing CommuniDirect $VERSION..."

# 1. Prepare Directory Structure
mkdir -p "$BIN_DIR" "$LOG_DIR" "$BASE_DIR/msg" "$BASE_DIR/keys" "$BASE_DIR/staged" "$BASE_DIR/sent"

# 2. Copy JARs
SERVER_JAR="communidirect-server/build/libs/communidirect-server-$VERSION.jar"
CLIENT_JAR="communidirect-client/build/libs/communidirect-client-$VERSION.jar"

if [[ -f "$SERVER_JAR" && -f "$CLIENT_JAR" ]]; then
    cp "$SERVER_JAR" "$BIN_DIR/server.jar"
    cp "$CLIENT_JAR" "$BIN_DIR/client.jar"
else
    echo "❌ Error: JARs not found. Run ./gradlew build first."
    exit 1
fi

# 3. Create Binary Wrappers
echo "📦 Creating binary wrappers in /usr/local/bin..."
for APP in "server" "client"; do
    cat <<EOF | sudo tee /usr/local/bin/cd-$APP > /dev/null
#!/bin/bash
java -Xms10m -Xmx100m -jar "$BIN_DIR/$APP.jar" "\$@"
EOF
    sudo chmod +x /usr/local/bin/cd-$APP
done

# 4. OS-Specific Daemon Setup
OS_TYPE=$(uname -s)

if [[ "$OS_TYPE" == "Darwin" ]]; then
    echo "🍎 macOS detected."
    PLIST="$HOME/Library/LaunchAgents/net.vincent.communidirect.plist"
    cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>net.vincent.communidirect</string>
    <key>ProgramArguments</key>
    <array><string>$(which java)</string><string>-Xms10m</string><string>-Xmx100m</string><string>-jar</string><string>$BIN_DIR/server.jar</string></array>
    <key>RunAtLoad</key><true/><key>KeepAlive</key><true/>
    <key>StandardOutPath</key><string>$LOG_DIR/access.log</string>
    <key>StandardErrorPath</key><string>$LOG_DIR/err.log</string>
    <key>WorkingDirectory</key><string>$BASE_DIR</string>
</dict>
</plist>
EOF
    launchctl load "$PLIST" 2>/dev/null

elif [[ "$OS_TYPE" == "FreeBSD" ]]; then
    echo "😈 FreeBSD detected. Setting up rc.d script..."
    RC_SCRIPT="/usr/local/etc/rc.d/communidirect"
    cat <<EOF | sudo tee "$RC_SCRIPT" > /dev/null
#!/bin/sh
# PROVIDE: communidirect
# REQUIRE: LOGIN
# KEYWORD: shutdown

. /etc/rc.subr

name="communidirect"
rcvar="communidirect_enable"
command="$(which java)"
command_args="-Xms10m -Xmx100m -jar $BIN_DIR/server.jar > /dev/null 2>&1 &"

load_rc_config \$name
run_rc_command "\$1"
EOF
    sudo chmod +x "$RC_SCRIPT"
    echo "✅ To enable on FreeBSD: sudo sysrc communidirect_enable=YES"
    echo "✅ To start: sudo service communidirect start"

elif [[ "$OS_TYPE" == "Linux" ]]; then
    echo "🐧 Linux detected."
    SERVICE_FILE="/etc/systemd/system/communidirect-server.service"
    cat <<EOF | sudo tee "$SERVICE_FILE" > /dev/null
[Unit]
Description=CommuniDirect Server
After=network.target
[Service]
User=$USER
ExecStart=$(which java) -Xms10m -Xmx100m -jar $BIN_DIR/server.jar
Restart=always
WorkingDirectory=$BASE_DIR
[Install]
WantedBy=multi-user.target
EOF
    sudo systemctl daemon-reload
    sudo systemctl enable --now communidirect-server
fi

echo "✨ Installation complete!"