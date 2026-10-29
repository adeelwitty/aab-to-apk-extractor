import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

/** Small Swing GUI around bundletool: AAB + keystore in, universal APK out. */
public class ApkExtractorApp extends JFrame {
    private static final Preferences PREFS = Preferences.userNodeForPackage(ApkExtractorApp.class);

    private final JTextField aabField = new JTextField();
    private final JTextField ksField = new JTextField();
    private final JPasswordField ksPass = new JPasswordField();
    private final JComboBox<String> aliasBox = new JComboBox<>();
    private final JPasswordField keyPass = new JPasswordField();
    private final JButton loadAliasesBtn = new JButton("Load aliases");
    private final JButton convertBtn = new JButton("Convert to APK");
    private final JButton showBtn = new JButton("Show in folder");
    private final JProgressBar progress = new JProgressBar();
    private final JTextArea log = new JTextArea(8, 40);
    private final JLabel status = new JLabel(" ");

    private final JToggleButton themeBtn = new JToggleButton();
    private File lastApk;
    private String lastStatus = " ";
    private boolean lastStatusError;

    private static void applyTheme(boolean dark) {
        try {
            if (dark) FlatDarkLaf.setup(); else FlatLightLaf.setup();
        } catch (Exception ignored) { }
    }

    private static boolean isDark() { return FlatLaf.isLafDark(); }

