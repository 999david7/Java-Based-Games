import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Random;

public class Main {

    // ── Palette ─────────────────────────────────────────────────────────────
    static final Color C_BG      = new Color(0x08080F);
    static final Color C_CARD    = new Color(0x10101C);
    static final Color C_BORDER  = new Color(0x1E1E36);
    static final Color C_ACCENT  = new Color(0x6EE7F7);  // P1 Color
    static final Color C_ACCENT2 = new Color(0xB06EF7);  // P2 Color
    static final Color C_SUCCESS = new Color(0x39D98A);  // P3 / Success
    static final Color C_DANGER  = new Color(0xFF5572);
    static final Color C_WARN    = new Color(0xFFB836);  // P4 Color
    static final Color C_TEXT    = new Color(0xECECFF);
    static final Color C_MUTED   = new Color(0x52527A);
    static final Color C_DIM     = new Color(0x1A1A2E);

    // ── State ────────────────────────────────────────────────────────────────
    enum GameMode { SOLO, FRIEND_SETS, MULTIPLAYER }
    private GameMode mode = GameMode.SOLO;
    private int maxAttempts = 5;

    // Multiplayer State
    private int numPlayers = 1;
    private int currentPlayerIndex = 0;
    private int[] targets;
    private int[] attemptsArr;
    private boolean[] finished;

    private long startTime;
    private int bestScore = Integer.MAX_VALUE;
    private boolean gameOver = false;

    // ── UI ───────────────────────────────────────────────────────────────────
    private JFrame frame;
    private JPanel contentPane;
    private NumberInput numberInput;
    private FeedbackDisplay feedbackDisplay;
    private LivesBar livesBar;
    private HistoryList historyList;
    private JLabel timerLbl, bestLbl, turnLbl;
    private Timer uiTimer;
    private int bgOffset = 0;
    private float glowPhase = 0f;

    public Main() {
        applyUIDefaults();
        chooseDifficulty();
        chooseGameMode();
        buildUI();
        startTimers();
        updateTurnLabel();
    }

    private void applyUIDefaults() {
        UIManager.put("OptionPane.background",        C_CARD);
        UIManager.put("Panel.background",             C_CARD);
        UIManager.put("OptionPane.messageForeground", C_TEXT);
        UIManager.put("Button.background",            C_DIM);
        UIManager.put("Button.foreground",            C_TEXT);
        UIManager.put("RadioButton.background",       C_CARD);
        UIManager.put("RadioButton.foreground",       C_TEXT);
        UIManager.put("Spinner.background",           C_DIM);
        UIManager.put("TextField.background",         C_DIM);
        UIManager.put("TextField.foreground",         C_TEXT);
        UIManager.put("TextField.caretForeground",    C_ACCENT);
        UIManager.put("Label.foreground",             C_TEXT);
    }

    // ════════════════════════════════════════════════════════════════════════
    private void chooseDifficulty() {
        JPanel p = dialogPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        addDialogTitle(p, "SELECT DIFFICULTY");

        JRadioButton[] opts = {
                radioBtn("EASY     — 10 total attempts", false),
                radioBtn("NORMAL   —  5 total attempts", true),
                radioBtn("HARD     —  3 total attempts", false),
                radioBtn("CUSTOM   —  set your own", false)
        };
        ButtonGroup bg = new ButtonGroup();
        for (JRadioButton o : opts) { bg.add(o); p.add(o); p.add(vgap(4)); }

        p.add(vgap(8));
        JLabel customLbl = new JLabel("Custom attempt count (1 – 99):");
        customLbl.setFont(mono(12)); customLbl.setForeground(C_MUTED);
        customLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        SpinnerNumberModel spinModel = new SpinnerNumberModel(7, 1, 99, 1);
        JSpinner spinner = new JSpinner(spinModel);
        styleSpinner(spinner);
        spinner.setEnabled(false);
        spinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        spinner.setAlignmentX(Component.LEFT_ALIGNMENT);

        opts[3].addActionListener(e -> spinner.setEnabled(true));
        for (int i = 0; i < 3; i++) opts[i].addActionListener(e -> spinner.setEnabled(false));
        p.add(customLbl); p.add(vgap(4)); p.add(spinner);

        int r = JOptionPane.showConfirmDialog(null, p, "Number Guesser",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) System.exit(0);

        if      (opts[0].isSelected()) maxAttempts = 10;
        else if (opts[2].isSelected()) maxAttempts = 3;
        else if (opts[3].isSelected()) maxAttempts = (int) spinner.getValue();
        else                           maxAttempts = 5;
    }

