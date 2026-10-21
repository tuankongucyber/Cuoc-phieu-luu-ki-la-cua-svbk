import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;

/** Swing owns presentation and input routing only. Gameplay lives in GameWorld. */
@SuppressWarnings("serial")
public final class GamePanel extends JPanel {
    public static final int SCREEN_WIDTH = 1000, SCREEN_HEIGHT = 750;
    enum Screen { START, GAME, PAUSE, SETTINGS, BOSS_TEST }
    Screen screen = Screen.START, settingsReturn = Screen.START;
    GameWorld world;
    Difficulty difficulty = Difficulty.NORMAL;
    final Sound sound = new Sound();
    final JPanel menuPanel = new JPanel();
    final SimulationClock clock = new SimulationClock();
    final GameRenderer renderer = new GameRenderer();
    final javax.swing.Timer gameTimer;
    final Set<Integer> held = new HashSet<>();
    long selectedSeed = Long.getLong("svbk.seed", System.nanoTime());
    JTextField seedField;
    Th06BossId selectedOriginalBoss = Th06BossId.RUMIA;
    int selectedOriginalRoute;
    String bossLoadError = "";
    boolean preparingBossData;
    String dataStatus = "";
    JLabel dataStatusLabel;
    SwingWorker<Void, String> dataTask;

