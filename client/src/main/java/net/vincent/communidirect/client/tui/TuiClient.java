package net.vincent.communidirect.client.tui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import net.vincent.communidirect.client.util.MessageParser;
import net.vincent.communidirect.client.util.StagedMessage;
import net.vincent.communidirect.common.config.SettingsManager;
import net.vincent.communidirect.common.crypto.KeyGenerator;
import net.vincent.communidirect.common.crypto.KeyStoreManager;
import net.vincent.communidirect.common.proto.CdirMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full Lanterna TUI for CommuniDirect Client.
 *
 * <h2>Key bindings</h2>
 * <pre>
 *   Ctrl+N  Open external editor to compose a new message
 *   Ctrl+S  Browse staged messages
 *   Ctrl+V  Browse received messages
 *   Ctrl+X  Exit
 *   ↑/↓     Navigate lists
 *   Ctrl+O  Send selected staged message (staged view only)
 *   Enter   Open selected message (received view only)
 *   Esc     Back to log / dismiss overlay
 * </pre>
 */
public class TuiClient {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    private static final String VERSION    = "v1.3.0";
    private static final String ROOT_DIR   = System.getProperty("user.home") + "/.communidirect";
    private static final String STAGED_DIR = ROOT_DIR + "/staged";
    private static final String SENT_DIR   = ROOT_DIR + "/sent";
    private static final String MSG_DIR    = ROOT_DIR + "/msg";

    private static final int SOCKET_TIMEOUT_MS = 5_000;
    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Regex to extract (timestamp, ip) from filenames like {@code 20260221_190501_127.0.0.1.msg}. */
    private static final Pattern MSG_FILENAME_PATTERN =
        Pattern.compile("^(\\d{8}_\\d{6})_(.+)\\.msg$");

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private enum View { LOG, STAGED, RECEIVED }

    private View    currentView   = View.LOG;
    private int     selectedIndex = 0;
    private boolean showOverlay   = false;
    private String  overlayTitle  = "";
    private final List<String> overlayLines = new ArrayList<>();

    private final List<String> logEntries    = new ArrayList<>();
    private final List<Path>   stagedFiles   = new ArrayList<>();
    private final List<Path>   receivedFiles = new ArrayList<>();

    private final AtomicBoolean needsRefresh = new AtomicBoolean(false);

    private final SettingsManager settings;
    private final KeyStoreManager keyStore;
    private final String          localId;

