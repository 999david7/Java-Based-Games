import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * Connect Four — single-file Java Swing implementation
 *
 * Run:  javac Main.java && java Main
 *
 * Features:
 *  - Player vs Player and Player vs AI (Minimax + alpha-beta, 3 difficulty levels)
 *  - Smooth disc-drop animation with gravity + bounce easing
 *  - Hover ghost disc showing where piece will land
 *  - Win detection (H / V / diagonal) with pulsing highlight
 *  - Score tracking, draw detection, restart button
 *  - Dark "deep space" theme with radial-gradient 3-D discs
 */
public class Main extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main window = new Main();
            window.setVisible(true);
        });
    }

    public Main() {
        super("Connect 4");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(560, 620));

        GamePanel panel = new GamePanel();
        add(panel);
        pack();
        setLocationRelativeTo(null);
    }

    // =========================================================================
    // CONSTANTS
    // =========================================================================

    static final int COLS = 7, ROWS = 6, WIN_LEN = 4;
    static final int CELL  = 78;   // cell size in pixels
    static final int PAD   = 10;   // padding inside board frame
    static final int RADIUS = 30;  // disc radius

    static final Color BG         = new Color(6,   6,  26);
    static final Color BOARD_CLR  = new Color(18,  18, 68);
    static final Color HOLE_CLR   = new Color(5,   5,  20);
    static final Color P1_COLOR   = new Color(255, 71, 87);   // coral red
    static final Color P2_COLOR   = new Color(255, 215, 0);   // gold
    static final Color TEXT_DIM   = new Color(150, 150, 200);
    static final Color GOLD       = new Color(255, 215, 0);

    // =========================================================================
    // MODEL — board, win detection, AI
    // =========================================================================

    static class Board {
        final int[][] grid = new int[ROWS][COLS];
        final int[]   heights = new int[COLS];   // pieces in each column
        int totalPieces;
        WinResult lastWin;

        Board() {}

        Board copy() {
            Board b = new Board();
            for (int r = 0; r < ROWS; r++) System.arraycopy(grid[r], 0, b.grid[r], 0, COLS);
            System.arraycopy(heights, 0, b.heights, 0, COLS);
            b.totalPieces = totalPieces;
            b.lastWin = lastWin;
            return b;
        }

        /** Returns row where piece landed, or -1 if column full. */
        int drop(int col, int player) {
            if (col < 0 || col >= COLS || heights[col] >= ROWS) return -1;
            int row = ROWS - 1 - heights[col];
            grid[row][col] = player;
            heights[col]++;
            totalPieces++;
            lastWin = checkWin(row, col, player);
            return row;
        }

        boolean canDrop(int col) { return col >= 0 && col < COLS && heights[col] < ROWS; }

        boolean isFull() { return totalPieces >= ROWS * COLS; }

        private WinResult checkWin(int row, int col, int player) {
            int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
            for (int[] d : dirs) {
                List<int[]> line = new ArrayList<>();
                line.add(new int[]{row, col});
                for (int s : new int[]{1, -1})
                    for (int i = 1; i < WIN_LEN; i++) {
                        int r = row + d[0]*i*s, c = col + d[1]*i*s;
                        if (r<0||r>=ROWS||c<0||c>=COLS||grid[r][c]!=player) break;
                        line.add(new int[]{r, c});
                    }
                if (line.size() >= WIN_LEN) return new WinResult(player, line);
            }
            return null;
        }

        void reset() {
            for (int[] row : grid) Arrays.fill(row, 0);
            Arrays.fill(heights, 0);
            totalPieces = 0;
            lastWin = null;
        }

        List<Integer> availableCols() {
            List<Integer> a = new ArrayList<>();
            for (int c = 0; c < COLS; c++) if (canDrop(c)) a.add(c);
            return a;
        }
    }

    static class WinResult {
        final int player;
        final List<int[]> cells;
        WinResult(int player, List<int[]> cells) {
            this.player = player;
            this.cells  = List.copyOf(cells);
        }
    }

    // ── Minimax AI ─────────────────────────────────────────────────────────────

    static class AI {
        enum Difficulty { EASY(3), MEDIUM(5), HARD(7);
            final int depth;
            Difficulty(int d) { depth = d; }
        }

        private final int depth;
        private static final int[] COL_ORDER = {3,2,4,1,5,0,6};

        AI(Difficulty d) { this.depth = d.depth; }

        int bestMove(Board board) {
            int best = -1, bestScore = Integer.MIN_VALUE;
            for (int col : COL_ORDER) {
                if (!board.canDrop(col)) continue;
                Board copy = board.copy();
                copy.drop(col, 2);
                if (copy.lastWin != null) return col;   // immediate win
                int score = minimax(copy, depth - 1, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
                if (score > bestScore) { bestScore = score; best = col; }
            }
            return best;
        }

        private int minimax(Board b, int depth, int alpha, int beta, boolean max) {
            if (b.lastWin != null) return b.lastWin.player == 2 ? 1000000+depth : -(1000000+depth);
            if (b.isFull() || depth == 0) return eval(b);
            if (max) {
                int v = Integer.MIN_VALUE;
                for (int col : COL_ORDER) {
                    if (!b.canDrop(col)) continue;
                    Board c = b.copy(); c.drop(col, 2);
                    v = Math.max(v, minimax(c, depth-1, alpha, beta, false));
                    alpha = Math.max(alpha, v);
                    if (beta <= alpha) break;
                }
                return v;
            } else {
                int v = Integer.MAX_VALUE;
                for (int col : COL_ORDER) {
                    if (!b.canDrop(col)) continue;
                    Board c = b.copy(); c.drop(col, 1);
                    v = Math.min(v, minimax(c, depth-1, alpha, beta, true));
                    beta = Math.min(beta, v);
                    if (beta <= alpha) break;
                }
                return v;
            }
        }

        private int eval(Board b) {
            int score = 0;
            // Center column bonus
            for (int r = 0; r < ROWS; r++)
                if (b.grid[r][COLS/2] == 2) score += 3;
            // Windows
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c <= COLS-4; c++) score += window(b, r, c, 0, 1);
            for (int c = 0; c < COLS; c++)
                for (int r = 0; r <= ROWS-4; r++) score += window(b, r, c, 1, 0);
            for (int r = 0; r <= ROWS-4; r++)
                for (int c = 0; c <= COLS-4; c++) score += window(b, r, c, 1, 1);
            for (int r = 3; r < ROWS; r++)
                for (int c = 0; c <= COLS-4; c++) score += window(b, r, c, -1, 1);
            return score;
        }

        private int window(Board b, int r, int c, int dr, int dc) {
            int ai=0, hu=0;
            for (int i=0; i<4; i++) {
                int v = b.grid[r+dr*i][c+dc*i];
                if (v==2) ai++; else if (v==1) hu++;
            }
            if (ai>0 && hu>0) return 0;
            if (ai==4) return 100; if (ai==3) return 5; if (ai==2) return 2;
            if (hu==3) return -4; if (hu==4) return -100;
            return 0;
        }
    }

    // =========================================================================
    // ANIMATION — one disc falling
    // =========================================================================

    static class DropAnim {
        final int row, col, player;
        final long startMs;
        final double totalDist;   // pixels to fall
        static final long DURATION_MS = 440;

        DropAnim(int row, int col, int player) {
            this.row = row; this.col = col; this.player = player;
            this.startMs   = System.currentTimeMillis();
            this.totalDist = (row + 1) * CELL;  // distance from top of board
        }

        /** Returns current Y offset (0 = resting position). Uses gravity ease + bounce. */
        double currentY() {
            double t = Math.min(1.0, (System.currentTimeMillis() - startMs) / (double) DURATION_MS);
            double eased = curve(t);
            // eased goes 0→1; at 1 disc is at rest (offset = 0 from top)
            double fromTop = eased * totalDist;
            return fromTop - totalDist;  // negative = above rest, 0 = rest
        }

        boolean finished() {
            return (System.currentTimeMillis() - startMs) >= DURATION_MS;
        }

        private double curve(double t) {
            if (t < 0.85) {
                double p = t / 0.85;
                return p * p * p * 0.96;    // cubic ease-in (gravity)
            }
            double p = (t - 0.85) / 0.15;
            return 0.96 + Math.sin(p * Math.PI) * 0.04;  // tiny bounce
        }
    }

    // =========================================================================
    // GAME PANEL — single Swing component that handles everything
    // =========================================================================

    static class GamePanel extends JPanel implements MouseListener, MouseMotionListener {

        // State
        Board board = new Board();
        int currentPlayer = 1;
        boolean gameOver = false;
        boolean isDraw   = false;
        int[] scores     = {0, 0};

        // Mode & AI
        boolean vsAI = false;
        AI.Difficulty difficulty = AI.Difficulty.MEDIUM;
        AI ai = new AI(difficulty);
        boolean aiThinking = false;

        // Hover
        int hoveredCol = -1;

        // Animation
        DropAnim activeAnim = null;
        Timer animTimer;

        // Win pulse
        float winPulse = 0f;
        Timer winTimer;

        // Layout (computed in paintComponent)
        int boardX, boardY, boardW, boardH;

        GamePanel() {
            setBackground(BG);
            addMouseListener(this);
            addMouseMotionListener(this);

            // ~60fps repaint timer for animations
            animTimer = new Timer(16, e -> repaint());
            animTimer.start();

            // Win pulse timer
            winTimer = new Timer(30, e -> {
                winPulse += 0.06f;
                if (winPulse > 2 * Math.PI) winPulse -= (float)(2 * Math.PI);
            });
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(COLS * CELL + PAD*2 + 40, ROWS * CELL + PAD*2 + 210);
        }

        // ── Painting ──────────────────────────────────────────────────────────

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int W = getWidth(), H = getHeight();

            // Background gradient
            GradientPaint bgGrad = new GradientPaint(0, 0, new Color(6,6,26), 0, H, new Color(8,8,20));
            g.setPaint(bgGrad);
            g.fillRect(0, 0, W, H);

            // Layout
            boardW = COLS * CELL + PAD * 2;
            boardH = ROWS * CELL + PAD * 2;
            boardX = (W - boardW) / 2;
            boardY = 130;

            drawHeader(g, W);
            drawHoverRow(g);
            drawBoard(g);
            drawControls(g, W, H);

            if (gameOver) drawOverlay(g, W, H);
            if (aiThinking) drawThinkingBadge(g, W);

            // Advance animation
            if (activeAnim != null && activeAnim.finished()) {
                activeAnim = null;
                if (!gameOver) onAnimDone();
            }
        }

        private void drawHeader(Graphics2D g, int W) {
            // Title
            g.setFont(new Font("Serif", Font.BOLD, 34));
            FontMetrics fm = g.getFontMetrics();

            String t1 = "CONNECT ", t2 = "FOUR";
            int totalW = fm.stringWidth(t1 + t2);
            int tx = (W - totalW) / 2;
            int ty = 50;

            g.setColor(Color.WHITE);
            g.drawString(t1, tx, ty);
            g.setColor(GOLD);
            g.drawString(t2, tx + fm.stringWidth(t1), ty);

            // Turn indicator dot + label
            if (!gameOver) {
                Color pc = currentPlayer == 1 ? P1_COLOR : P2_COLOR;
                String name = playerName(currentPlayer);

                // Glow dot
                int dx = W/2 - 70, dy = 75;
                drawGlowCircle(g, dx, dy, 9, pc, 14);

                g.setFont(new Font("Serif", Font.PLAIN, 15));
                g.setColor(blendColor(pc, TEXT_DIM, 0.35f));
                g.drawString(name + "'s Turn", dx + 16, dy + 5);
            }

            // Scores
            drawScoreCard(g, W/2 - 220, 62, "Player 1", scores[0], P1_COLOR);
            drawScoreCard(g, W/2 + 130, 62, "Player 2", scores[1], P2_COLOR);
        }

        private void drawScoreCard(Graphics2D g, int x, int y, String label, int score, Color color) {
            // Card background
            g.setColor(new Color(26, 26, 62, 160));
            g.fillRoundRect(x, y - 22, 88, 42, 10, 10);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 80));
            g.drawRoundRect(x, y - 22, 88, 42, 10, 10);

            g.setFont(new Font("Serif", Font.PLAIN, 11));
            g.setColor(TEXT_DIM);
            g.drawString(label, x + 8, y - 6);

            g.setFont(new Font("Serif", Font.BOLD, 26));
            g.setColor(color);
            g.drawString(String.valueOf(score), x + 8, y + 16);
        }

        private void drawHoverRow(Graphics2D g) {
            if (hoveredCol < 0 || gameOver || aiThinking || activeAnim != null) return;
            if (!board.canDrop(hoveredCol)) return;

            Color base = currentPlayer == 1 ? P1_COLOR : P2_COLOR;
            Color ghost = new Color(base.getRed(), base.getGreen(), base.getBlue(), 100);

            int cx = boardX + PAD + hoveredCol * CELL + CELL / 2;
            int cy = boardY - CELL / 2;

            drawDisc(g, cx, cy, RADIUS - 2, ghost, false);
        }

        private void drawBoard(Graphics2D g) {
            // Board shadow
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRoundRect(boardX + 4, boardY + 6, boardW, boardH, 18, 18);

            // Board body
            g.setColor(BOARD_CLR);
            g.fillRoundRect(boardX, boardY, boardW, boardH, 18, 18);

            // Subtle top-edge highlight
            g.setColor(new Color(255, 255, 255, 18));
            g.fillRoundRect(boardX, boardY, boardW, 4, 18, 18);

            // Draw each cell
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int cx = boardX + PAD + c * CELL + CELL / 2;
                    int cy = boardY + PAD + r * CELL + CELL / 2;

                    drawHole(g, cx, cy);

                    int player = board.grid[r][c];
                    if (player != 0) {
                        // Is this cell currently animating?
                        if (activeAnim != null && activeAnim.row == r && activeAnim.col == c) {
                            double offset = activeAnim.currentY();
                            Color color = activeAnim.player == 1 ? P1_COLOR : P2_COLOR;
                            boolean dimmed = isDimmedCell(r, c);
                            drawDisc(g, cx, (int)(cy + offset), RADIUS, color, dimmed);
                        } else {
                            Color color = player == 1 ? P1_COLOR : P2_COLOR;
                            boolean dimmed = isDimmedCell(r, c);
                            drawDisc(g, cx, cy, RADIUS, color, dimmed);
                            if (isWinCell(r, c)) drawWinRing(g, cx, cy);
                        }
                    }
                }
            }
        }

        private boolean isDimmedCell(int r, int c) {
            if (board.lastWin == null || !gameOver) return false;
            for (int[] wc : board.lastWin.cells)
                if (wc[0] == r && wc[1] == c) return false;
            return true;
        }

        private boolean isWinCell(int r, int c) {
            if (board.lastWin == null) return false;
            for (int[] wc : board.lastWin.cells)
                if (wc[0] == r && wc[1] == c) return true;
            return false;
        }

        private void drawHole(Graphics2D g, int cx, int cy) {
            // Recessed hole effect
            g.setColor(HOLE_CLR);
            g.fillOval(cx - RADIUS, cy - RADIUS, RADIUS * 2, RADIUS * 2);

            // Inner shadow (top-left dark arc)
            g.setColor(new Color(0, 0, 0, 80));
            g.setStroke(new BasicStroke(4));
            g.drawArc(cx - RADIUS + 3, cy - RADIUS + 3, RADIUS * 2 - 6, RADIUS * 2 - 6, 45, 180);
            g.setStroke(new BasicStroke(1));
        }

        private void drawDisc(Graphics2D g, int cx, int cy, int r, Color base, boolean dimmed) {
            // Radial gradient gives 3-D sphere appearance
            Color light  = blend(base, Color.WHITE, 0.28f);
            Color dark   = base.darker().darker();

            if (dimmed) {
                base  = new Color(base.getRed(),  base.getGreen(),  base.getBlue(),  100);
                light = new Color(light.getRed(), light.getGreen(), light.getBlue(), 100);
                dark  = new Color(dark.getRed(),  dark.getGreen(),  dark.getBlue(),  100);
            }

            // Clip to disc shape
            Ellipse2D disc = new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2);
            Shape old = g.getClip();
            g.clip(disc);

            // Radial gradient center-offset top-left for sphere look
            int gx = cx - r/3, gy = cy - r/3;
            float[] fractions = {0f, 0.55f, 1f};
            Color[] colors    = {light, base, dark};
            RadialGradientPaint grad = new RadialGradientPaint(
                    new Point2D.Float(gx, gy), r * 1.3f, fractions, colors);
            g.setPaint(grad);
            g.fillOval(cx - r, cy - r, r * 2, r * 2);

            g.setClip(old);

            // Glow ring
            if (!dimmed) {
                Color glow = new Color(base.getRed(), base.getGreen(), base.getBlue(), 55);
                g.setColor(glow);
                g.setStroke(new BasicStroke(4));
                g.drawOval(cx - r - 1, cy - r - 1, r * 2 + 2, r * 2 + 2);
                g.setStroke(new BasicStroke(1));
            }

            // Shimmer highlight (top-left gloss)
            if (!dimmed) {
                int sx = cx - r/3, sy = cy - r/3;
                int sr = r / 3;
                Color shimmer = new Color(255, 255, 255, 55);
                g.setColor(shimmer);
                g.fillOval(sx - sr, sy - sr, sr * 2, sr * 2);
            }
        }

        private void drawWinRing(Graphics2D g, int cx, int cy) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(winPulse);
            int alpha = (int)(80 + 175 * pulse);
            float width = 2.5f + 2.5f * pulse;

            g.setColor(new Color(255, 255, 255, alpha));
            g.setStroke(new BasicStroke(width));
            g.drawOval(cx - RADIUS + 2, cy - RADIUS + 2, (RADIUS - 2) * 2, (RADIUS - 2) * 2);
            g.setStroke(new BasicStroke(1));
        }

        private void drawControls(Graphics2D g, int W, int H) {
            int y = boardY + boardH + 22;

            // Mode buttons
            drawToggleBtn(g, W/2 - 170, y, 80, 30, "PvP",   !vsAI);
            drawToggleBtn(g, W/2 -  85, y, 80, 30, "vs AI",  vsAI);

            // Difficulty buttons (only active in AI mode)
            float a = vsAI ? 1f : 0.35f;
            drawDiffBtn(g, W/2 + 10,  y, 55, 30, "Easy",   difficulty == AI.Difficulty.EASY,   a);
            drawDiffBtn(g, W/2 + 70,  y, 65, 30, "Med",    difficulty == AI.Difficulty.MEDIUM, a);
            drawDiffBtn(g, W/2 + 140, y, 55, 30, "Hard",   difficulty == AI.Difficulty.HARD,   a);

            // New Game button
            int bw = 110, bh = 34;
            int bx = (W - bw) / 2, by = y + 44;
            drawPrimaryBtn(g, bx, by, bw, bh, "New Game");
        }

        private void drawToggleBtn(Graphics2D g, int x, int y, int w, int h, String text, boolean active) {
            Color bg  = active ? new Color(42, 42, 110)  : new Color(26, 26, 62);
            Color bdr = active ? GOLD                    : new Color(42, 42, 94);
            Color fg  = active ? GOLD                    : TEXT_DIM;
            drawStyledBtn(g, x, y, w, h, text, bg, bdr, fg);
        }

        private void drawDiffBtn(Graphics2D g, int x, int y, int w, int h, String text, boolean active, float alpha) {
            Color bg  = active ? new Color(42, 42, 110)  : new Color(26, 26, 62);
            Color bdr = active ? GOLD                    : new Color(42, 42, 94);
            Color fg  = active ? GOLD                    : TEXT_DIM;
            // Apply alpha
            bg  = alphaColor(bg, alpha);
            bdr = alphaColor(bdr, alpha);
            fg  = alphaColor(fg, alpha);
            drawStyledBtn(g, x, y, w, h, text, bg, bdr, fg);
        }

        private void drawPrimaryBtn(Graphics2D g, int x, int y, int w, int h, String text) {
            GradientPaint gp = new GradientPaint(x, y, new Color(255,215,0), x, y+h, new Color(255,160,0));
            g.setPaint(gp);
            g.fillRoundRect(x, y, w, h, 8, 8);
            g.setFont(new Font("Serif", Font.BOLD, 14));
            g.setColor(new Color(6, 6, 26));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, x + (w - fm.stringWidth(text))/2, y + (h + fm.getAscent() - fm.getDescent())/2);
        }

        private void drawStyledBtn(Graphics2D g, int x, int y, int w, int h,
                                   String text, Color bg, Color border, Color fg) {
            g.setColor(bg);
            g.fillRoundRect(x, y, w, h, 7, 7);
            g.setColor(border);
            g.drawRoundRect(x, y, w, h, 7, 7);
            g.setFont(new Font("Serif", Font.PLAIN, 13));
            g.setColor(fg);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, x + (w - fm.stringWidth(text))/2, y + (h + fm.getAscent() - fm.getDescent())/2);
        }

        private void drawOverlay(Graphics2D g, int W, int H) {
            // Dim background
            g.setColor(new Color(0, 0, 0, 165));
            g.fillRect(0, 0, W, H);

            // Card
            int cw = 320, ch = 160;
            int cx = (W - cw) / 2, cy = (H - ch) / 2 - 20;

            g.setColor(new Color(6, 6, 26, 230));
            g.fillRoundRect(cx, cy, cw, ch, 20, 20);
            g.setColor(new Color(255, 215, 0, 90));
            g.drawRoundRect(cx, cy, cw, ch, 20, 20);

            // Title
            String title = isDraw ? "It's a Draw!" :
                    playerName(board.lastWin.player) + " Wins!";
            Color tc = isDraw ? TEXT_DIM :
                    (board.lastWin.player == 1 ? P1_COLOR : P2_COLOR);

            g.setFont(new Font("Serif", Font.BOLD, 36));
            g.setColor(tc);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, cx + (cw - fm.stringWidth(title))/2, cy + 58);

            // Subtitle
            g.setFont(new Font("Serif", Font.PLAIN, 15));
            g.setColor(TEXT_DIM);
            String sub = isDraw ? "Nobody connected four." : "Connected four in a row!";
            fm = g.getFontMetrics();
            g.drawString(sub, cx + (cw - fm.stringWidth(sub))/2, cy + 88);

            // Restart button
            drawPrimaryBtn(g, cx + (cw - 130)/2, cy + 108, 130, 36, "Play Again");
        }

        private void drawThinkingBadge(Graphics2D g, int W) {
            String msg = "AI is thinking…";
            g.setFont(new Font("Serif", Font.PLAIN, 13));
            FontMetrics fm = g.getFontMetrics();
            int bw = fm.stringWidth(msg) + 32, bh = 28;
            int bx = (W - bw) / 2, by = boardY + boardH + 8;
            g.setColor(new Color(13, 13, 43, 220));
            g.fillRoundRect(bx, by, bw, bh, 8, 8);
            g.setColor(new Color(42, 42, 94));
            g.drawRoundRect(bx, by, bw, bh, 8, 8);
            g.setColor(TEXT_DIM);
            g.drawString(msg, bx + 16, by + 19);
        }

        private void drawGlowCircle(Graphics2D g, int cx, int cy, int r, Color color, int glowR) {
            // Soft radial glow
            for (int i = glowR; i > r; i--) {
                int a = (int)(30 * (1 - (float)(i - r) / (glowR - r)));
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), a));
                g.fillOval(cx - i, cy - i, i*2, i*2);
            }
            g.setColor(color);
            g.fillOval(cx - r, cy - r, r*2, r*2);
        }

        // ── Input handling ────────────────────────────────────────────────────

        @Override
        public void mouseClicked(MouseEvent e) {
            int x = e.getX(), y = e.getY();

            if (gameOver) {
                // Check "Play Again" button
                int W = getWidth(), H = getHeight();
                int cw = 320, ch = 160;
                int cx = (W - cw) / 2, cy = (H - ch) / 2 - 20;
                int bx = cx + (cw - 130)/2, by = cy + 108;
                if (x >= bx && x <= bx+130 && y >= by && y <= by+36) {
                    startNewGame(); return;
                }
                return;
            }

            int W = getWidth();
            int ctrlY = boardY + boardH + 22;

            // New Game button
            int bw=110, bh=34, bx=(W-bw)/2, by=ctrlY+44;
            if (x>=bx && x<=bx+bw && y>=by && y<=by+bh) { startNewGame(); return; }

            // PvP button
            if (x>=W/2-170 && x<=W/2-90 && y>=ctrlY && y<=ctrlY+30) {
                vsAI = false; repaint(); return;
            }
            // vs AI button
            if (x>=W/2-85 && x<=W/2-5 && y>=ctrlY && y<=ctrlY+30) {
                vsAI = true; repaint(); return;
            }

            // Difficulty buttons (only if vsAI)
            if (vsAI) {
                if (x>=W/2+10 && x<=W/2+65  && y>=ctrlY && y<=ctrlY+30) { setDifficulty(AI.Difficulty.EASY);   return; }
                if (x>=W/2+70 && x<=W/2+135 && y>=ctrlY && y<=ctrlY+30) { setDifficulty(AI.Difficulty.MEDIUM); return; }
                if (x>=W/2+140&& x<=W/2+195 && y>=ctrlY && y<=ctrlY+30) { setDifficulty(AI.Difficulty.HARD);   return; }
            }

            // Board column click
            if (activeAnim != null || aiThinking) return;
            if (vsAI && currentPlayer == 2) return;

            int col = xToCol(x);
            if (col >= 0) handleClick(col);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            int col = xToCol(e.getX());
            if (col != hoveredCol) { hoveredCol = col; repaint(); }
        }

        @Override public void mousePressed(MouseEvent e)  {}
        @Override public void mouseReleased(MouseEvent e) {}
        @Override public void mouseEntered(MouseEvent e)  {}
        @Override public void mouseExited(MouseEvent e)   { hoveredCol = -1; repaint(); }
        @Override public void mouseDragged(MouseEvent e)  { mouseMoved(e); }

        // ── Game logic ────────────────────────────────────────────────────────

        private void handleClick(int col) {
            if (gameOver || !board.canDrop(col)) return;

            int row = board.drop(col, currentPlayer);
            if (row < 0) return;

            // Start drop animation; result is handled in onAnimDone()
            activeAnim = new DropAnim(row, col, currentPlayer);
        }

        /** Called when the drop animation completes. */
        private void onAnimDone() {
            // Check win / draw
            if (board.lastWin != null) {
                gameOver = true;
                scores[board.lastWin.player - 1]++;
                winTimer.start();
                repaint();
                return;
            }
            if (board.isFull()) {
                gameOver = true;
                isDraw   = true;
                repaint();
                return;
            }

            // Switch player
            currentPlayer = currentPlayer == 1 ? 2 : 1;
            repaint();

            // Trigger AI if needed
            if (vsAI && currentPlayer == 2) {
                aiThinking = true;
                repaint();
                new Thread(() -> {
                    try { Thread.sleep(320); } catch (InterruptedException ignored) {}
                    int bestCol = ai.bestMove(board);
                    SwingUtilities.invokeLater(() -> {
                        aiThinking = false;
                        handleClick(bestCol);
                    });
                }, "AI-thread").start();
            }
        }

        private void startNewGame() {
            board.reset();
            currentPlayer = 1;
            gameOver  = false;
            isDraw    = false;
            activeAnim = null;
            aiThinking = false;
            winTimer.stop();
            winPulse = 0;
            repaint();
        }

        private void setDifficulty(AI.Difficulty d) {
            difficulty = d;
            ai = new AI(d);
            repaint();
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        /** Converts mouse X to board column, or -1 if outside the board. */
        private int xToCol(int mouseX) {
            int relX = mouseX - boardX - PAD;
            int col  = relX / CELL;
            if (relX < 0 || col >= COLS) return -1;
            return col;
        }

        private String playerName(int player) {
            if (vsAI) return player == 1 ? "You" : "AI";
            return "Player " + player;
        }

        // ── Color utilities ───────────────────────────────────────────────────

        static Color blend(Color a, Color b, float t) {
            return new Color(
                    (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
                    (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
        }

        static Color blendColor(Color a, Color b, float t) { return blend(a, b, t); }

        static Color alphaColor(Color c, float alpha) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(),
                    Math.min(255, (int)(c.getAlpha() * alpha)));
        }
    }
}