    /** Window/taskbar/Dock icon (PNG sizes are packed into the jar). */
    private void applyIcon() {
        try {
            List<Image> icons = new ArrayList<>();
            for (int z : new int[]{16, 32, 48, 64, 128, 256, 512}) {
                java.net.URL u = ApkExtractorApp.class.getResource("/icon-" + z + ".png");
                if (u != null) icons.add(javax.imageio.ImageIO.read(u));
            }
            if (icons.isEmpty()) return;
            setIconImages(icons);
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(icons.get(icons.size() - 1));
            }
        } catch (Exception | Error ignored) { }
    }

    private void toggleTheme() {
        boolean dark = !isDark();
        applyTheme(dark);
        try { PREFS.putBoolean("dark", dark); } catch (RuntimeException ignored) { }
        FlatLaf.updateUI();
        updateThemeButton();
        setStatus(lastStatus, lastStatusError);
    }

    private void updateThemeButton() {
        themeBtn.setSelected(isDark());
        themeBtn.setText(isDark() ? "\u2600  Light mode" : "\u263E  Dark mode");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            applyTheme(PREFS.getBoolean("dark", true));
            new ApkExtractorApp().setVisible(true);
        });
    }

    private ApkExtractorApp() {
        super("AAB to APK Extractor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        applyIcon();

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        JButton aabBrowse = new JButton("Browse...");
        JButton ksBrowse = new JButton("Browse...");
        row(form, c, 0, "1. App Bundle (.aab)", aabField, aabBrowse);
        row(form, c, 1, "2. Keystore", ksField, ksBrowse);
        row(form, c, 2, "3. Keystore password", ksPass, loadAliasesBtn);
        row(form, c, 3, "4. Key alias", aliasBox, null);
        row(form, c, 4, "Key password (optional)", keyPass, null);

        aabField.putClientProperty("JTextField.placeholderText", "Drop an .aab file here, or click Browse");
        ksField.putClientProperty("JTextField.placeholderText", "Drop a keystore file here, or click Browse");
        keyPass.setToolTipText("Only needed if the key has a different password than the keystore");
        aliasBox.setEnabled(false);
        convertBtn.setEnabled(false);
        showBtn.setEnabled(false);
        progress.setVisible(false);
        progress.setIndeterminate(true);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(showBtn);
        actions.add(convertBtn);
        c.gridx = 0; c.gridy = 5; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1;
        form.add(actions, c);
        c.gridy = 6; form.add(progress, c);
        c.gridy = 7; form.add(status, c);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createEmptyBorder(0, 14, 14, 14));

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 18, 0, 14));
        JLabel title = new JLabel("AAB to APK Extractor");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        header.add(title, BorderLayout.WEST);
        themeBtn.setFocusable(false);
        themeBtn.addActionListener(e -> toggleTheme());
        updateThemeButton();
        header.add(themeBtn, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH);
        top.add(form, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(logScroll, BorderLayout.CENTER);

        String savedKs = PREFS.get("keystore", "");
        if (!savedKs.isEmpty() && new File(savedKs).isFile()) ksField.setText(savedKs);

        aabBrowse.addActionListener(e -> browse(aabField, "Select an Android App Bundle (.aab)", "aab"));
        ksBrowse.addActionListener(e -> {
            browse(ksField, "Select a keystore file", null);
            resetAliases();
        });
        loadAliasesBtn.addActionListener(e -> loadAliases());
        ksPass.addActionListener(e -> loadAliases());
        convertBtn.addActionListener(e -> convert());
        showBtn.addActionListener(e -> showInFolder());
        ksField.getDocument().addDocumentListener(new SimpleDocListener(this::resetAliases));
        ksPass.getDocument().addDocumentListener(new SimpleDocListener(this::resetAliases));
        aabField.getDocument().addDocumentListener(new SimpleDocListener(this::updateConvertEnabled));
        aliasBox.addActionListener(e -> updateConvertEnabled());

        // Drag & drop: an .aab and/or a keystore can be dropped anywhere on the window.
        attachDropTargets(getRootPane());
        attachDropTargets(this);

        setMinimumSize(new Dimension(620, 480));
        pack();
        setSize(Math.max(getWidth(), 700), 520);
        setLocationRelativeTo(null);
    }

    private void row(JPanel p, GridBagConstraints c, int y, String label, JComponent field, JButton button) {
        c.gridy = y; c.gridwidth = 1; c.fill = GridBagConstraints.NONE; c.weightx = 0;
        c.gridx = 0; p.add(new JLabel(label), c);
        c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; p.add(field, c);
        c.gridx = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 0;
        if (button != null) p.add(button, c);
    }

    // ---- drag & drop ----
    private static final java.util.Set<String> KEYSTORE_EXT =
            java.util.Set.of("jks", "keystore", "ks", "p12", "pfx", "bks", "jceks");

    private final DropTargetAdapter dropListener = new DropTargetAdapter() {
        @Override public void dragEnter(DropTargetDragEvent e) { onDragOver(e); }
        @Override public void dragOver(DropTargetDragEvent e) { onDragOver(e); }
        @Override public void dragExit(DropTargetEvent e) { setStatus(lastStatus, lastStatusError); }

        private void onDragOver(DropTargetDragEvent e) {
            if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                e.acceptDrag(DnDConstants.ACTION_COPY);
                status.setForeground(UIManager.getColor("Component.accentColor") != null
                        ? UIManager.getColor("Component.accentColor") : UIManager.getColor("Label.foreground"));
                status.setText("Drop to load the .aab or keystore file");
            } else {
                e.rejectDrag();
            }
        }

        @Override @SuppressWarnings("unchecked") public void drop(DropTargetDropEvent e) {
            if (!e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) { e.rejectDrop(); return; }
            try {
                e.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> files = (List<File>) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                Component target = e.getDropTargetContext().getComponent();
                e.dropComplete(true);
                SwingUtilities.invokeLater(() -> handleDrop(files, target));
            } catch (Exception ex) {
                e.dropComplete(false);
                setStatus(lastStatus, lastStatusError);
            }
        }
    };

    /** Swing text fields/combos swallow drops with their own handlers, so install ours on every component. */
    private void attachDropTargets(Component c) {
        new DropTarget(c, DnDConstants.ACTION_COPY, dropListener, true);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) attachDropTargets(child);
        }
    }

    private void handleDrop(List<File> files, Component target) {
        boolean onKeystoreField = target == ksField;
        boolean gotAab = false, gotKs = false;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName().toLowerCase();
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 ? name.substring(dot + 1) : "";
            if (ext.equals("aab")) {
                if (!gotAab) { aabField.setText(f.getAbsolutePath()); gotAab = true; }
            } else if (KEYSTORE_EXT.contains(ext) || onKeystoreField) {
                if (!gotKs) { ksField.setText(f.getAbsolutePath()); gotKs = true; }
            }
        }
        if (!gotAab && !gotKs) {
            setStatus("Drop an .aab file or a keystore (.jks, .keystore, .p12, .pfx).", true);
            return;
        }
        if (gotKs) {
            ksPass.requestFocusInWindow();
            setStatus((gotAab ? "App bundle and keystore loaded." : "Keystore loaded.") + " Enter the keystore password.", false);
        } else {
            setStatus("App bundle loaded.", false);
        }
    }

    /** Native open-file dialog: the real macOS panel (follows system light/dark) or the Windows Explorer dialog. */
    private void browse(JTextField target, String title, String ext) {
        String cur = target.getText().trim();
        String dir = !cur.isEmpty() && new File(cur).getParentFile() != null
                ? new File(cur).getParent()
                : PREFS.get(target == ksField ? "keystoreDir" : "aabDir", System.getProperty("user.home"));
        if (!new File(dir).isDirectory()) dir = System.getProperty("user.home");

        File f;
        if (IS_MAC) {
            f = macChooseFile(title, ext, dir);
        } else if (IS_WINDOWS) {
            File[] res = windowsChooseFile(title, ext, dir);
            f = res != null ? res[0] : awtChooseFile(title, ext, dir); // null result = PowerShell unavailable
        } else {
            f = awtChooseFile(title, ext, dir);
        }
        if (f != null) {
            target.setText(f.getAbsolutePath());
            try { PREFS.put(target == ksField ? "keystoreDir" : "aabDir", f.getParent()); } catch (RuntimeException ignored) { }
        }
    }

    private static final boolean IS_MAC = System.getProperty("os.name").toLowerCase().contains("mac");
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private File awtChooseFile(String title, String ext, String dir) {
        FileDialog fd = new FileDialog(this, title, FileDialog.LOAD);
        fd.setDirectory(dir);
        if (ext != null) fd.setFilenameFilter((d, name) -> name.toLowerCase().endsWith("." + ext));
        fd.setVisible(true);
        return fd.getFile() == null ? null : new File(fd.getDirectory(), fd.getFile());
    }

    /**
     * Java's own FileDialog on macOS always renders in light mode, so ask macOS itself for the panel
     * (AppleScript "choose file"). It is the genuine system panel and follows the OS appearance.
     * The EDT keeps pumping events while the panel is open (secondary loop).
     */
    private File macChooseFile(String title, String ext, String dir) {
        String script = "on run argv\n"
                + "set d to item 1 of argv\n"
                + "set p to item 2 of argv\n"
                + "try\n set loc to (POSIX file d) as alias\n on error\n set loc to path to home folder\n end try\n"
                + "set f to choose file with prompt p " + (ext != null ? "of type {\"" + ext + "\"} " : "") + "default location loc\n"
                + "return POSIX path of f\n"
                + "end run\n";
        final String[] result = new String[1];
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        Thread t = new Thread(() -> {
            try {
                Process proc = new ProcessBuilder("/usr/bin/osascript", "-", dir, title).redirectErrorStream(false).start();
                try (OutputStream os = proc.getOutputStream()) { os.write(script.getBytes(StandardCharsets.UTF_8)); }
                String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (proc.waitFor() == 0 && !out.isEmpty()) result[0] = out;
            } catch (Exception ignored) {
            } finally {
                loop.exit();
            }
        }, "mac-file-chooser");
        t.setDaemon(true);
        t.start();
        loop.enter();
        if (result[0] == null) return null;
        File f = new File(result[0]);
        return f.exists() ? f : null;
    }

    /**
     * Windows: shows the standard modern Explorer "Open" dialog (System.Windows.Forms.OpenFileDialog via
     * the PowerShell that ships with Windows). Java's own FileDialog gives the old-style dialog instead.
     * Returns {file} (file may be null if the user cancelled), or null if PowerShell could not be used,
     * in which case the caller falls back to Java's FileDialog.
     */
    private File[] windowsChooseFile(String title, String ext, String dir) {
        String script = String.join("\n",
                "Add-Type -AssemblyName System.Windows.Forms",
                "[System.Windows.Forms.Application]::EnableVisualStyles()",
                "$d = New-Object System.Windows.Forms.OpenFileDialog",
                "$d.Title = $env:AAB_TITLE",
                "$d.Filter = $env:AAB_FILTER",
                "$d.InitialDirectory = $env:AAB_DIR",
                "$d.CheckFileExists = $true",
                "$o = New-Object System.Windows.Forms.Form",
                "$o.TopMost = $true",
                "$o.ShowInTaskbar = $false",
                "$o.StartPosition = 'Manual'",
                "$o.Location = New-Object System.Drawing.Point(-32000, -32000)",
                "$o.Size = New-Object System.Drawing.Size(1, 1)",
                "$o.Show()",
                "$r = $d.ShowDialog($o)",
                "$o.Close()",
                "if ($r -eq [System.Windows.Forms.DialogResult]::OK) {",
                "  [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)",
                "  [Console]::Out.Write($d.FileName)",
                "  exit 0",
                "}",
                "exit 1");
        String filter = ext != null
                ? "Android App Bundle (*." + ext + ")|*." + ext + "|All files (*.*)|*.*"
                : "All files (*.*)|*.*|Keystore files (*.jks;*.keystore;*.ks;*.p12;*.pfx)|*.jks;*.keystore;*.ks;*.p12;*.pfx";
        String sysRoot = System.getenv("SystemRoot");
        File ps = sysRoot == null ? null : new File(sysRoot, "System32\\WindowsPowerShell\\v1.0\\powershell.exe");
        String psExe = ps != null && ps.isFile() ? ps.getPath() : "powershell.exe";
        String encoded = java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));

        final int[] code = { -1 };
        final String[] out = { "" };
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        Thread t = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(psExe, "-NoProfile", "-NonInteractive", "-STA",
                        "-WindowStyle", "Hidden", "-EncodedCommand", encoded);
                pb.environment().put("AAB_TITLE", title);
                pb.environment().put("AAB_FILTER", filter);
                pb.environment().put("AAB_DIR", dir);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process proc = pb.start();
                proc.getOutputStream().close();
                out[0] = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                code[0] = proc.waitFor();
            } catch (Exception ignored) {
            } finally {
                loop.exit();
            }
        }, "win-file-chooser");
        t.setDaemon(true);
        t.start();
        loop.enter();

        if (code[0] == 1) return new File[] { null };          // cancelled
        if (code[0] != 0) return null;                         // PowerShell failed -> fall back
        String path = out[0].replace("\uFEFF", "").trim();
        File f = new File(path);
        return path.isEmpty() || !f.isFile() ? null : new File[] { f };
    }

    private void resetAliases() {
        aliasBox.removeAllItems();
        aliasBox.setEnabled(false);
        updateConvertEnabled();
    }

    private void updateConvertEnabled() {
        boolean ok = aliasBox.isEnabled() && aliasBox.getSelectedItem() != null
                && new File(aabField.getText().trim()).isFile();
        convertBtn.setEnabled(ok);
    }

    private void setStatus(String s, boolean error) {
        lastStatus = s; lastStatusError = error;
        Color err = isDark() ? new Color(0xFF6B6B) : new Color(0xB00020);
        status.setForeground(error ? err : UIManager.getColor("Label.foreground"));
        status.setText(s);
    }

    // ---- step 3: read aliases from the keystore ----
    private void loadAliases() {
        File ks = new File(ksField.getText().trim());
        if (!ks.isFile()) { setStatus("Select a keystore file first.", true); return; }
        char[] pw = ksPass.getPassword();
        if (pw.length == 0) { setStatus("Enter the keystore password.", true); return; }
        try {
            KeyStore store = KeyStore.getInstance(ks, pw);
            List<String> aliases = new ArrayList<>();
            for (String a : Collections.list(store.aliases())) {
                if (store.isKeyEntry(a)) aliases.add(a);
            }
            if (aliases.isEmpty()) { resetAliases(); setStatus("No signing keys found in this keystore.", true); return; }
            aliasBox.removeAllItems();
            for (String a : aliases) aliasBox.addItem(a);
            String savedAlias = PREFS.get(aliasKey(ks.getAbsolutePath()), "");
            if (aliases.contains(savedAlias)) aliasBox.setSelectedItem(savedAlias);
            aliasBox.setEnabled(true);
            try { PREFS.put("keystore", ks.getAbsolutePath()); } catch (RuntimeException ignored) { }
            setStatus("Loaded " + aliases.size() + " alias" + (aliases.size() == 1 ? "" : "es") + ". Choose one.", false);
            updateConvertEnabled();
        } catch (IOException e) {
            resetAliases();
            Throwable cause = e.getCause();
            boolean badPass = e.getMessage() != null && (e.getMessage().contains("password was incorrect") || cause instanceof java.security.UnrecoverableKeyException);
            setStatus(badPass ? "Wrong keystore password." : "Cannot read keystore: " + e.getMessage(), true);
        } catch (Exception e) {
            resetAliases();
            setStatus("Cannot read keystore: " + e, true);
        } finally {
            java.util.Arrays.fill(pw, '\0');
        }
    }

    // ---- step 5: run bundletool ----
    private void convert() {
        File aab = new File(aabField.getText().trim());
        String base = aab.getName().replaceFirst("\\.[^.]*$", "");
        File dest = new File(aab.getAbsoluteFile().getParentFile(), base + ".apk");
        if (dest.exists()) {
            int r = JOptionPane.showConfirmDialog(this, dest.getName() + " already exists. Overwrite?",
                    "Overwrite?", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
        }
        final String alias = (String) aliasBox.getSelectedItem();
        final String ksPath = ksField.getText().trim();
        final char[] sp = ksPass.getPassword();
        final char[] kp = keyPass.getPassword().length > 0 ? keyPass.getPassword() : sp;
        try { PREFS.put(aliasKey(new File(ksPath).getAbsolutePath()), alias); } catch (RuntimeException ignored) { }

        setBusy(true);
        log.setText("");
        lastApk = null;
        setStatus("Converting... this can take a minute for large bundles.", false);

        new SwingWorker<Boolean, String>() {
            String error = "";

            @Override protected Boolean doInBackground() throws Exception {
                Path tmp = Files.createTempDirectory("aab2apk");
                try {
                    Path ksPassFile = tmp.resolve("ks.pw");
                    Path keyPassFile = tmp.resolve("key.pw");
                    Files.write(ksPassFile, new String(sp).getBytes(StandardCharsets.UTF_8));
                    Files.write(keyPassFile, new String(kp).getBytes(StandardCharsets.UTF_8));

                    Path out = tmp.resolve("out");
                    List<String> cmd = new ArrayList<>();
                    cmd.add(javaBinary());
                    cmd.add("-jar");
                    cmd.add(bundletoolJar());
                    cmd.add("build-apks");
                    cmd.add("--bundle=" + aab.getAbsolutePath());
                    cmd.add("--output=" + out);
                    cmd.add("--output-format=DIRECTORY");
                    cmd.add("--mode=universal");
                    cmd.add("--ks=" + new File(ksPath).getAbsolutePath());
                    cmd.add("--ks-key-alias=" + alias);
                    cmd.add("--ks-pass=file:" + ksPassFile);
                    cmd.add("--key-pass=file:" + keyPassFile);

                    ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
                    Process proc = pb.start();
                    StringBuilder tail = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (line.matches("^(INFO|[A-Z][a-z]{2} \\d{2}, \\d{4}).*") || line.startsWith("\tat ")) continue;
                            publish(line);
                            tail.append(line).append('\n');
                        }
                    }
                    int code = proc.waitFor();
                    Path apk = out.resolve("universal.apk");
                    if (code != 0 || !Files.isRegularFile(apk)) {
                        error = tail.toString().trim();
                        if (error.isEmpty()) error = "bundletool exited with code " + code;
                        return false;
                    }
                    Files.move(apk, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    return true;
                } finally {
                    deleteRecursively(tmp);
                    java.util.Arrays.fill(sp, '\0');
                    java.util.Arrays.fill(kp, '\0');
                }
            }

            @Override protected void process(List<String> lines) {
                for (String l : lines) log.append(l + "\n");
            }

            @Override protected void done() {
                setBusy(false);
                try {
                    if (get()) {
                        lastApk = dest;
                        showBtn.setEnabled(true);
                        long mb = dest.length() / (1024 * 1024);
                        setStatus("Done: " + dest.getName() + " (" + mb + " MB)", false);
                        log.append("\nSaved to " + dest.getAbsolutePath() + "\n");
                    } else {
                        setStatus("Failed - see log below.", true);
                        log.append("\n" + error + "\n");
                    }
                } catch (Exception e) {
                    setStatus("Failed: " + e.getMessage(), true);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        progress.setVisible(busy);
        loadAliasesBtn.setEnabled(!busy);
        if (busy) convertBtn.setEnabled(false); else updateConvertEnabled();
        if (busy) showBtn.setEnabled(false);
        revalidate();
    }

    private void showInFolder() {
        if (lastApk == null) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                Desktop.getDesktop().browseFileDirectory(lastApk);
            } else {
                Desktop.getDesktop().open(lastApk.getParentFile());
            }
        } catch (Exception e) {
            setStatus("Cannot open folder: " + e.getMessage(), true);
        }
    }

    // ---- helpers ----
    /** Prefs keys are limited to 80 chars, so hash the keystore path. */
    private static String aliasKey(String path) { return "alias." + Integer.toHexString(path.hashCode()); }

    private static String javaBinary() {
        boolean win = System.getProperty("os.name").toLowerCase().contains("win");
        return Paths.get(System.getProperty("java.home"), "bin", win ? "java.exe" : "java").toString();
    }

    /** bundletool.jar sits next to this jar (lib/). */
    private static String bundletoolJar() throws Exception {
        File self = new File(ApkExtractorApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File bt = new File(self.getParentFile(), "bundletool.jar");
        if (!bt.isFile()) throw new FileNotFoundException("Missing " + bt);
        return bt.getAbsolutePath();
    }

    private static void deleteRecursively(Path p) {
        try (var s = Files.walk(p)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> x.toFile().delete());
        } catch (IOException ignored) { }
    }

    private static class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable r;
        SimpleDocListener(Runnable r) { this.r = r; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
    }
}