    private Screen screen;
    private ScheduledExecutorService scheduler;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a {@code TuiClient} and derives the compact local identity string
     * from the public key held in {@code keyStore}.
     *
     * @param settings the loaded application settings (IP, port)
     * @param keyStore  the loaded key store providing the local private key and peer keys
     */
    public TuiClient(SettingsManager settings, KeyStoreManager keyStore) {
        this.settings = settings;
        this.keyStore = keyStore;
        this.localId  = deriveLocalId(keyStore.getOwnPublicKeyRaw());
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    /**
     * Starts the interactive TUI, blocking until the user exits with {@code Ctrl+X}
     * or the application is interrupted.
     *
     * <p>This method:
     * <ol>
     *   <li>Creates required data directories.</li>
     *   <li>Performs an initial directory scan.</li>
     *   <li>Initialises the Lanterna {@link Screen}.</li>
     *   <li>Schedules a 60-second auto-refresh of received messages.</li>
     *   <li>Enters the poll-based event loop.</li>
     * </ol>
     *
     * @throws IOException if Lanterna cannot initialise the terminal
     */
    public void run() throws IOException {
        ensureDirectories();
        scanAllDirs();

        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        screen = factory.createScreen();

        try {
            screen.startScreen();
            screen.setCursorPosition(null); // hide cursor

            // Scheduled auto-refresh of the received message directory.
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cdir-refresh");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleAtFixedRate(
                () -> needsRefresh.set(true), 60, 60, TimeUnit.SECONDS);

            log("CommuniDirect " + VERSION + " ready. ID: " + localId);
            drawScreen();

            // Main event loop (poll-based to allow background refresh).
            while (true) {
                if (needsRefresh.compareAndSet(true, false)) {
                    scanAllDirs();
                    log("Auto-refresh: " + receivedFiles.size() + " message(s) on disk.");
                    drawScreen();
                }
                KeyStroke key = screen.pollInput();
                if (key != null) {
                    if (handleInput(key)) break; // exit requested
                    drawScreen();
                } else {
                    try { Thread.sleep(50); } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            if (scheduler != null) scheduler.shutdownNow();
            screen.stopScreen();
        }
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    /**
     * Dispatches a key event to the appropriate action.
     *
     * <p>If an informational overlay is currently displayed, any key dismisses
     * it.  Otherwise the following bindings apply:
     * <ul>
     *   <li>{@code Ctrl+X} – exit</li>
     *   <li>{@code Ctrl+N} – open external editor to compose a new message</li>
     *   <li>{@code Ctrl+T} – switch to STAGED view</li>
     *   <li>{@code Ctrl+V} – switch to RECEIVED view</li>
     *   <li>{@code Ctrl+R} – force-reload all directories</li>
     *   <li>{@code Ctrl+W} – send selected staged message (STAGED view only)</li>
     *   <li>{@code Esc}    – return to LOG view</li>
     *   <li>{@code ↑/↓}     – navigate list selection</li>
     *   <li>{@code Enter}  – open selected received message (RECEIVED view only)</li>
     * </ul>
     *
     * @param key the key stroke received from Lanterna
     * @return {@code true} if the application should exit
     */
    private boolean handleInput(KeyStroke key) {
        // Dismiss overlay on any key.
        if (showOverlay) {
            showOverlay = false;
            return false;
        }

        if (key.getKeyType() == KeyType.Escape) {
            if (currentView != View.LOG) {
                switchView(View.LOG);
            }
            return false;
        }

        if (key.getKeyType() == KeyType.ArrowUp) {
            if (selectedIndex > 0) selectedIndex--;
            return false;
        }
        if (key.getKeyType() == KeyType.ArrowDown) {
            int max = listSize() - 1;
            if (selectedIndex < max) selectedIndex++;
            return false;
        }

        if (key.getKeyType() == KeyType.Enter && currentView == View.RECEIVED) {
            openReceived();
            return false;
        }

        if (key.getKeyType() == KeyType.Character && key.isCtrlDown()) {
            char c = Character.toLowerCase(key.getCharacter());
            switch (c) {
                case 'x': return true;  // exit
                case 'n': openNewMessageEditor(); return false;
                case 't': switchView(View.STAGED);   return false;
                case 'v': switchView(View.RECEIVED); return false;
                case 'r':
                    scanAllDirs();
                    log("Reloaded: " + receivedFiles.size() + " message(s), " +
                        stagedFiles.size() + " staged.");
                    return false;
                case 'w':
                    if (currentView == View.STAGED) sendSelectedStaged();
                    return false;
                default: break;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Drawing
    // -------------------------------------------------------------------------

    /**
     * Clears and redraws the entire screen: header bar, content area, footer bar,
     * and (if active) the informational overlay.
     * Silently logs any {@link IOException} thrown by Lanterna rather than crashing.
     */
    private void drawScreen() {
        try {
            screen.doResizeIfNecessary();
            TerminalSize size = screen.getTerminalSize();
            if (size.getColumns() == 0 || size.getRows() == 0) return;

            screen.clear();
            TextGraphics tg = screen.newTextGraphics();

            drawHeader(tg, size);
            drawContent(tg, size);
            drawFooter(tg, size);

            if (showOverlay) drawOverlay(tg, size);

            screen.refresh();
        } catch (IOException e) {
            log("Draw error: " + e.getMessage());
        }
    }

    /**
     * Draws the top-of-screen header bar showing the application name, version,
     * local identity, and current view name.
     *
     * @param tg   the Lanterna text-graphics context
     * @param size current terminal dimensions
     */
    private void drawHeader(TextGraphics tg, TerminalSize size) {
        tg.setForegroundColor(TextColor.ANSI.BLACK);
        tg.setBackgroundColor(TextColor.ANSI.WHITE);
        String header = " [CommuniDirect " + VERSION + "] | ID: " + localId +
                        "  (" + viewLabel() + ")";
        tg.putString(0, 0, padRight(header, size.getColumns()));
    }

    /**
     * Dispatches to the correct content-area renderer based on the current view.
     *
     * @param tg   the Lanterna text-graphics context
     * @param size current terminal dimensions
     */
    private void drawContent(TextGraphics tg, TerminalSize size) {
        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);

        int contentTop    = 1;
        int contentBottom = size.getRows() - 3; // rows 1..height-3
        int contentHeight = contentBottom - contentTop;
        if (contentHeight <= 0) return;

        switch (currentView) {
            case LOG:      drawLog(tg, size, contentTop, contentHeight);      break;
            case STAGED:   drawList(tg, size, contentTop, contentHeight,
                                   stagedFiles,   "[STAGED]");               break;
            case RECEIVED: drawList(tg, size, contentTop, contentHeight,
                                   receivedFiles, "[RECEIVED]");             break;
        }
    }

    /**
     * Renders the rolling event log in the content area, showing the newest
     * entries at the bottom.
     *
     * @param tg     the Lanterna text-graphics context
     * @param size   current terminal dimensions
     * @param top    first row index available for content
     * @param height number of rows available
     */
    private void drawLog(TextGraphics tg, TerminalSize size,
                         int top, int height) {
        // Show last `height` log entries, newest at the bottom.
        int start = Math.max(0, logEntries.size() - height);
        for (int i = 0; i < height && (start + i) < logEntries.size(); i++) {
            String line = truncate(logEntries.get(start + i), size.getColumns());
            tg.putString(0, top + i, line);
        }
    }

    /**
     * Renders a scrollable, navigable file list (STAGED or RECEIVED view),
     * highlighting the currently selected entry.
     *
     * @param tg       the Lanterna text-graphics context
     * @param size     current terminal dimensions
     * @param top      first row index available for content
     * @param height   number of rows available
     * @param files    the list of file paths to display
     * @param emptyMsg message shown when {@code files} is empty
     */
    private void drawList(TextGraphics tg, TerminalSize size,
                          int top, int height,
                          List<Path> files, String emptyMsg) {
        if (files.isEmpty()) {
            tg.setForegroundColor(TextColor.ANSI.YELLOW);
            tg.putString(2, top + 1, emptyMsg + " – no files.");
            tg.setForegroundColor(TextColor.ANSI.DEFAULT);
            return;
        }
        // Clamp selection.
        if (selectedIndex >= files.size()) selectedIndex = files.size() - 1;

        // Viewport: keep selected item visible.
        int viewStart = Math.max(0, selectedIndex - height + 1);
        for (int i = 0; i < height && (viewStart + i) < files.size(); i++) {
            int   idx      = viewStart + i;
            Path  p        = files.get(idx);
            String label   = formatFileLabel(p);
            String line    = " " + (idx + 1) + ".  " + label;
            int   row      = top + i;
            if (idx == selectedIndex) {
                tg.setForegroundColor(TextColor.ANSI.BLACK);
                tg.setBackgroundColor(TextColor.ANSI.WHITE);
                tg.putString(0, row, padRight(line, size.getColumns()));
                tg.setForegroundColor(TextColor.ANSI.DEFAULT);
                tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
            } else {
                tg.putString(0, row, truncate(line, size.getColumns()));
            }
        }
    }

    /**
     * Draws the two-row footer: a separator bar and the context-sensitive
     * key-binding hint line.
     *
     * @param tg   the Lanterna text-graphics context
     * @param size current terminal dimensions
     */
    private void drawFooter(TextGraphics tg, TerminalSize size) {
        int rows = size.getRows();

        // Separator line
        tg.setForegroundColor(TextColor.ANSI.BLACK);
        tg.setBackgroundColor(TextColor.ANSI.WHITE);
        tg.putString(0, rows - 2, padRight("", size.getColumns()));

        // Key binding bar
        String bindings = currentView == View.STAGED
            ? " ^N New  ^W Send  ^R Reload  ^V Received  ^X Exit  ESC Back"
            : " ^N New  ^T Staged  ^R Reload  ^V Received  ^X Exit";
        tg.putString(0, rows - 1, padRight(bindings, size.getColumns()));

        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    /**
     * Renders a centred, bordered overlay dialog containing {@link #overlayTitle}
     * and {@link #overlayLines}.  The dialog is dismissed on the next key press
     * via {@link #handleInput}.
     *
     * @param tg   the Lanterna text-graphics context
     * @param size current terminal dimensions
     */
    private void drawOverlay(TextGraphics tg, TerminalSize size) {
        int cols = size.getColumns();
        int rows = size.getRows();

        int boxW = Math.min(cols - 4, 74);
        int boxH = Math.min(rows - 4, overlayLines.size() + 4);
        int left = (cols - boxW) / 2;
        int top  = (rows - boxH) / 2;

        tg.setForegroundColor(TextColor.ANSI.WHITE);
        tg.setBackgroundColor(TextColor.ANSI.BLUE);

        // Top border
        tg.putString(left, top, "┌" + "─".repeat(boxW - 2) + "┐");
        // Title row
        String title = " " + overlayTitle + " ";
        String titleBar = "│" + centerPad(title, boxW - 2) + "│";
        tg.putString(left, top + 1, titleBar);
        // Separator
        tg.putString(left, top + 2, "├" + "─".repeat(boxW - 2) + "┤");

        // Content
        int contentRows = boxH - 4;
        for (int i = 0; i < contentRows; i++) {
            String lineContent = (i < overlayLines.size())
                ? truncate(overlayLines.get(i), boxW - 4)
                : "";
            tg.putString(left, top + 3 + i, "│ " + padRight(lineContent, boxW - 4) + " │");
        }

        // Bottom border
        tg.putString(left, top + boxH - 1, "└" + "─".repeat(boxW - 2) + "┘");

        tg.setForegroundColor(TextColor.ANSI.DEFAULT);
        tg.setBackgroundColor(TextColor.ANSI.DEFAULT);
    }

    // -------------------------------------------------------------------------
    // Workflow: New Message (Ctrl+N)
    // -------------------------------------------------------------------------

    /**
     * Creates a pre-filled temp file, suspends Lanterna, launches the user's
     * {@code $EDITOR} (defaulting to {@code vi}), then parses the result via
     * {@link MessageParser#parseTempFile} and saves a valid message to
     * {@link #STAGED_DIR}.  If the editor exits non-zero or the message is
     * incomplete the draft is discarded with a log entry.
     */
    private void openNewMessageEditor() {
        Path tmpFile = null;
        try {
            // 1. Create temp file with template.
            tmpFile = Files.createTempFile("cdir-", ".tmp");
            String template = MessageParser.buildTempFileContent(
                settings.getIp(), settings.getPort());
            Files.writeString(tmpFile, template, StandardCharsets.UTF_8);

            // 2. Stop Lanterna before handing control back to the terminal.
            screen.stopScreen();

            // 3. Launch external editor.
            String editor = System.getenv("EDITOR");
            if (editor == null || editor.isBlank()) editor = "vi";
            int exitCode = new ProcessBuilder(editor, tmpFile.toString())
                .inheritIO()
                .start()
                .waitFor();

            // 4. Restart Lanterna.
            screen.startScreen();
            screen.setCursorPosition(null);

            if (exitCode != 0) {
                log("Editor exited with code " + exitCode + " – message discarded.");
                return;
            }

            // 5. Parse the temp file.
            String raw = Files.readString(tmpFile, StandardCharsets.UTF_8);
            StagedMessage msg = MessageParser.parseTempFile(raw);

            if (!msg.isValid()) {
                log("Incomplete message headers – discarded. (Fill TARGET_IP, KEY_NAME, body)");
                return;
            }

            // 6. Save to staged/.
            String filename = TS_FMT.format(LocalDateTime.now()) + "_" +
                              msg.targetIp.replace(':', '_') + ".toml";
            MessageParser.writeStagedToml(msg, Paths.get(STAGED_DIR), filename);
            scanAllDirs();
            log("Staged: " + filename + " → " + msg.targetIp + " [" + msg.keyName + "]");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("Editor interrupted.");
        } catch (Exception e) {
            log("New-message error: " + e.getMessage());
        } finally {
            if (tmpFile != null) {
                try { Files.deleteIfExists(tmpFile); } catch (IOException ignored) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Workflow: Send Staged (Ctrl+O)
    // -------------------------------------------------------------------------

    /**
     * Reads the currently selected staged TOML file, encodes it as a CDIR frame,
     * opens a TCP connection to the target, transmits the frame, then moves the
     * file from {@link #STAGED_DIR} to {@link #SENT_DIR}.
     *
     * <p>Errors (unknown peer key, connection failure, etc.) are logged to the
     * event log and do not interrupt the TUI.
     */
    private void sendSelectedStaged() {
        if (stagedFiles.isEmpty()) {
            log("No staged messages.");
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= stagedFiles.size()) return;

        Path stagedFile = stagedFiles.get(selectedIndex);
        try {
            StagedMessage msg = MessageParser.readStagedToml(stagedFile);
            if (!msg.isValid()) {
                log("Staged file is incomplete – edit and re-stage: " + stagedFile.getFileName());
                return;
            }

            var recipientPub = keyStore.getPeerKey(msg.keyName);
            if (recipientPub == null) {
                log("Unknown identity: " + msg.keyName);
                return;
            }

            byte[] recipientRaw = KeyGenerator.toRaw32(recipientPub.getEncoded());
            byte[] payload      = msg.body.getBytes(StandardCharsets.UTF_8);

            try (Socket socket = new Socket()) {
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                socket.connect(new InetSocketAddress(msg.targetIp, msg.port), SOCKET_TIMEOUT_MS);
                OutputStream out = socket.getOutputStream();
                CdirMessage.encode(payload,
                                   keyStore.getPrivateKey(),
                                   keyStore.getOwnPublicKeyRaw(),
                                   recipientRaw,
                                   out);
            }

            // Move to sent/.
            Path sentPath = Paths.get(SENT_DIR).resolve(stagedFile.getFileName());
            Files.move(stagedFile, sentPath);
            log("Message securely dispatched to " + msg.targetIp + ".");

            scanAllDirs();

        } catch (Exception e) {
            log("Send failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Workflow: View Received (Enter in RECEIVED view)
    // -------------------------------------------------------------------------

    /**
     * Reads the currently selected received message file and displays its
     * contents in the informational overlay.
     */
    private void openReceived() {
        if (receivedFiles.isEmpty()) return;
        if (selectedIndex < 0 || selectedIndex >= receivedFiles.size()) return;

        Path msgFile = receivedFiles.get(selectedIndex);
        try {
            String content = Files.readString(msgFile, StandardCharsets.UTF_8);
            overlayLines.clear();
            for (String line : content.split("\\r?\\n")) {
                overlayLines.add(line);
            }
            overlayTitle  = msgFile.getFileName().toString();
            showOverlay   = true;
        } catch (IOException e) {
            log("Failed to open message: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Directory scanning
    // -------------------------------------------------------------------------

    /**
     * Refreshes {@link #stagedFiles} and {@link #receivedFiles} by re-scanning
     * their respective directories.  Received files are sorted newest-first by
     * last-modified time.
     */
    private void scanAllDirs() {
        scanDir(Paths.get(STAGED_DIR),   "*.toml", stagedFiles);
        scanDir(Paths.get(MSG_DIR),      "*.msg",  receivedFiles);

        // Sort received by last-modified time, newest first.
        receivedFiles.sort((a, b) -> {
            try {
                FileTime ta = Files.getLastModifiedTime(a);
                FileTime tb = Files.getLastModifiedTime(b);
                return tb.compareTo(ta);
            } catch (IOException e) {
                return b.getFileName().compareTo(a.getFileName());
            }
        });
    }

    /**
     * Clears {@code target} and populates it with all paths in {@code dir}
     * matching {@code glob}, sorted alphabetically by filename.
     *
     * @param dir    directory to scan
     * @param glob   glob pattern (e.g. {@code "*.toml"}) relative to {@code dir}
     * @param target list to populate; cleared before filling
     */
    private void scanDir(Path dir, String glob, List<Path> target) {
        target.clear();
        if (!Files.isDirectory(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            for (Path p : stream) target.add(p);
        } catch (IOException e) {
            log("Scan error (" + dir + "): " + e.getMessage());
        }
        target.sort(Comparator.comparing(Path::getFileName));
    }

    /**
     * Appends a log entry for each received message file whose filename matches
     * {@link #MSG_FILENAME_PATTERN}, extracting the timestamp and peer IP for
     * display.  Called during auto-refresh cycles to surface new arrivals.
     */
    private void scanMsgDirForLog() {
        // Add log entries for any new messages found during an auto-refresh.
        for (Path p : receivedFiles) {
            Matcher m = MSG_FILENAME_PATTERN.matcher(p.getFileName().toString());
            if (m.matches()) {
                String ts = m.group(1);    // 20260221_190501
                String ip = m.group(2);    // 127.0.0.1
                // Format ts as HH:mm:ss for display.
                String displayTs = (ts.length() >= 13)
                    ? ts.substring(9, 11) + ":" + ts.substring(11, 13) + ":" + ts.substring(13)
                    : ts;
                log(displayTs + " - New message from " + ip);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Switches the active view, resetting the list selection to the first item.
     *
     * @param view the view to activate
     */
    private void switchView(View view) {
        currentView   = view;
        selectedIndex = 0;
    }

    /**
     * Returns the number of items in the currently active list view, or
     * {@code 0} in LOG view.
     *
     * @return item count for the selection cursor
     */
    private int listSize() {
        return switch (currentView) {
            case STAGED   -> stagedFiles.size();
            case RECEIVED -> receivedFiles.size();
            default       -> 0;
        };
    }

    /**
     * Returns a human-readable label for the current view, used in the header bar.
     *
     * @return {@code "Log"}, {@code "Staged"}, or {@code "Received"}
     */
    private String viewLabel() {
        return switch (currentView) {
            case LOG      -> "Log";
            case STAGED   -> "Staged";
            case RECEIVED -> "Received";
        };
    }

    /**
     * Appends a timestamped entry to the in-memory log buffer.
     * Keeps the buffer bounded at 500 entries by removing the oldest entry when
     * the limit is exceeded.
     *
     * @param message the text to append (without a timestamp prefix)
     */
    private void log(String message) {
        String ts = DISPLAY_FMT.format(LocalDateTime.now());
        logEntries.add(ts + " - " + message);
        // Keep a rolling buffer to avoid unbounded growth.
        if (logEntries.size() > 500) logEntries.remove(0);
    }

    /**
     * Creates the four data directories ({@code ~/.communidirect/},
     * {@code staged/}, {@code sent/}, {@code msg/}) if they do not already exist.
     * Errors are printed to stderr but do not abort startup.
     */
    private void ensureDirectories() {
        for (String dir : new String[]{ROOT_DIR, STAGED_DIR, SENT_DIR, MSG_DIR}) {
            try {
                Files.createDirectories(Paths.get(dir));
            } catch (IOException e) {
                System.err.println("[TuiClient] Failed to create directory: " + dir);
            }
        }
    }

    /**
     * Returns {@code s} padded with trailing spaces to exactly {@code width}
     * characters, or truncated if it is already longer.
     *
     * @param s     the source string
     * @param width desired output width in terminal columns
     * @return a string of exactly {@code width} characters
     */
    private static String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }

    /**
     * Returns {@code s} centred within {@code width} columns by adding equal
     * leading and trailing spaces.  Truncates if {@code s} exceeds {@code width}.
     *
     * @param s     the source string
     * @param width desired output width in terminal columns
     * @return a centred string of exactly {@code width} characters
     */
    private static String centerPad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        int pad = width - s.length();
        int left = pad / 2;
        return " ".repeat(left) + s + " ".repeat(pad - left);
    }

    /**
     * Returns {@code s} unchanged if it fits within {@code width} columns;
     * otherwise returns the first {@code width-1} characters followed by an
     * ellipsis ({@code …}).
     *
     * @param s     the source string
     * @param width maximum output width in terminal columns
     * @return a string no longer than {@code width} characters
     */
    private static String truncate(String s, int width) {
        return s.length() <= width ? s : s.substring(0, Math.max(0, width - 1)) + "…";
    }

    /**
     * Maps a file's path to a user-friendly display label.
     *
     * <ul>
     *   <li>For {@code *.msg} files whose name matches {@link #MSG_FILENAME_PATTERN}
     *       the label is {@code [YYYYMMDD HH:MM:SS] ip}.</li>
     *   <li>For {@code *.toml} staged files the label is the bare filename
     *       without extension.</li>
     * </ul>
     *
     * @param p the path to format
     * @return a human-readable label string
     */
    private static String formatFileLabel(Path p) {
        String name = p.getFileName().toString();
        Matcher m = MSG_FILENAME_PATTERN.matcher(name);
        if (m.matches()) {
            String ts = m.group(1);
            String ip = m.group(2);
            return "[" + ts.replace('_', ' ').replaceAll("(\\d{2})(\\d{2})(\\d{2})$", "$1:$2:$3") + "] " + ip;
        }
        // For staged .toml files: display as-is without extension.
        return name.replaceAll("\\.toml$", "").replaceAll("\\.msg$", "");
    }

    /** Derives a compact 8-char hex ID from the SHA-256 of the own public key. */
    private static String deriveLocalId(byte[] pubKeyRaw) {
        if (pubKeyRaw == null) return "unknown";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(pubKeyRaw);
            // First 4 bytes = 8 hex chars
            return String.format("%02x%02x%02x%02x",
                hash[0] & 0xff, hash[1] & 0xff, hash[2] & 0xff, hash[3] & 0xff);
        } catch (Exception e) {
            return "n/a";
        }
    }
}