    private void chooseGameMode() {
        JPanel p = dialogPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        addDialogTitle(p, "GAME MODE");

        JRadioButton solo   = radioBtn("SOLO         — computer picks", true);
        JRadioButton pass   = radioBtn("PASS         — a friend sets the number", false);
        JRadioButton multi  = radioBtn("MULTIPLAYER  — race to guess each other's (2-8P)", false);
        ButtonGroup bg = new ButtonGroup();
        bg.add(solo); bg.add(pass); bg.add(multi);
        p.add(solo); p.add(vgap(4)); p.add(pass); p.add(vgap(4)); p.add(multi);

        int r = JOptionPane.showConfirmDialog(null, p, "Number Guesser",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) System.exit(0);

        if (solo.isSelected()) {
            mode = GameMode.SOLO;
            numPlayers = 1;
            targets = new int[]{ new Random().nextInt(100) + 1 };
        }
        else if (pass.isSelected()) {
            mode = GameMode.FRIEND_SETS;
            numPlayers = 1;
            targets = new int[1];
            while (true) {
                try {
                    String s = JOptionPane.showInputDialog(null,
                            "Friend, enter a secret number (1–100):", "Setup", JOptionPane.PLAIN_MESSAGE);
                    if (s == null) System.exit(0);
                    int val = Integer.parseInt(s.trim());
                    if (val >= 1 && val <= 100) { targets[0] = val; break; }
                } catch (Exception ignored) {}
            }
        }
        else {
            mode = GameMode.MULTIPLAYER;
            String s = JOptionPane.showInputDialog(null, "How many players? (2 to 8):", "2");
            if (s == null) System.exit(0);
            try {
                numPlayers = Math.max(2, Math.min(8, Integer.parseInt(s.trim())));
            } catch(Exception e) { numPlayers = 2; }

            targets = new int[numPlayers];
            for (int i = 0; i < numPlayers; i++) {
                int setter = i + 1;
                int guesser = (i + 1) % numPlayers + 1;
                while (true) {
                    JPanel setupPanel = dialogPanel();
                    setupPanel.setLayout(new BoxLayout(setupPanel, BoxLayout.Y_AXIS));
                    addDialogTitle(setupPanel, "SECRET SETUP");
                    JLabel lbl = new JLabel("Player " + setter + ", set number for Player " + guesser + ":");
                    lbl.setFont(mono(13)); lbl.setForeground(C_TEXT);
                    setupPanel.add(lbl); setupPanel.add(vgap(8));

                    JPasswordField pf = new JPasswordField(10);
                    pf.setFont(mono(16, true));
                    pf.setBackground(C_DIM); pf.setForeground(C_TEXT); pf.setCaretColor(C_ACCENT);
                    pf.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
                    setupPanel.add(pf);

                    int res = JOptionPane.showConfirmDialog(null, setupPanel, "Setup", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                    if (res != JOptionPane.OK_OPTION) System.exit(0);
                    try {
                        int val = Integer.parseInt(new String(pf.getPassword()).trim());
                        if (val >= 1 && val <= 100) {
                            targets[guesser - 1] = val; // Target stored for the guesser
                            break;
                        }
                    } catch(Exception ignored) {}
                }
            }
        }

        attemptsArr = new int[numPlayers];
        finished = new boolean[numPlayers];
        currentPlayerIndex = 0;
    }

    // ════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        frame = new JFrame();
        frame.setUndecorated(true);
        frame.setSize(460, 680);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        contentPane = new JPanel(null) {
            @Override protected void paintComponent(Graphics g2) {
                Graphics2D g = (Graphics2D) g2;
                applyQualityRendering(g);
                g.setColor(C_BG);
                g.fillRect(0, 0, getWidth(), getHeight());
                float phase = (bgOffset % 360) / 360f;
                float cx = (float)(getWidth() * (0.3 + 0.4 * Math.sin(phase * Math.PI * 2)));
                RadialGradientPaint rg = new RadialGradientPaint(cx, 120, 260,
                        new float[]{0f, 1f},
                        new Color[]{new Color(110,231,247,22), new Color(0,0,0,0)});
                g.setPaint(rg); g.fillRect(0, 0, getWidth(), getHeight());
                RadialGradientPaint rg2 = new RadialGradientPaint(
                        getWidth()*0.75f, getHeight()*0.85f, 200,
                        new float[]{0f, 1f},
                        new Color[]{new Color(176,110,247,18), new Color(0,0,0,0)});
                g.setPaint(rg2); g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(C_BORDER);
                g.setStroke(new BasicStroke(1f));
                g.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 20, 20);
            }
        };