    public GamePanel() {
        setPreferredSize(new Dimension(1000, 750)); setFocusable(true);
        setLayout(new GridBagLayout()); setBackground(new Color(17, 24, 32));
        menuPanel.setPreferredSize(new Dimension(620, 620));
        menuPanel.setLayout(new BoxLayout(menuPanel, BoxLayout.Y_AXIS));
        menuPanel.setBackground(new Color(24, 34, 50));
        menuPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(75, 155, 200)),
            BorderFactory.createEmptyBorder(25, 35, 25, 35)));
        add(menuPanel);
        int[] keys = {KeyEvent.VK_W, KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
            KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_SHIFT, KeyEvent.VK_X, KeyEvent.VK_SPACE, KeyEvent.VK_ESCAPE,
            KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_R, KeyEvent.VK_ENTER};
        for (int key : keys) {
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, false), "press" + key);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, true), "release" + key);
            // Shift modifies other key bindings, so register the same actions with SHIFT_DOWN_MASK too.
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, InputEvent.SHIFT_DOWN_MASK, false), "press" + key);
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, InputEvent.SHIFT_DOWN_MASK, true), "release" + key);
            getActionMap().put("press" + key, new AbstractAction() { public void actionPerformed(ActionEvent e) { press(key); } });
            getActionMap().put("release" + key, new AbstractAction() { public void actionPerformed(ActionEvent e) { release(key); } });
        }
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
                if (screen == Screen.GAME && world.state == GameWorld.State.LEVEL_UP) {
                    for (int i = 0; i < 3; i++) if (GameRenderer.CARDS[i].contains(e.getPoint())) { world.chooseUpgrade(i); break; }
                    if (GameRenderer.REROLL.contains(e.getPoint())) world.reroll();
                    repaint();
                }
            }
        });
        showMenu(Screen.START);
        gameTimer = new javax.swing.Timer(16, e -> {
            boolean running = screen == Screen.GAME && world != null && !world.paused && world.state == GameWorld.State.PLAYING;
            clock.advance(System.nanoTime(), running, () -> world.tick());
            repaint();
        });
        gameTimer.start();
    }
    void startRun(long seed) {
        if (!Boolean.getBoolean("svbk.legacyBosses") && !Th06Data.ready(2, 6)) {
            selectedSeed = seed;
            prepareBossData(() -> startRun(seed), Screen.START, 2, 6); return;
        }
        bossLoadError = "";
        if (world != null) world.dispose();
        selectedSeed = seed; world = new GameWorld(seed, difficulty);
        held.clear(); screen = Screen.GAME; menuPanel.setVisible(false); requestFocusInWindow(); repaint();
    }
    void startBossTest(long seed, int encounter) {
        if (world != null) world.dispose();
        selectedSeed = seed; world = GameWorld.bossTest(seed, difficulty, encounter);
        held.clear(); screen = Screen.GAME; menuPanel.setVisible(false); requestFocusInWindow(); repaint();
    }
    void startOriginalBoss(long seed, Th06BossId identity, int route) {
        if (!Th06Data.ready(identity.stage)) {
            selectedSeed = seed;
            prepareBossData(() -> startOriginalBoss(seed, identity, route), Screen.BOSS_TEST, identity.stage); return;
        }
        try {
            GameWorld next = GameWorld.originalBossTest(seed, difficulty, identity, route);
            if (world != null) world.dispose();
            world = next; selectedSeed = seed; selectedOriginalBoss = identity; selectedOriginalRoute = route;
            bossLoadError = "";
            held.clear(); screen = Screen.GAME; menuPanel.setVisible(false); requestFocusInWindow(); repaint();
        } catch (java.io.IOException error) {
            bossLoadError = "Không tải được Boss: " + error.getMessage(); showMenu(Screen.BOSS_TEST);
        }
    }
    private void prepareBossData(Runnable onReady, Screen origin, int... stages) {
        if (preparingBossData) return;
        if (Boolean.getBoolean("svbk.th06.offline") && !java.nio.file.Files.isRegularFile(Th06Data.directory().resolve("reference-data.zip"))) {
            bossLoadError = "Chưa có dữ liệu Boss. Kết nối mạng hoặc chọn gói ZIP."; showMenu(origin); return;
        }
        runDataTask(progress -> Th06Setup.ensure(progress, stages), onReady, origin);
    }
    @FunctionalInterface private interface DataPreparation {
        void run(java.util.function.IntConsumer progress) throws java.io.IOException;
    }
    private void runDataTask(DataPreparation preparation, Runnable onReady, Screen origin) {
        preparingBossData = true; bossLoadError = ""; dataStatus = "Đang chuẩn bị dữ liệu Boss…";
        showMenu(origin);
        dataTask = new SwingWorker<>() {
            @Override protected Void doInBackground() throws java.io.IOException {
                preparation.run(percent -> publish(percent < 0 ? "Đang chuẩn bị script Boss…" : "Đang tải dữ liệu Boss (~54 MB)… " + percent + "%"));
                return null;
            }
            @Override protected void process(java.util.List<String> updates) {
                dataStatus = updates.get(updates.size() - 1);
                if (dataStatusLabel != null) dataStatusLabel.setText(dataStatus);
            }
            @Override protected void done() {
                preparingBossData = false; dataStatus = "";
                try {
                    get(); bossLoadError = "";
                    if (screen == origin) onReady.run(); else showMenu(screen);
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    bossLoadError = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                    showMenu(screen == Screen.BOSS_TEST ? Screen.BOSS_TEST : Screen.START);
                }
            }
        };
        dataTask.execute();
    }
    private void chooseBossPack() {
        if (preparingBossData) return;
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Chọn gói ZIP dữ liệu Boss");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("TH06 game data (*.zip)", "zip"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path source = chooser.getSelectedFile().toPath();
        Screen origin = screen == Screen.BOSS_TEST ? Screen.BOSS_TEST : Screen.START;
        runDataTask(progress -> Th06Setup.importPack(source, progress), () -> showMenu(origin), origin);
    }
    private void dataMessage() {
        dataStatusLabel = null;
        if (preparingBossData) dataStatusLabel = text(dataStatus, 14, new Color(255, 215, 100));
        else if (!bossLoadError.isEmpty()) {
            String shortError = bossLoadError.length() > 66 ? bossLoadError.substring(0, 63) + "…" : bossLoadError;
            text(shortError, 14, new Color(255,140,150)).setToolTipText(bossLoadError);
            JButton choose = new JButton("Chọn gói ZIP dữ liệu…"); choose.setFont(new Font("Arial", Font.PLAIN, 14));
            choose.setAlignmentX(Component.CENTER_ALIGNMENT); choose.setMaximumSize(new Dimension(480, 30));
            choose.addActionListener(e -> chooseBossPack()); menuPanel.add(choose); menuPanel.add(Box.createVerticalStrut(7));
        }
    }
    private void replay() {
        if (world.originalPractice != null) startOriginalBoss(selectedSeed, world.originalPractice, world.originalRoute);
        else if (world.isBossTest()) startBossTest(selectedSeed, world.practiceEncounter);
        else startRun(selectedSeed);
    }
    private void startFromSeed(boolean testBoss) {
        try {
            selectedSeed = Long.parseLong(seedField.getText().trim());
            if (testBoss) showMenu(Screen.BOSS_TEST); else startRun(selectedSeed);
        } catch (NumberFormatException error) {
            seedField.setText("42"); seedField.setToolTipText("Seed phải là số nguyên 64-bit.");
        }
    }
    void press(int key) {
        if (!held.add(key)) return;
        if (key == KeyEvent.VK_ESCAPE) { escape(); return; }
        if (screen != Screen.GAME || world == null) return;
        if (world.state == GameWorld.State.LEVEL_UP) {
            if (key >= KeyEvent.VK_1 && key <= KeyEvent.VK_3) world.chooseUpgrade(key - KeyEvent.VK_1);
            if (key == KeyEvent.VK_R) world.reroll();
        } else if (world.state == GameWorld.State.PLAYING) {
            updateMovement();
            if (key == KeyEvent.VK_X) world.input.dashPressed = true;
            if (key == KeyEvent.VK_SPACE) world.input.bombPressed = true;
        } else if (key == KeyEvent.VK_ENTER) {
            replay();
        }
        repaint();
    }
    void release(int key) { held.remove(key); updateMovement(); }
    private void updateMovement() {
        if (screen != Screen.GAME || world == null || world.state != GameWorld.State.PLAYING) return;
        world.input.up = held.contains(KeyEvent.VK_W) || held.contains(KeyEvent.VK_UP);
        world.input.down = held.contains(KeyEvent.VK_S) || held.contains(KeyEvent.VK_DOWN);
        world.input.left = held.contains(KeyEvent.VK_A) || held.contains(KeyEvent.VK_LEFT);
        world.input.right = held.contains(KeyEvent.VK_D) || held.contains(KeyEvent.VK_RIGHT);
        world.input.focus = held.contains(KeyEvent.VK_SHIFT);
    }
    void escape() {
        if (screen == Screen.GAME) {
            if (world.state == GameWorld.State.VICTORY || world.state == GameWorld.State.DEFEAT) showMenu(Screen.START);
            else { world.setPaused(true); showMenu(Screen.PAUSE); }
        } else if (screen == Screen.PAUSE) resume();
        else if (screen == Screen.SETTINGS) showMenu(settingsReturn);
        else if (screen == Screen.BOSS_TEST) showMenu(Screen.START);
    }
    void resume() {
        boolean escapeHeld = held.contains(KeyEvent.VK_ESCAPE);
        world.setPaused(false); held.clear();
        if (escapeHeld) held.add(KeyEvent.VK_ESCAPE);
        screen = Screen.GAME; menuPanel.setVisible(false); requestFocusInWindow(); repaint();
    }
    public void pauseForFocusLoss() {
        held.clear();
        if (world != null) world.input.clear();
        if (screen == Screen.GAME && world.state != GameWorld.State.VICTORY && world.state != GameWorld.State.DEFEAT) {
            world.setPaused(true); showMenu(Screen.PAUSE);
        }
    }
    private JLabel text(String value, int size, Color color) {
        JLabel label = new JLabel(value); label.setFont(new Font("Arial", Font.PLAIN, size));
        label.setForeground(color); label.setAlignmentX(Component.CENTER_ALIGNMENT);
        menuPanel.add(label); menuPanel.add(Box.createVerticalStrut(13));
        return label;
    }
    private void button(String value, Runnable action) {
        JButton button = new JButton(value); button.setFont(new Font("Arial", Font.BOLD, 18));
        button.setAlignmentX(Component.CENTER_ALIGNMENT); button.setMaximumSize(new Dimension(480, 44));
        button.setEnabled(!preparingBossData);
        button.addActionListener(e -> action.run()); menuPanel.add(button); menuPanel.add(Box.createVerticalStrut(13));
    }
    void showMenu(Screen next) {
        if (next == Screen.START && world != null) world.dispose();
        screen = next; menuPanel.removeAll();
        text(next == Screen.SETTINGS ? "CÀI ĐẶT" : next == Screen.PAUSE ? "TẠM DỪNG" : next == Screen.BOSS_TEST ? "TEST BOSS" : "CUỘC PHIÊU LƯU KÌ LẠ", 28, new Color(255, 215, 100));
        if (next == Screen.SETTINGS) {
            JCheckBox audio = new JCheckBox("Bật âm thanh", sound.isSoundEnabled()); audio.setForeground(Color.WHITE);
            audio.setOpaque(false); audio.setAlignmentX(Component.CENTER_ALIGNMENT);
            audio.addActionListener(e -> sound.setSoundEnabled(audio.isSelected())); menuPanel.add(audio);
            text("Âm lượng (bản này chưa có file âm thanh)", 15, Color.LIGHT_GRAY);
            JSlider slider = new JSlider(0, 100, sound.getVolume()); slider.setOpaque(false);
            slider.setMaximumSize(new Dimension(480, 40)); slider.addChangeListener(e -> sound.setVolume(slider.getValue())); menuPanel.add(slider);
            menuPanel.add(Box.createVerticalStrut(16));
            text("Độ khó • chọn trước khi bắt đầu ván", 17, Color.WHITE);
            JComboBox<Difficulty> combo = new JComboBox<>(Difficulty.values()); combo.setSelectedItem(difficulty);
            combo.setEnabled(settingsReturn == Screen.START); combo.setMaximumSize(new Dimension(480, 36));
            combo.addActionListener(e -> difficulty = (Difficulty) combo.getSelectedItem()); menuPanel.add(combo);
            menuPanel.add(Box.createVerticalStrut(16));
            text("Ảnh hưởng máu, sát thương, tốc độ, mật độ và pattern.", 15, Color.LIGHT_GRAY);
            text("Hitbox và điều khiển giữ nguyên ở mọi mức khó.", 15, Color.LIGHT_GRAY);
            button("Quay lại [Esc]", () -> showMenu(settingsReturn));
        } else if (next == Screen.BOSS_TEST) {
            if (!Boolean.getBoolean("svbk.legacyBosses")) {
                text("Độ khó: " + difficulty, 18, Color.WHITE);
                text("Chọn Boss • đánh trọn bộ đòn riêng", 17, Color.LIGHT_GRAY);
                JComboBox<Th06BossId> roster = new JComboBox<>(Th06BossId.values());
                roster.setSelectedItem(selectedOriginalBoss); roster.setMaximumSize(new Dimension(480, 36));
                roster.setEnabled(!preparingBossData);
                roster.addActionListener(e -> selectedOriginalBoss = (Th06BossId) roster.getSelectedItem()); menuPanel.add(roster);
                menuPanel.add(Box.createVerticalStrut(15));
                text("Nhánh đòn Boss theo nhân vật TH06", 15, Color.LIGHT_GRAY);
                JComboBox<String> route = new JComboBox<>(new String[]{"Reimu A", "Reimu B", "Marisa A", "Marisa B"});
                route.setSelectedIndex(selectedOriginalRoute); route.setMaximumSize(new Dimension(480, 36));
                route.setEnabled(!preparingBossData);
                route.addActionListener(e -> selectedOriginalRoute = route.getSelectedIndex()); menuPanel.add(route);
                menuPanel.add(Box.createVerticalStrut(15));
                text("4 vũ khí cấp 4 • 100 HP • 3 Bomb", 16, Color.LIGHT_GRAY);
                button("Đánh Boss đã chọn", () -> startOriginalBoss(selectedSeed, selectedOriginalBoss, selectedOriginalRoute));
                button("Quay lại [Esc]", () -> showMenu(Screen.START));
                dataMessage();
                menuPanel.add(Box.createVerticalGlue());
                text("Shift: Focus • X: Dash • Space: Bomb", 16, Color.LIGHT_GRAY);
                text("Thắng / thua: Enter để thử lại cùng Boss", 16, Color.LIGHT_GRAY);
            } else {
            text("Độ khó: " + difficulty, 18, Color.WHITE);
            text("Vào thẳng đấu trường, đánh đủ 3 pha.", 17, Color.LIGHT_GRAY);
            text("Build thử: 4 vũ khí cấp 4 + nội tại", 16, Color.LIGHT_GRAY);
            text("100 HP • 3 Bomb • không bị ngắt để lên cấp", 16, Color.LIGHT_GRAY);
            button("Test Boss giữa", () -> startBossTest(selectedSeed, 0));
            button("Test Boss cuối", () -> startBossTest(selectedSeed, 1));
            button("Quay lại [Esc]", () -> showMenu(Screen.START));
            menuPanel.add(Box.createVerticalGlue());
            text("Shift: Focus • X: Dash • Space: Bomb", 16, Color.LIGHT_GRAY);
            text("Thắng / thua: Enter để thử lại cùng Boss", 16, Color.LIGHT_GRAY);
            }
        } else {
            text(next == Screen.START ? "CỦA SVBK • DANMAKU SURVIVORS" : "Mọi đồng hồ chiến đấu đã dừng", 18, Color.WHITE);
            text("Độ khó: " + (next == Screen.START ? difficulty : world.difficulty), 16, Color.LIGHT_GRAY);
            if (next == Screen.START) {
                text("Seed • nhập lại để tái hiện cùng một run", 14, Color.LIGHT_GRAY);
                seedField = new JTextField(Long.toString(selectedSeed)); seedField.setMaximumSize(new Dimension(480, 32));
                seedField.setEnabled(!preparingBossData);
                seedField.setHorizontalAlignment(JTextField.CENTER); menuPanel.add(seedField); menuPanel.add(Box.createVerticalStrut(14));
                button("Bắt đầu", () -> startFromSeed(false));
                button("Test Boss", () -> startFromSeed(true));
                dataMessage();
            } else button("Tiếp tục [Esc]", this::resume);
            button("Cài đặt", () -> { settingsReturn = screen; showMenu(Screen.SETTINGS); });
            if (next == Screen.PAUSE) button("Về Start • kết thúc ván", () -> { selectedSeed++; held.clear(); showMenu(Screen.START); });
            if (next == Screen.PAUSE && world.isBossTest())
                button("Chơi lại Test Boss", this::replay);
            menuPanel.add(Box.createVerticalGlue());
            text("WASD / Mũi tên: di chuyển • Shift: Focus", 16, Color.LIGHT_GRAY);
            text("X: Dash không bất tử • Space: Bomb", 16, Color.LIGHT_GRAY);
            text("1 / 2 / 3: nâng cấp • R: đổi lựa chọn • Esc: tạm dừng", 16, Color.LIGHT_GRAY);
            text("4 vũ khí + 4 nội tại • Mỗi Boss có bộ đòn riêng", 14, new Color(255, 215, 100));
        }
        menuPanel.setVisible(true); menuPanel.revalidate(); repaint();
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (world != null && screen != Screen.START) renderer.draw((Graphics2D) graphics, world);
        if (screen != Screen.GAME) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(new Color(0, 0, 0, 175)); g.fillRect(0, 0, 1000, 750); g.dispose();
        }
    }
}
