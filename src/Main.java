/*
 * Chess — single file, pure Java Swing (no external dependencies)
 *
 * COMPILE:  javac Main.java
 * RUN:      java Main
 *
 * Requires: Java 11 or higher (uses only standard java.* / javax.swing.* APIs)
 *
 * FEATURES:
 *   Full chess rules: legal moves, check/checkmate/stalemate, castling,
 *   en passant, pawn promotion (auto-queen or dialog choice).
 *   Highlighted selected piece, legal moves, last move.
 *   Two-player local play. Restart button. Status bar.
 */

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class Main extends JFrame {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main w = new Main();
            w.setVisible(true);
        });
    }

    public Main() {
        super("Chess");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(true);

        ChessPanel panel = new ChessPanel();
        add(panel);
        pack();
        setMinimumSize(new Dimension(560, 640));
        setLocationRelativeTo(null);
    }

    // =========================================================================
    //  CONSTANTS
    // =========================================================================

    static final int SQ = 72;                        // square size px
    static final int BOARD_PX = SQ * 8;             // 576

    // Palette — warm parchment / dark walnut aesthetic
    static final Color LIGHT_SQ   = new Color(240, 217, 181);
    static final Color DARK_SQ    = new Color(181, 136,  99);
    static final Color SEL_CLR    = new Color( 20, 160,  80, 160);
    static final Color MOVE_CLR   = new Color( 20, 160,  80, 100);
    static final Color LAST_CLR   = new Color(205, 210,  56, 130);
    static final Color CHECK_CLR  = new Color(220,  40,  40, 170);
    static final Color BG         = new Color( 40,  30,  22);
    static final Color PANEL_BG   = new Color( 50,  38,  26);
    static final Color TEXT_CLR   = new Color(230, 210, 180);
    static final Color ACCENT     = new Color(212, 175,  55);   // gold

    // =========================================================================
    //  PIECE TYPES & COLORS
    // =========================================================================

    enum PType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }
    enum PColor { WHITE, BLACK }

    // =========================================================================
    //  PIECE — holds type, color, has-moved flag
    // =========================================================================

    static class Piece {
        PType  type;
        PColor color;
        boolean hasMoved;

        Piece(PType t, PColor c) { type = t; color = c; }

        Piece copy() {
            Piece p = new Piece(type, color);
            p.hasMoved = hasMoved;
            return p;
        }

        // Unicode glyphs
        String glyph() {
            return switch (type) {
                case KING   -> color == PColor.WHITE ? "♔" : "♚";
                case QUEEN  -> color == PColor.WHITE ? "♕" : "♛";
                case ROOK   -> color == PColor.WHITE ? "♖" : "♜";
                case BISHOP -> color == PColor.WHITE ? "♗" : "♝";
                case KNIGHT -> color == PColor.WHITE ? "♘" : "♞";
                case PAWN   -> color == PColor.WHITE ? "♙" : "♟";
            };
        }
    }

    // =========================================================================
    //  MOVE — from/to squares plus metadata
    // =========================================================================

    static class Move {
        int fr, fc, tr, tc;
        boolean isCastle, isEnPassant, isPromotion;
        PType  promoteTo;

        Move(int fr, int fc, int tr, int tc) {
            this.fr = fr; this.fc = fc; this.tr = tr; this.tc = tc;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Move m)) return false;
            return fr==m.fr && fc==m.fc && tr==m.tr && tc==m.tc;
        }
    }

    // =========================================================================
    //  BOARD — 8×8 grid + game state
    // =========================================================================

    static class Board {

        Piece[][] grid = new Piece[8][8];
        PColor    turn = PColor.WHITE;

        // En passant: the square a pawn just double-pushed to (or -1)
        int epRow = -1, epCol = -1;

        // Last move for highlighting
        Move lastMove;

        // Cached game-status
        enum Status { PLAYING, CHECK, CHECKMATE, STALEMATE }
        Status status = Status.PLAYING;

        Board() { setup(); }

        void setup() {
            grid = new Piece[8][8];
            turn = PColor.WHITE;
            epRow = epCol = -1;
            lastMove = null;
            status = Status.PLAYING;

            PType[] backRank = {
                    PType.ROOK, PType.KNIGHT, PType.BISHOP, PType.QUEEN,
                    PType.KING, PType.BISHOP, PType.KNIGHT, PType.ROOK
            };
            for (int c = 0; c < 8; c++) {
                grid[0][c] = new Piece(backRank[c], PColor.BLACK);
                grid[1][c] = new Piece(PType.PAWN,  PColor.BLACK);
                grid[6][c] = new Piece(PType.PAWN,  PColor.WHITE);
                grid[7][c] = new Piece(backRank[c], PColor.WHITE);
            }
        }

        /** Deep copy used for legality checking (does not preserve status). */
        Board copy() {
            Board b = new Board();
            b.grid = new Piece[8][8];
            for (int r = 0; r < 8; r++)
                for (int c = 0; c < 8; c++)
                    if (grid[r][c] != null) b.grid[r][c] = grid[r][c].copy();
            b.turn  = turn;
            b.epRow = epRow;
            b.epCol = epCol;
            b.lastMove = lastMove;
            return b;
        }

        Piece at(int r, int c) { return (r>=0&&r<8&&c>=0&&c<8) ? grid[r][c] : null; }

        boolean empty(int r, int c) { return at(r,c) == null; }

        boolean enemy(int r, int c, PColor me) {
            Piece p = at(r,c);
            return p != null && p.color != me;
        }

        // ── Apply a move (mutates this board) ─────────────────────────────────

        void applyMove(Move m) {
            Piece p = grid[m.fr][m.fc];
            grid[m.fr][m.fc] = null;

            // En passant capture
            if (m.isEnPassant) grid[m.fr][m.tc] = null;

            // Promotion
            if (m.isPromotion) {
                PType pt = (m.promoteTo != null) ? m.promoteTo : PType.QUEEN;
                grid[m.tr][m.tc] = new Piece(pt, p.color);
                grid[m.tr][m.tc].hasMoved = true;
            } else {
                grid[m.tr][m.tc] = p;
                p.hasMoved = true;
            }

            // Castling — move rook
            if (m.isCastle) {
                if (m.tc == 6) { // kingside
                    grid[m.tr][5] = grid[m.tr][7];
                    grid[m.tr][7] = null;
                    if (grid[m.tr][5] != null) grid[m.tr][5].hasMoved = true;
                } else {         // queenside
                    grid[m.tr][3] = grid[m.tr][0];
                    grid[m.tr][0] = null;
                    if (grid[m.tr][3] != null) grid[m.tr][3].hasMoved = true;
                }
            }

            // Set en-passant target
            epRow = epCol = -1;
            if (p.type == PType.PAWN && Math.abs(m.tr - m.fr) == 2) {
                epRow = (m.fr + m.tr) / 2;
                epCol = m.fc;
            }

            lastMove = m;
            turn = (turn == PColor.WHITE) ? PColor.BLACK : PColor.WHITE;
        }

        // ── Compute all pseudo-legal moves for a piece ────────────────────────

        List<Move> pseudoMoves(int r, int c) {
            Piece p = at(r, c);
            if (p == null) return List.of();
            List<Move> list = new ArrayList<>();

            switch (p.type) {
                case PAWN   -> pawnMoves(r, c, p.color, list);
                case KNIGHT -> knightMoves(r, c, p.color, list);
                case BISHOP -> slideMoves(r, c, p.color, list, new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}});
                case ROOK   -> slideMoves(r, c, p.color, list, new int[][]{{1,0},{-1,0},{0,1},{0,-1}});
                case QUEEN  -> { slideMoves(r, c, p.color, list, new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}});
                    slideMoves(r, c, p.color, list, new int[][]{{1,0},{-1,0},{0,1},{0,-1}}); }
                case KING   -> kingMoves(r, c, p.color, list);
            }
            return list;
        }

        private void pawnMoves(int r, int c, PColor col, List<Move> list) {
            int dir = (col == PColor.WHITE) ? -1 : 1;
            int startRow = (col == PColor.WHITE) ? 6 : 1;
            int promRow  = (col == PColor.WHITE) ? 0 : 7;

            // Forward one
            if (empty(r+dir, c)) {
                addPawnMove(r, c, r+dir, c, r+dir==promRow, list);
                // Forward two from start
                if (r == startRow && empty(r+2*dir, c))
                    list.add(new Move(r, c, r+2*dir, c));
            }
            // Diagonal captures
            for (int dc : new int[]{-1, 1}) {
                int tr = r+dir, tc = c+dc;
                if (tr>=0&&tr<8&&tc>=0&&tc<8) {
                    if (enemy(tr, tc, col))
                        addPawnMove(r, c, tr, tc, tr==promRow, list);
                    // En passant
                    if (tr == epRow && tc == epCol) {
                        Move m = new Move(r, c, tr, tc);
                        m.isEnPassant = true;
                        list.add(m);
                    }
                }
            }
        }

        private void addPawnMove(int fr, int fc, int tr, int tc, boolean promo, List<Move> list) {
            if (promo) {
                for (PType pt : new PType[]{PType.QUEEN, PType.ROOK, PType.BISHOP, PType.KNIGHT}) {
                    Move m = new Move(fr, fc, tr, tc);
                    m.isPromotion = true;
                    m.promoteTo   = pt;
                    list.add(m);
                }
            } else {
                list.add(new Move(fr, fc, tr, tc));
            }
        }

        private void knightMoves(int r, int c, PColor col, List<Move> list) {
            int[][] deltas = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
            for (int[] d : deltas) {
                int tr=r+d[0], tc=c+d[1];
                if (tr>=0&&tr<8&&tc>=0&&tc<8 && !sameColor(tr,tc,col))
                    list.add(new Move(r,c,tr,tc));
            }
        }

        private void slideMoves(int r, int c, PColor col, List<Move> list, int[][] dirs) {
            for (int[] d : dirs) {
                int tr=r+d[0], tc=c+d[1];
                while (tr>=0&&tr<8&&tc>=0&&tc<8) {
                    if (empty(tr,tc)) { list.add(new Move(r,c,tr,tc)); }
                    else {
                        if (enemy(tr,tc,col)) list.add(new Move(r,c,tr,tc));
                        break;
                    }
                    tr+=d[0]; tc+=d[1];
                }
            }
        }

        private void kingMoves(int r, int c, PColor col, List<Move> list) {
            for (int dr=-1; dr<=1; dr++)
                for (int dc=-1; dc<=1; dc++) {
                    if (dr==0&&dc==0) continue;
                    int tr=r+dr, tc=c+dc;
                    if (tr>=0&&tr<8&&tc>=0&&tc<8 && !sameColor(tr,tc,col))
                        list.add(new Move(r,c,tr,tc));
                }
            // Castling
            Piece king = at(r, c);
            if (king != null && !king.hasMoved && !isInCheck(col)) {
                // Kingside
                Piece rk = at(r, 7);
                if (rk != null && !rk.hasMoved && empty(r,5) && empty(r,6)
                        && !squareAttacked(r,5,col) && !squareAttacked(r,6,col)) {
                    Move m = new Move(r,c,r,6); m.isCastle = true; list.add(m);
                }
                // Queenside
                rk = at(r, 0);
                if (rk != null && !rk.hasMoved && empty(r,1) && empty(r,2) && empty(r,3)
                        && !squareAttacked(r,3,col) && !squareAttacked(r,2,col)) {
                    Move m = new Move(r,c,r,2); m.isCastle = true; list.add(m);
                }
            }
        }

        private boolean sameColor(int r, int c, PColor col) {
            Piece p = at(r,c);
            return p != null && p.color == col;
        }

        // ── Legal moves (filter those that leave king in check) ───────────────

        List<Move> legalMoves(int r, int c) {
            List<Move> pseudo = pseudoMoves(r, c);
            List<Move> legal  = new ArrayList<>();
            Piece p = at(r, c);
            if (p == null) return legal;

            for (Move m : pseudo) {
                Board sim = copy();
                sim.applyMove(m);
                // After apply, turn flipped — check for original color
                if (!sim.isInCheck(p.color)) legal.add(m);
            }
            return legal;
        }

        List<Move> allLegalMoves(PColor col) {
            List<Move> all = new ArrayList<>();
            for (int r=0; r<8; r++)
                for (int c=0; c<8; c++) {
                    Piece p = at(r,c);
                    if (p != null && p.color == col) all.addAll(legalMoves(r,c));
                }
            return all;
        }

        // ── Check detection ───────────────────────────────────────────────────

        boolean isInCheck(PColor col) {
            // Find king
            int kr=-1, kc=-1;
            outer:
            for (int r=0; r<8; r++)
                for (int c=0; c<8; c++) {
                    Piece p = at(r,c);
                    if (p != null && p.type == PType.KING && p.color == col) {
                        kr=r; kc=c; break outer;
                    }
                }
            if (kr < 0) return false;
            return squareAttacked(kr, kc, col);
        }

        /** Returns true if square (r,c) is attacked by any enemy of 'defender'. */
        boolean squareAttacked(int r, int c, PColor defender) {
            PColor attacker = (defender == PColor.WHITE) ? PColor.BLACK : PColor.WHITE;
            for (int ar=0; ar<8; ar++)
                for (int ac=0; ac<8; ac++) {
                    Piece p = at(ar,ac);
                    if (p == null || p.color != attacker) continue;
                    for (Move m : pseudoMoves(ar,ac))
                        if (m.tr==r && m.tc==c) return true;
                }
            return false;
        }

        // ── Compute status after a move ────────────────────────────────────────

        void updateStatus() {
            boolean inCheck  = isInCheck(turn);
            boolean hasMoves = !allLegalMoves(turn).isEmpty();

            if (!hasMoves) {
                status = inCheck ? Status.CHECKMATE : Status.STALEMATE;
            } else {
                status = inCheck ? Status.CHECK : Status.PLAYING;
            }
        }

        // Find king position for check highlight
        int[] kingPos(PColor col) {
            for (int r=0; r<8; r++)
                for (int c=0; c<8; c++) {
                    Piece p = at(r,c);
                    if (p!=null && p.type==PType.KING && p.color==col) return new int[]{r,c};
                }
            return null;
        }
    }

    // =========================================================================
    //  CHESS PANEL — Swing component: renders board + handles input
    // =========================================================================

    static class ChessPanel extends JPanel {

        Board board = new Board();

        // Selection state
        int selRow = -1, selCol = -1;
        List<Move> legalMoves = new ArrayList<>();

        // Hover
        int hovRow = -1, hovCol = -1;

        // Pre-rendered piece images (drawn once, cached)
        Map<String, Image> pieceCache = new HashMap<>();

        // Fonts
        Font pieceFont, labelFont, statusFont, btnFont;

        // Buttons (positioned dynamically in paintComponent)
        Rectangle restartBtn = new Rectangle();
        Rectangle[] promoBtns = null;   // shown during promotion choice
        Move pendingPromo = null;        // move awaiting promotion choice

        ChessPanel() {
            setBackground(BG);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { handleClick(e.getX(), e.getY()); }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) { handleHover(e.getX(), e.getY()); }
            });

            pieceFont  = loadFont(64);
            labelFont  = new Font("Monospaced", Font.BOLD, 11);
            statusFont = new Font("Serif", Font.BOLD, 16);
            btnFont    = new Font("Serif", Font.BOLD, 13);
        }

        private Font loadFont(int size) {
            // Use a font that renders chess Unicode well on all platforms
            String[] candidates = {"Segoe UI Symbol", "Apple Symbols", "DejaVu Sans", "Dialog"};
            for (String name : candidates) {
                Font f = new Font(name, Font.PLAIN, size);
                // Quick check: does this font support ♔?
                if (f.canDisplay('♔')) return f;
            }
            return new Font("Dialog", Font.PLAIN, size);
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(BOARD_PX + 40, BOARD_PX + 100);
        }

        // ── Layout helpers ────────────────────────────────────────────────────

        int boardOriginX() { return (getWidth()  - BOARD_PX) / 2; }
        int boardOriginY() { return 18; }

        int[] squareAt(int px, int py) {
            int bx = boardOriginX(), by = boardOriginY();
            int c = (px - bx) / SQ, r = (py - by) / SQ;
            if (r<0||r>7||c<0||c>7) return null;
            return new int[]{r, c};
        }

        // ── Painting ──────────────────────────────────────────────────────────

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,  RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,          RenderingHints.VALUE_RENDER_QUALITY);

            int W = getWidth(), H = getHeight();
            int bx = boardOriginX(), by = boardOriginY();

            drawBackground(g, W, H);
            drawBoardBorder(g, bx, by);
            drawSquares(g, bx, by);
            drawCoordinates(g, bx, by);
            drawPieces(g, bx, by);
            drawStatusBar(g, bx, by, W);

            if (pendingPromo != null) drawPromoDialog(g, bx, by);
        }

        private void drawBackground(Graphics2D g, int W, int H) {
            g.setColor(BG);
            g.fillRect(0, 0, W, H);
            // Subtle vignette
            RadialGradientPaint vignette = new RadialGradientPaint(
                    new Point2D.Float(W/2f, H/2f), Math.max(W,H)*0.7f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0,0,0,0), new Color(0,0,0,90)});
            g.setPaint(vignette);
            g.fillRect(0, 0, W, H);
        }

        private void drawBoardBorder(Graphics2D g, int bx, int by) {
            // Outer glow / shadow
            for (int i=6; i>0; i--) {
                g.setColor(new Color(0,0,0, 20+i*8));
                g.drawRoundRect(bx-i-2, by-i-2, BOARD_PX+i*2+4, BOARD_PX+i*2+4, 6, 6);
            }
            // Gold border
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(bx-2, by-2, BOARD_PX+3, BOARD_PX+3);
            g.setStroke(new BasicStroke(1f));
        }

        private void drawSquares(Graphics2D g, int bx, int by) {
            int[] kPos = (board.status == Board.Status.CHECK || board.status == Board.Status.CHECKMATE)
                    ? board.kingPos(board.turn) : null;

            for (int r=0; r<8; r++) {
                for (int c=0; c<8; c++) {

                    // LÖSUNG: Lokale Variablen für das Lambda anlegen
                    int finalR = r;
                    int finalC = c;

                    int sx = bx + c*SQ, sy = by + r*SQ;
                    boolean light = (r+c)%2==0;

                    // Base square color
                    Color base = light ? LIGHT_SQ : DARK_SQ;
                    g.setColor(base);
                    g.fillRect(sx, sy, SQ, SQ);

                    // Last move highlight
                    if (board.lastMove != null &&
                            ((r==board.lastMove.fr&&c==board.lastMove.fc)||(r==board.lastMove.tr&&c==board.lastMove.tc))) {
                        g.setColor(LAST_CLR);
                        g.fillRect(sx, sy, SQ, SQ);
                    }

                    // Selection highlight
                    if (r==selRow && c==selCol) {
                        g.setColor(SEL_CLR);
                        g.fillRect(sx, sy, SQ, SQ);
                    }

                    // Check highlight on king
                    if (kPos != null && r==kPos[0] && c==kPos[1]) {
                        // Radial red glow
                        RadialGradientPaint rg = new RadialGradientPaint(
                                sx+SQ/2f, sy+SQ/2f, SQ*0.6f,
                                new float[]{0f, 1f},
                                new Color[]{new Color(220,40,40,200), new Color(220,40,40,0)});
                        g.setPaint(rg);
                        g.fillRect(sx, sy, SQ, SQ);
                    }

                    // Legal move dots / highlights (Hier werden jetzt finalR und finalC genutzt)
                    boolean isTarget = legalMoves.stream().anyMatch(m->m.tr==finalR&&m.tc==finalC);
                    if (isTarget) {
                        if (board.at(r,c) != null) {
                            // Capture ring
                            g.setColor(MOVE_CLR);
                            g.setStroke(new BasicStroke(5f));
                            g.drawOval(sx+3, sy+3, SQ-6, SQ-6);
                            g.setStroke(new BasicStroke(1f));
                        } else {
                            // Move dot
                            g.setColor(MOVE_CLR);
                            int ds = SQ/3;
                            g.fillOval(sx+(SQ-ds)/2, sy+(SQ-ds)/2, ds, ds);
                        }
                    }

                    // Hover tint
                    if (r==hovRow && c==hovCol && !(r==selRow&&c==selCol)) {
                        g.setColor(new Color(255,255,255,18));
                        g.fillRect(sx, sy, SQ, SQ);
                    }
                }
            }
        }

        private void drawCoordinates(Graphics2D g, int bx, int by) {
            g.setFont(labelFont);
            for (int i=0; i<8; i++) {
                // Rank numbers (right side)
                String rank = String.valueOf(8-i);
                g.setColor((i%2==0) ? DARK_SQ : LIGHT_SQ);
                g.drawString(rank, bx + BOARD_PX + 4, by + i*SQ + SQ/2 + 4);

                // File letters (bottom)
                String file = String.valueOf((char)('a'+i));
                g.setColor((i%2==0) ? LIGHT_SQ : DARK_SQ);
                g.drawString(file, bx + i*SQ + SQ/2 - 3, by + BOARD_PX + 14);
            }
        }

        private void drawPieces(Graphics2D g, int bx, int by) {
            g.setFont(pieceFont);
            FontMetrics fm = g.getFontMetrics();

            for (int r=0; r<8; r++) {
                for (int c=0; c<8; c++) {
                    Piece p = board.at(r,c);
                    if (p == null) continue;

                    int sx = bx + c*SQ, sy = by + r*SQ;
                    String glyph = p.glyph();

                    // Shadow
                    g.setColor(new Color(0,0,0,80));
                    int tw = fm.stringWidth(glyph);
                    int tx = sx + (SQ - tw)/2 + 2;
                    int ty = sy + (SQ + fm.getAscent() - fm.getDescent())/2 + 2;
                    g.drawString(glyph, tx, ty);

                    // Piece
                    g.setColor(p.color == PColor.WHITE ? new Color(255,252,240) : new Color(25,20,15));
                    g.drawString(glyph, tx-2, ty-2);

                    // White piece outline (thin stroke simulation by drawing slightly shifted)
                    if (p.color == PColor.WHITE) {
                        g.setColor(new Color(120,90,50,120));
                        g.drawString(glyph, tx-2, ty-2);
                        g.setColor(new Color(255,252,240));
                        g.drawString(glyph, tx-2, ty-2);
                    }
                }
            }
        }

        private void drawStatusBar(Graphics2D g, int bx, int by, int W) {
            int barY = by + BOARD_PX + 20;
            int barH = 52;
            int barX = bx;
            int barW = BOARD_PX;

            // Status background
            g.setColor(PANEL_BG);
            g.fillRoundRect(barX, barY, barW, barH, 10, 10);
            g.setColor(ACCENT.darker());
            g.drawRoundRect(barX, barY, barW, barH, 10, 10);

            // Turn indicator dot
            Color turnColor = board.turn == PColor.WHITE ? new Color(255,252,240) : new Color(40,30,20);
            g.setColor(turnColor);
            g.fillOval(barX+14, barY+16, 20, 20);
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(barX+14, barY+16, 20, 20);
            g.setStroke(new BasicStroke(1f));

            // Status text
            g.setFont(statusFont);
            g.setColor(TEXT_CLR);
            String turnStr = (board.turn == PColor.WHITE ? "White" : "Black");
            String statusStr = switch (board.status) {
                case PLAYING    -> turnStr + " to move";
                case CHECK      -> turnStr + " is in CHECK";
                case CHECKMATE  -> turnStr + " is CHECKMATED  —  " +
                        (board.turn==PColor.WHITE?"Black":"White") + " wins!";
                case STALEMATE  -> "STALEMATE — Draw";
            };
            g.drawString(statusStr, barX+44, barY+31);

            // Restart button
            int btnW=100, btnH=28, btnX=barX+barW-btnW-10, btnY=barY+12;
            restartBtn.setBounds(btnX, btnY, btnW, btnH);
            drawButton(g, btnX, btnY, btnW, btnH, "New Game");
        }

        private void drawButton(Graphics2D g, int x, int y, int w, int h, String text) {
            // Gradient button
            GradientPaint gp = new GradientPaint(x, y, new Color(80,62,38), x, y+h, new Color(55,40,22));
            g.setPaint(gp);
            g.fillRoundRect(x, y, w, h, 7, 7);
            g.setColor(ACCENT);
            g.drawRoundRect(x, y, w, h, 7, 7);
            g.setFont(btnFont);
            g.setColor(TEXT_CLR);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, x+(w-fm.stringWidth(text))/2, y+(h+fm.getAscent()-fm.getDescent())/2);
        }

        private void drawPromoDialog(Graphics2D g, int bx, int by) {
            // Semi-transparent overlay
            g.setColor(new Color(0,0,0,170));
            g.fillRect(0, 0, getWidth(), getHeight());

            // Promotion panel
            int pw = 340, ph = 130;
            int px = (getWidth()-pw)/2, py = (getHeight()-ph)/2;
            g.setColor(PANEL_BG);
            g.fillRoundRect(px, py, pw, ph, 14, 14);
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(px, py, pw, ph, 14, 14);
            g.setStroke(new BasicStroke(1f));

            g.setFont(statusFont);
            g.setColor(TEXT_CLR);
            g.drawString("Promote pawn to:", px+20, py+28);

            // 4 piece buttons
            PType[] opts = {PType.QUEEN, PType.ROOK, PType.BISHOP, PType.KNIGHT};
            PColor pc = (pendingPromo != null) ? board.at(pendingPromo.fr, pendingPromo.fc).color : PColor.WHITE;
            promoBtns = new Rectangle[4];

            g.setFont(loadFont(38));
            for (int i=0; i<4; i++) {
                int bx2 = px + 20 + i*76, by2 = py+40;
                int bw2 = 64, bh2 = 64;
                promoBtns[i] = new Rectangle(bx2, by2, bw2, bh2);

                g.setColor(new Color(60,46,28));
                g.fillRoundRect(bx2, by2, bw2, bh2, 8, 8);
                g.setColor(ACCENT.darker());
                g.drawRoundRect(bx2, by2, bw2, bh2, 8, 8);

                Piece tmp = new Piece(opts[i], pc);
                String glyph = tmp.glyph();
                FontMetrics fm = g.getFontMetrics();
                int tw = fm.stringWidth(glyph);
                g.setColor(pc==PColor.WHITE ? new Color(255,252,240) : new Color(25,20,15));
                g.drawString(glyph, bx2+(bw2-tw)/2, by2+(bh2+fm.getAscent()-fm.getDescent())/2);
            }
        }

        // ── Interaction ───────────────────────────────────────────────────────

        private void handleHover(int px, int py) {
            int[] sq = squareAt(px, py);
            int nr = sq!=null?sq[0]:-1, nc = sq!=null?sq[1]:-1;
            if (nr!=hovRow || nc!=hovCol) { hovRow=nr; hovCol=nc; repaint(); }
        }

        private void handleClick(int px, int py) {
            // Restart
            if (restartBtn.contains(px, py)) {
                board.setup();
                selRow=selCol=-1;
                legalMoves.clear();
                pendingPromo=null;
                promoBtns=null;
                repaint();
                return;
            }

            // Promotion dialog
            if (pendingPromo != null && promoBtns != null) {
                PType[] opts = {PType.QUEEN, PType.ROOK, PType.BISHOP, PType.KNIGHT};
                for (int i=0; i<4; i++) {
                    if (promoBtns[i].contains(px, py)) {
                        pendingPromo.promoteTo = opts[i];
                        applyAndUpdate(pendingPromo);
                        pendingPromo=null; promoBtns=null;
                        repaint();
                        return;
                    }
                }
                return;  // click outside = ignore
            }

            if (board.status==Board.Status.CHECKMATE || board.status==Board.Status.STALEMATE) return;

            int[] sq = squareAt(px, py);
            if (sq == null) return;
            int r=sq[0], c=sq[1];

            if (selRow < 0) {
                // First click — select a piece
                Piece p = board.at(r,c);
                if (p != null && p.color == board.turn) {
                    selRow=r; selCol=c;
                    legalMoves = board.legalMoves(r, c);
                }
            } else {
                // Second click — try to move
                Move target = legalMoves.stream()
                        .filter(m->m.tr==r&&m.tc==c)
                        .findFirst().orElse(null);

                if (target != null) {
                    if (target.isPromotion && target.promoteTo == null) {
                        // Show promotion dialog — pick queen by default if multiple options share tr/tc
                        // Collect all promotions for this square (all 4 piece types)
                        pendingPromo = target;
                        selRow=selCol=-1; legalMoves.clear();
                        // The dialog will call applyAndUpdate
                    } else {
                        applyAndUpdate(target);
                    }
                } else {
                    // Re-select another piece of same color
                    Piece p = board.at(r,c);
                    if (p != null && p.color == board.turn) {
                        selRow=r; selCol=c;
                        legalMoves = board.legalMoves(r, c);
                    } else {
                        selRow=selCol=-1;
                        legalMoves.clear();
                    }
                }
            }
            repaint();
        }

        private void applyAndUpdate(Move m) {
            board.applyMove(m);
            board.updateStatus();
            selRow=selCol=-1;
            legalMoves.clear();
            repaint();
        }
    }
}