        // ── Title bar
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setBounds(0, 0, 460, 48);

        JLabel titleLbl2 = new JLabel("  NUMBER GUESSER");
        titleLbl2.setFont(mono(13, true));
        titleLbl2.setForeground(C_MUTED);

        JButton closeBtn = iconButton("✕", C_DANGER);
        closeBtn.addActionListener(e -> System.exit(0));
        JButton minBtn = iconButton("−", C_MUTED);
        minBtn.addActionListener(e -> frame.setExtendedState(JFrame.ICONIFIED));

        JPanel winBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 10));
        winBtns.setOpaque(false);
        winBtns.add(minBtn); winBtns.add(closeBtn);

        titleBar.add(titleLbl2, BorderLayout.WEST);
        titleBar.add(winBtns,   BorderLayout.EAST);

        MouseAdapter drag = new MouseAdapter() {
            Point start;
            public void mousePressed(MouseEvent e)  { start = e.getPoint(); }
            public void mouseDragged(MouseEvent e)  {
                Point loc = frame.getLocation();
                frame.setLocation(loc.x + e.getX() - start.x, loc.y + e.getY() - start.y);
            }
        };
        titleBar.addMouseListener(drag);
        titleBar.addMouseMotionListener(drag);
        contentPane.add(titleBar);

        // ── Stats row
        timerLbl = statLabel("0s");
        timerLbl.setBounds(20, 52, 120, 22);

        turnLbl = new JLabel("");
        turnLbl.setFont(mono(13, true));
        turnLbl.setHorizontalAlignment(SwingConstants.CENTER);
        turnLbl.setBounds(140, 52, 180, 22);

        bestLbl = statLabel(mode == GameMode.MULTIPLAYER ? "VERSUS" : "");
        bestLbl.setHorizontalAlignment(SwingConstants.RIGHT);
        bestLbl.setBounds(320, 52, 120, 22);

        contentPane.add(timerLbl);
        contentPane.add(turnLbl);
        contentPane.add(bestLbl);

        // ── Feedback
        feedbackDisplay = new FeedbackDisplay();
        feedbackDisplay.setBounds(20, 82, 420, 140);
        contentPane.add(feedbackDisplay);

        // ── Lives bar
        livesBar = new LivesBar(maxAttempts);
        livesBar.setBounds(20, 228, 420, 30);
        contentPane.add(livesBar);

        // ── Input
        numberInput = new NumberInput();
        numberInput.setBounds(20, 274, 420, 72);
        numberInput.onSubmit = this::checkGuess;
        contentPane.add(numberInput);

        // ── Guess button
        NeonButton guessBtn = new NeonButton("GUESS");
        guessBtn.setBounds(20, 358, 420, 52);
        guessBtn.addActionListener(e -> checkGuess());
        contentPane.add(guessBtn);

        // ── History label
        JLabel hLbl = new JLabel("HISTORY");
        hLbl.setFont(mono(10, true));
        hLbl.setForeground(C_MUTED);
        hLbl.setBounds(20, 426, 100, 16);
        contentPane.add(hLbl);

        historyList = new HistoryList();
        historyList.setBounds(20, 448, 420, 210);
        contentPane.add(historyList);

        frame.setContentPane(contentPane);
        frame.setVisible(true);
        SwingUtilities.invokeLater(numberInput::focus);
    }

    private void startTimers() {
        startTime = System.currentTimeMillis();
        uiTimer = new Timer(33, e -> {
            long s = (System.currentTimeMillis() - startTime) / 1000;
            timerLbl.setText(s + "s");
            bgOffset++;
            glowPhase += 0.02f;
            feedbackDisplay.tick(glowPhase);
            contentPane.repaint();
        });
        uiTimer.start();
    }

    static Color getPlayerColor(int p) {
        switch (p) {
            case 1: return C_ACCENT;                  // Cyan
            case 2: return C_ACCENT2;                 // Purple
            case 3: return C_SUCCESS;                 // Green
            case 4: return C_WARN;                    // Orange/Yellow
            case 5: return new Color(0xFF79C6);       // Pink
            case 6: return new Color(0x8BE9FD);       // Light Blue
            case 7: return new Color(0x50FA7B);       // Bright Lime
            case 8: return new Color(0xFF5555);       // Red
            default: return C_TEXT;
        }
    }

    private void updateTurnLabel() {
        if (mode == GameMode.MULTIPLAYER) {
            int p = currentPlayerIndex + 1;
            turnLbl.setText("PLAYER " + p + "'S TURN");
            Color c = getPlayerColor(p);
            turnLbl.setForeground(c);
            numberInput.setCaretColor(c);
        } else {
            turnLbl.setText("");
            numberInput.setCaretColor(C_ACCENT);
        }
        livesBar.setUsed(attemptsArr[currentPlayerIndex]);
    }

    // ════════════════════════════════════════════════════════════════════════
    private void checkGuess() {
        if (gameOver) return;
        String raw = numberInput.getText().trim();
        try {
            int guess = Integer.parseInt(raw);
            if (guess < 1 || guess > 100) {
                feedbackDisplay.set("1 to 100 only", C_WARN, "");
                return;
            }

            int pIdx = currentPlayerIndex;
            attemptsArr[pIdx]++;
            int cmp = Integer.compare(guess, targets[pIdx]);
            historyList.addEntry(attemptsArr[pIdx], guess, cmp, mode == GameMode.MULTIPLAYER ? pIdx + 1 : 0);

            if (cmp == 0) {
                finished[pIdx] = true;
                String winMsg = mode == GameMode.MULTIPLAYER ? "P" + (pIdx+1) + " WINS!" : "CORRECT!";
                Color winCol = mode == GameMode.MULTIPLAYER ? getPlayerColor(pIdx+1) : C_SUCCESS;
                feedbackDisplay.set(winMsg, winCol, "Nailed their secret number!");

                if (mode != GameMode.MULTIPLAYER) updateBest();

                // End game immediately when someone correctly guesses
                gameOver = true; uiTimer.stop();
                delay(1500, () -> endGame(true));

            } else if (attemptsArr[pIdx] >= maxAttempts) {
                finished[pIdx] = true;
                if (allFinished()) {
                    feedbackDisplay.set("GAME OVER", C_DANGER, mode == GameMode.MULTIPLAYER ? "Everyone lost!" : "Out of attempts!");
                    gameOver = true; uiTimer.stop();
                    delay(1200, () -> endGame(false));
                } else {
                    feedbackDisplay.set("ELIMINATED", C_DANGER, "P" + (pIdx+1) + " is out!");
                    nextPlayer();
                }
            } else {
                Color c = mode == GameMode.MULTIPLAYER ? getPlayerColor(pIdx+1) : (cmp < 0 ? C_ACCENT : C_ACCENT2);
                feedbackDisplay.set(cmp < 0 ? "TOO LOW  ↑" : "TOO HIGH  ↓", c,
                        (maxAttempts - attemptsArr[pIdx]) + " attempts left");

                if (mode == GameMode.MULTIPLAYER) {
                    nextPlayer();
                } else {
                    livesBar.setUsed(attemptsArr[pIdx]);
                }
            }
            numberInput.clear();
        } catch (NumberFormatException ex) {
            feedbackDisplay.set("NUMBERS ONLY", C_WARN, "enter a digit 1–100");
        }
    }

    private boolean allFinished() {
        for (boolean b : finished) if (!b) return false;
        return true;
    }

    private void nextPlayer() {
        int start = currentPlayerIndex;
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % numPlayers;
        } while (finished[currentPlayerIndex] && currentPlayerIndex != start);
        updateTurnLabel();
    }

    private void updateBest() {
        if (mode != GameMode.MULTIPLAYER && attemptsArr[0] < bestScore) {
            bestScore = attemptsArr[0];
            bestLbl.setText("BEST  " + bestScore);
        }
    }

    private void endGame(boolean won) {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        JPanel p = dialogPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        String title = won ? "✦  VICTORY" : "✗  GAME OVER";
        if (won && mode == GameMode.MULTIPLAYER) title = "✦  PLAYER " + (currentPlayerIndex + 1) + " WINS";
        addDialogTitle(p, title);

        String msg;
        if (won) {
            msg = "Guessed in " + attemptsArr[currentPlayerIndex] + " attempt(s)  ·  " + elapsed + "s";
        } else {
            if (mode == GameMode.MULTIPLAYER) {
                StringBuilder sb = new StringBuilder("<html>Targets were:<br>");
                for (int i=0; i<numPlayers; i++) {
                    sb.append("P").append(i+1).append(": ").append(targets[i]).append("<br>");
                }
                sb.append("</html>");
                msg = sb.toString();
            } else {
                msg = "The number was " + targets[0];
            }
        }

        JLabel sub = new JLabel(msg);
        sub.setFont(mono(13)); sub.setForeground(C_MUTED);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(sub); p.add(vgap(12));
        JLabel q = new JLabel("Play again?");
        q.setFont(mono(13)); q.setForeground(C_TEXT);
        q.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(q);

        int r = JOptionPane.showConfirmDialog(frame, p, "Number Guesser",
                JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.YES_OPTION) { frame.dispose(); new Main(); }
        else System.exit(0);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Custom Swing components
    // ════════════════════════════════════════════════════════════════════════

    static class FeedbackDisplay extends JComponent {
        String big = "Type a number", small = "1 – 100";
        Color bigColor = new Color(0x52527A);
        float phase = 0;
        void set(String b, Color c, String s) { big = b; bigColor = c; small = s; repaint(); }
        void tick(float p) { phase = p; }
        @Override protected void paintComponent(Graphics g2) {
            Graphics2D g = (Graphics2D) g2;
            applyQualityRendering(g);
            g.setColor(C_CARD);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18));
            float alpha = (float)(0.35 + 0.25 * Math.sin(phase * Math.PI * 2));
            g.setColor(new Color(bigColor.getRed(), bigColor.getGreen(), bigColor.getBlue(),
                    Math.min(255, (int)(alpha*255))));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth()-1.5f, getHeight()-1.5f, 18, 18));
            g.setFont(mono(11));
            g.setColor(C_MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(small, (getWidth()-fm.stringWidth(small))/2, getHeight()-18);
            g.setFont(mono(30, true));
            fm = g.getFontMetrics();
            // glow
            g.setColor(new Color(bigColor.getRed(), bigColor.getGreen(), bigColor.getBlue(), 55));
            for (int dx=-3; dx<=3; dx++) for (int dy=-3; dy<=3; dy++)
                g.drawString(big, (getWidth()-fm.stringWidth(big))/2+dx, getHeight()/2+12+dy);
            g.setColor(bigColor);
            g.drawString(big, (getWidth()-fm.stringWidth(big))/2, getHeight()/2+12);
        }
    }

    static class LivesBar extends JComponent {
        int total, used = 0;
        LivesBar(int t) { total = t; }
        void setUsed(int u) { used = u; repaint(); }
        @Override protected void paintComponent(Graphics g2) {
            Graphics2D g = (Graphics2D) g2;
            applyQualityRendering(g);
            int n = Math.min(total, 20);
            float segW = (getWidth() - (n-1)*4f) / n;
            for (int i = 0; i < n; i++) {
                float x = i*(segW+4);
                boolean spent = i < used;
                Color fill = !spent ? C_DIM
                        : (double)used/total < 0.5 ? C_ACCENT
                          : (double)used/total < 0.8 ? C_WARN : C_DANGER;
                g.setColor(fill);
                g.fill(new RoundRectangle2D.Float(x, 10, segW, 10, 5, 5));
                if (!spent) {
                    g.setColor(C_BORDER); g.setStroke(new BasicStroke(1f));
                    g.draw(new RoundRectangle2D.Float(x, 10, segW, 10, 5, 5));
                }
            }
            g.setFont(mono(10)); g.setColor(C_MUTED);
            String lbl = used + " / " + total;
            g.drawString(lbl, getWidth()-g.getFontMetrics().stringWidth(lbl), 9);
        }
    }

    static class NumberInput extends JComponent {
        Runnable onSubmit;
        private final JTextField field;
        NumberInput() {
            setLayout(null);
            field = new JTextField();
            field.setFont(mono(32, true));
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setBackground(new Color(0,0,0,0));
            field.setForeground(C_TEXT);
            field.setCaretColor(C_ACCENT);
            field.setBorder(BorderFactory.createEmptyBorder());
            field.setOpaque(false);
            field.addActionListener(e -> { if (onSubmit != null) onSubmit.run(); });
            add(field);
        }
        @Override public void setBounds(int x, int y, int w, int h) {
            super.setBounds(x, y, w, h);
            field.setBounds(12, 8, w-24, h-16);
        }
        String getText()  { return field.getText(); }
        void clear()      { field.setText(""); field.requestFocus(); }
        void focus()      { field.requestFocus(); }
        void setCaretColor(Color c) { field.setCaretColor(c); }
        @Override protected void paintComponent(Graphics g2) {
            Graphics2D g = (Graphics2D) g2;
            applyQualityRendering(g);
            g.setColor(C_CARD);
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
            g.setColor(C_BORDER); g.setStroke(new BasicStroke(1.5f));
            g.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth()-1.5f, getHeight()-1.5f, 16, 16));
        }
    }

    static class NeonButton extends JButton {
        boolean hover = false;
        NeonButton(String t) {
            super(t);
            setFont(mono(14, true));
            setForeground(Color.WHITE);
            setFocusPainted(false); setBorderPainted(false); setContentAreaFilled(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover=true;  repaint(); }
                public void mouseExited (MouseEvent e) { hover=false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g2) {
            Graphics2D g = (Graphics2D) g2;
            applyQualityRendering(g);
            Color a = hover ? new Color(0x8CF0FF) : C_ACCENT;
            Color b = hover ? new Color(0xC48AFF) : C_ACCENT2;
            g.setPaint(new GradientPaint(0, 0, a, getWidth(), 0, b));
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            if (hover) {
                for (int i=6; i>=1; i--) {
                    g.setColor(new Color(110,231,247,10));
                    g.setStroke(new BasicStroke(i*2f));
                    g.draw(new RoundRectangle2D.Float(0,0,getWidth(),getHeight(),14,14));
                }
            }
            g.dispose();
            super.paintComponent(g2);
        }
    }

    static class HistoryList extends JComponent {
        JScrollPane scroll;
        JPanel inner;
        HistoryList() {
            setLayout(new BorderLayout());
            inner = new JPanel();
            inner.setBackground(C_CARD);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
            inner.setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
            scroll = new JScrollPane(inner,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBackground(C_CARD);
            scroll.getViewport().setBackground(C_CARD);
            scroll.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
            add(scroll);
        }
        @Override public void setBounds(int x,int y,int w,int h) {
            super.setBounds(x,y,w,h);
            if (scroll != null) scroll.setBounds(0,0,w,h);
        }
        void addEntry(int attempt, int guess, int cmp, int player) {
            JPanel row = new JPanel(new BorderLayout(8,0)) {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D)g;
                    applyQualityRendering(g2);
                    g2.setColor(C_BORDER);
                    g2.drawLine(0,getHeight()-1,getWidth(),getHeight()-1);
                }
            };
            row.setBackground(C_CARD);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            row.setBorder(BorderFactory.createEmptyBorder(6,0,6,0));

            String prefix = player > 0 ? "P" + player : "#" + attempt;
            JLabel numLbl = new JLabel(prefix + "   " + String.format("%3d",guess));
            numLbl.setFont(mono(13,true));

            if (cmp == 0) numLbl.setForeground(C_SUCCESS);
            else if (player > 0) numLbl.setForeground(getPlayerColor(player));
            else numLbl.setForeground(C_TEXT);

            String hint = cmp==0 ? "✓  correct" : cmp<0 ? "↑  too low" : "↓  too high";
            Color hcol  = cmp==0 ? C_SUCCESS    : cmp<0 ? C_ACCENT     : C_ACCENT2;
            JLabel h2 = new JLabel(hint, SwingConstants.RIGHT);
            h2.setFont(mono(12)); h2.setForeground(hcol);

            row.add(numLbl, BorderLayout.WEST);
            row.add(h2,     BorderLayout.EAST);
            inner.add(row);
            inner.revalidate();
            SwingUtilities.invokeLater(() -> {
                JScrollBar sb = scroll.getVerticalScrollBar();
                sb.setValue(sb.getMaximum());
            });
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    // Centralized rendering hints for maximum visual sharpness
    static void applyQualityRendering(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    static Font mono(int s)          { return new Font("Monospaced", Font.PLAIN, s); }
    static Font mono(int s, boolean b){ return new Font("Monospaced", b?Font.BOLD:Font.PLAIN, s); }

    static JPanel dialogPanel() {
        JPanel p = new JPanel();
        p.setBackground(C_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(14,18,14,18));
        return p;
    }

    static void addDialogTitle(JPanel p, String text) {
        JLabel l = new JLabel(text);
        l.setFont(mono(15,true)); l.setForeground(C_ACCENT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0,0,12,0));
        p.add(l);
    }

    static JRadioButton radioBtn(String text, boolean sel) {
        JRadioButton r = new JRadioButton(text, sel);
        r.setFont(mono(13)); r.setForeground(C_TEXT); r.setBackground(C_CARD);
        r.setFocusPainted(false); r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    static JLabel statLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(mono(12,true)); l.setForeground(C_MUTED);
        return l;
    }

    static JButton iconButton(String sym, Color color) {
        JButton b = new JButton(sym) {
            boolean h = false;
            { addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e){h=true; repaint();}
                public void mouseExited (MouseEvent e){h=false;repaint();}
            });}
            @Override protected void paintComponent(Graphics g2) {
                Graphics2D g=(Graphics2D)g2;
                applyQualityRendering(g);
                if(h){ g.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),40));
                    g.fillOval(0,0,getWidth(),getHeight()); }
                g.setColor(h?color:C_MUTED); g.setFont(getFont());
                FontMetrics fm=g.getFontMetrics();
                g.drawString(getText(),(getWidth()-fm.stringWidth(getText()))/2,
                        (getHeight()+fm.getAscent()-fm.getDescent())/2);
            }
        };
        b.setFont(mono(14,true));
        b.setPreferredSize(new Dimension(28,28));
        b.setFocusPainted(false); b.setBorderPainted(false); b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static void styleSpinner(JSpinner s) {
        s.setFont(mono(13));
        JTextField tf = ((JSpinner.DefaultEditor)s.getEditor()).getTextField();
        tf.setBackground(C_DIM); tf.setForeground(C_TEXT); tf.setCaretColor(C_ACCENT);
        tf.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));
    }

    static Component vgap(int h) { return Box.createRigidArea(new Dimension(0,h)); }

    static void delay(int ms, Runnable r) {
        Timer t = new Timer(ms, e -> r.run()); t.setRepeats(false); t.start();
    }

    public static void main(String[] args) {
        // System properties for maximum visual sharpness on modern displays
        System.setProperty("sun.java2d.dpiaware", "true");
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(Main::new);
    }
}