import javax.swing.JFrame;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.ArrayList;

/**
 *  Geometry Run – Ultimate Edition
 *
 *  Compile:  javac GeometryRun.java
 *  Run:      java -Dsun.java2d.opengl=true GeometryRun
 *
 *  Controls:
 *    SPACE / UP / W / Click – Jump (Cube/Robot) | Fly (Ship/UFO) | Flip (Ball/Gravity)
 *    P                      – Toggle Practice Mode
 *    Z                      – Place checkpoint (practice mode only)
 *    R                      – Restart current level
 *    S                      – Open skin editor
 *    ESC                    – Back / Menu
 *
 *  Game Modes:
 *    CUBE   – classic jump, gravity physics
 *    SHIP   – hold to fly up, release to fall
 *    BALL   – tap to flip gravity
 *    UFO    – hold for thrust, release to fall slowly
 *    WAVE   – hold to move diagonally up, release for down
 *    ROBOT  – small jump, can hold for extra height
 *
 *  Level Row Format (6 rows per level):
 *    row 0 – ceiling obstacles / end flag (E) / ceiling spikes during flip
 *    row 1 – upper mid obstacles
 *    row 2 – mode portals: C=cube H=ship L=ball U=ufo W=wave R=robot G=gravity_flip N=gravity_norm
 *    row 3 – speed portals: 1=slow(x0.6) 2=normal(x1) 3=fast(x1.5) 4=vfast(x2.2)
 *    row 4 – orbs (O) and pads (D), floating mid-air
 *    row 5 – ground obstacles: S=spike B=block T=tall_block
 *
 *  Physics rules (speed 9, tile=40px):
 *    Jump arc: 29 frames, ~290px = ~7.25 tiles horizontal
 *    Min gap between obstacle groups: 2 tiles clear space
 *    Block landing: player can land on top of blocks safely
 *    Block sides/bottom: instant death on contact
 */
public class Main extends Canvas implements Runnable {

    // =========================================================================
    //  WINDOW / PHYSICS CONSTANTS
    // =========================================================================
    static final int   W = 960, H = 540, TILE = 40;
    static final int   GROUND_Y = H - 90;    // y-coord of the ground line
    static final int   CEIL_Y   = 48;         // y-coord of the ceiling line
    static final int   PS       = 36;         // player square size in pixels
    static final int   PLAYER_X = 140;        // player's fixed screen x-position

    static final float G_BASE       =  1.00f; // gravity acceleration per tick
    static final float JUMP_BASE    = -15.5f; // initial jump velocity (negative = up)
    static final float SHIP_THRUST  =  -1.30f;// ship upward acceleration per tick
    static final float SHIP_DRAG    =  0.875f;// ship velocity multiplier (friction)
    static final float SHIP_VY_MAX  =  10.0f; // maximum ship vertical speed

    // =========================================================================
    //  CACHED RENDERING RESOURCES  (allocated once, reused every frame)
    // =========================================================================
    static final BasicStroke SK1  = new BasicStroke(1f);
    static final BasicStroke SK15 = new BasicStroke(1.5f);
    static final BasicStroke SK2  = new BasicStroke(2f);
    static final BasicStroke SK3  = new BasicStroke(3f);
    static final BasicStroke SK4  = new BasicStroke(4f);

    // Fonts — Courier New for retro pixel aesthetic
    static final Font FNT_MICRO = new Font("Courier New", Font.PLAIN,  10);
    static final Font FNT_TINY  = new Font("Courier New", Font.PLAIN,  11);
    static final Font FNT_SM    = new Font("Courier New", Font.BOLD,   13);
    static final Font FNT_MD    = new Font("Courier New", Font.BOLD,   15);
    static final Font FNT_LG    = new Font("Courier New", Font.BOLD,   22);
    static final Font FNT_XL    = new Font("Courier New", Font.BOLD,   50);
    static final Font FNT_XXL   = new Font("Courier New", Font.BOLD,   68);
    static final Font FNT_BTN   = new Font("Courier New", Font.BOLD,   14);

    // Colours
    static final Color C_SPIKE    = new Color(255,  40,  80);
    static final Color C_SPIKE_GL = new Color(255,  80, 120, 50);
    static final Color C_BLK      = new Color( 14,   6,  52);
    static final Color C_STAR     = new Color(255, 255, 255, 115);
    static final Color C_GRID     = new Color(255, 255, 255,   6);
    static final Color C_OVERLAY  = new Color(  0,   0,   0, 175);
    static final Color C_PANEL    = new Color(  5,   5,  26, 245);
    static final Color C_GOLD     = new Color(255, 210,   0);
    static final Color C_DIM      = new Color(255, 255, 255, 155);
    static final Color C_FAINT    = new Color(255, 255, 255,  55);
    static final Color C_BARBG    = new Color(  0,   0,   0, 130);
    static final Color C_ORB      = new Color(255, 210,  45);
    static final Color C_PAD      = new Color( 60, 255, 180);
    static final Color C_WHITE    = Color.WHITE;

    // Portal colours
    static final Color PC_CUBE  = new Color(  0, 235, 205);
    static final Color PC_SHIP  = new Color(255, 150,   0);
    static final Color PC_BALL  = new Color(190,  85, 255);
    static final Color PC_UFO   = new Color( 85, 190, 255);
    static final Color PC_WAVE  = new Color(255,  40, 190);
    static final Color PC_ROBOT = new Color(190, 190,  65);
    static final Color PC_SLOW  = new Color( 65, 255,  65);
    static final Color PC_FAST  = new Color(255, 170,   0);
    static final Color PC_VFAST = new Color(255,  40,  40);
    static final Color PC_GFLIP = new Color(245, 245,  55);
    static final Color PC_GNORM = new Color(160, 255, 160);

    // =========================================================================
    //  ENUMERATIONS
    // =========================================================================
    enum Phase {
        MAIN_MENU, LEVEL_SELECT, PLAYING, DEAD, WIN, SKIN_EDITOR, LEVEL_MAKER
    }

    enum GameMode {
        CUBE, SHIP, BALL, UFO, WAVE, ROBOT
    }

    enum PortalKind {
        MODE_CUBE, MODE_SHIP, MODE_BALL, MODE_UFO, MODE_WAVE, MODE_ROBOT,
        SPEED_SLOW, SPEED_NORMAL, SPEED_FAST, SPEED_VFAST,
        GRAVITY_FLIP, GRAVITY_NORM
    }

    enum ObjKind {
        SPIKE, BLOCK, TALL_BLOCK, ORB, PAD
    }

    // =========================================================================
    //  INNER CLASSES
    // =========================================================================

    /** A level definition: colours, speed, and the 6 obstacle/portal rows. */
    static class LevelDef {
        final String   name;
        final String   difficulty;   // "EASY", "NORMAL", "HARD"
        final Color    skyTop, skyBot, groundCol, lineCol;
        final Color    groundDark, lineFaint;
        final float    baseSpeed;
        final String[] rows;

        LevelDef(String name, Color skyTop, Color skyBot,
                 Color groundCol, Color lineCol, float speed,
                 String difficulty, String... rows) {
            this.name       = name;
            this.difficulty = difficulty;
            this.skyTop     = skyTop;
            this.skyBot     = skyBot;
            this.groundCol  = groundCol;
            this.lineCol    = lineCol;
            this.baseSpeed  = speed;
            this.rows       = rows;
            // Pre-compute derived colours so no allocation per-frame
            this.groundDark = new Color(groundCol.getRed()   / 5,
                    groundCol.getGreen() / 5,
                    groundCol.getBlue()  / 5);
            this.lineFaint  = new Color(lineCol.getRed(),
                    lineCol.getGreen(),
                    lineCol.getBlue(), 38);
        }
    }

    /** A runtime obstacle or orb/pad object. */
    static class Obj {
        float   wx, wy;   // world position (x scrolls, y is fixed screen coord)
        int     w, h;
        ObjKind kind;
        boolean used;     // true once collected (orb/pad) or if we skip rendering

        Obj(float wx, float wy, int w, int h, ObjKind kind) {
            this.wx = wx; this.wy = wy; this.w = w; this.h = h; this.kind = kind;
        }
    }

    /** A portal — triggers a mode or speed change when the player crosses it. */
    static class Portal {
        float      wx;
        PortalKind kind;
        boolean    used;

        Portal(float wx, PortalKind kind) {
            this.wx = wx; this.kind = kind;
        }
    }

    /** A single particle for trail / explosion / win-firework effects. */
    static class Ptcl {
        float x, y, vx, vy, life, maxLife;
        Color col;
        int   sz;

        Ptcl(float x, float y, float vx, float vy, float life, Color col, int sz) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.maxLife = life; this.col = col; this.sz = sz;
        }
    }

    /** One background star — two layers create parallax depth. */
    static class Star {
        float x, y, radius, speed;
    }

    /** Player skin — colours + shape preset. */
    static class Skin {
        String name      = "Custom";
        Color  primary   = new Color(0, 245, 212);
        Color  secondary = Color.WHITE;
        Color  trail     = new Color(0, 245, 212, 175);
        Color  glowColor = new Color(0, 245, 212, 28);   // cached — update via updateGlow()
        String shape     = "cube"; // cube|diamond|triangle|ghost|arrow|ball

        /** Recompute the glow colour whenever primary changes. */
        void updateGlow() {
            glowColor = new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 28);
        }
    }

    // =========================================================================
    //  LEVEL DATA
    // =========================================================================
    /**
     * All five levels.  Each level has 6 rows of equal length.
     *
     * Physics at speed 9.0, TILE=40:
     *   full jump = 29 frames = 290 px = 7.25 tiles
     *   min safe gap between obstacle groups = 2 tiles
     *   block height = 1 tile, tall-block = 2 tiles, both clearable with full jump
     */
    static final LevelDef[] LEVELS = {

            new LevelDef(
                    "Stereo Madness",
                    new Color(3,10,38),new Color(7,22,75),new Color(0,155,195),new Color(0,215,250),
                    9.0f, "EASY",
                    "........................................................................................................................................................................................................................................................................................................................................................................E....",
                    ".............................................................................................................................................................................................................................................................................................................................................................................",
                    ".............................................................................................................................................................................................................................................................................................................................................................................",
                    ".............................................................................................................................................................................................................................................................................................................................................................................",
                    "..............................O........................O........................O.......................................O..................................O..................................O.......................................O.......................................O.......................................O.............................O........................",
                    "....S....S....S.....S.....SS....S....SS....S.....SS....S....SS....B....S....B....SS....B....S....SS....B....S.......SSS....SS....SSS....S....SS....B....SSS....S....SS.....B....SS....SSS....B....S...SS....B....SSS....B....SS....S.....S....SS....B....SSS....S...SS....B....SS....SSS....B....S...SSS....S....B....SS....SSS....B....S...SS....B....SSS....S....SS........"
            ),

            new LevelDef(
                    "Back on Track",
                    new Color(38,9,3),new Color(82,26,3),new Color(195,90,16),new Color(250,150,38),
                    9.0f, "EASY",
                    "................................................B......B......B......B.......B......B......B......B.......B......B......B.......B...........................................................................................................................................................................................................................................................E....",
                    ".................................................................................................................................................................................................................................................................................................................................................................................................",
                    "............................................H......................................................................................C.............................................................................................................................................................................................................................................................",
                    ".................................................................................................................................................................................................................................................................................................................................................................................................",
                    "....................O........................O......................................................O..................................O..................................O.......................................O.......................................O.......................................O.......................................O..................................O...................",
                    "...S...SS....B....SS....SS....B....S....SS.........B......B......B.......B......B......B.......B......B......B.......B......B.........S....SS....B....SSS....S....SS....B....SS....SSS....B....S...SSS....B....SS....SSS....B....S...SS....B....SSS....S....B....SS....SSS....B....S...SS....B....SSS....S....B....SS....SSS....B....S...SS....SSS....B....SS....SSS....B....S...SS....SSS......."
            ),

            new LevelDef(
                    "Polargeist",
                    new Color(12,3,38),new Color(36,3,78),new Color(110,0,195),new Color(190,72,250),
                    9.0f, "NORMAL",
                    "...........................................................S....SS....S....SS....S....SS....S....SS....S....SS....S....SS....S....SS....S....SS....S....S.......................................................................................................................................................................................................................................................E....",
                    ".....................................................................................................................................................................................................................................................................................................................................................................................................................",
                    "......................................................G.....................................................................................................N........................................................................................................................................................................................................................................................",
                    ".....................................................................................................................................................................................................................................................................................................................................................................................................................",
                    "....................O.............................O.................................................O.......................................O........................O..................................O.......................................O.......................................O.......................................O.......................................O..................................O.........",
                    "...S...SS....B....SS....SS....B....SS....S....SSS........S....SS....S....SS....S....SS....S....SS....S....SS....S....SS....S....SS....S....SS....S....SS........S....SS....T....SSS....S....SS....B....SS....SSS....T....S...SSS....B....SS....SSS....T....S...SS....B....SSS....S....T....SS....SSS....B....SS....SSS....T....S...SS....B....SSS....S....T....SS....SSS....B....SS....SSS....T....S...SS....SSS....."
            ),

            new LevelDef(
                    "Dry Out",
                    new Color(38,18,3),new Color(78,42,9),new Color(170,130,52),new Color(250,190,78),
                    9.0f, "HARD",
                    ".......................................................S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S....................................................................................................................................................................................................................................................................E....",
                    ".........................................................................................................................................................................................................................................................................................................................................................................................................................................",
                    ".........................................................................................................................................................................................................................................................................................................................................................................................................................................",
                    ".....3............................................................................................................................................................2......................................................................................................................................................................................................................................................................",
                    "....................O........................O......................................................O..................................O.............................O..................................O.......................................O.......................................O.......................................O.......................................O.......................................O........................",
                    "...S....SS....B....SS....SS....B....SS....S.........S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S....SS....T....SSS....S....SS....B....SS....SSS....T....S...SS....B....SSS....S....T....SS....SSS....B....SS....SSS....T....S...SS....B....SSS....S....T....SS....SSS....B....SS....SSS....T....S...SS....B....SSS....S....T....SS....SSS....B....SS....SSS....S...."
            ),

            new LevelDef(
                    "Base After Base",
                    new Color(0,18,3),new Color(0,46,13),new Color(0,190,72),new Color(0,250,98),
                    9.0f, "HARD",
                    "..........B......B......B......B......B......B......B.......B......B......B......B......B.....................................................................S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.........................................................................................................................................................................................................E....",
                    ".............................................................................................................................................................................................................................................................................................................................................................................................................................................................",
                    "..........H..................................................................................C...........................................................W.......................................................................................C...........................................................................................................................................................................................................",
                    "..................................................................................................................................................................................................................................................3..........................................................................................................................................................................................................",
                    "....................O.............................O.............................O.............................O..................................O..................................O.......................................O..................................O.......................................O.......................................O.......................................O.......................................O......................O......",
                    "............B......B......B......B.......B......B......B......B.......B......B......B......B.....S....SS....B....SSS....S....B....SS....SSS....B....S......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.......S.........S....SS....T....SSS....S....B....SS....SSS....T....S...SS....B....SSS....S....T....SS....SSS....B....SS....SSS....T....S...SS....B....SSS....S....T....SS....SSS....B....SS....SSS....T....S...SS..S...."
            ),

    };


    // =========================================================================
    //  SKIN PRESETS
    // =========================================================================
    static final Skin[] PRESETS;
    static {
        // Each entry: { name, shape, primary[r,g,b], secondary[r,g,b], trail[r,g,b] }
        Object[][] pd = {
                { "Neon",    "cube",     new int[]{ 0,245,212}, new int[]{255,255,255}, new int[]{ 0,245,212} },
                { "Inferno", "triangle", new int[]{255, 72,  0}, new int[]{255,214, 10}, new int[]{255, 72,  0} },
                { "Void",    "diamond",  new int[]{176,  0,255}, new int[]{ 76,201,240}, new int[]{176,  0,255} },
                { "Matrix",  "cube",     new int[]{ 0,255, 65}, new int[]{ 0, 80, 20}, new int[]{ 0,255, 65} },
                { "Ice",     "ball",     new int[]{120,220,255}, new int[]{200,240,255}, new int[]{120,220,255} },
                { "Sakura",  "arrow",    new int[]{255,160,200}, new int[]{255,200,230}, new int[]{255,160,200} },
                { "Solar",   "diamond",  new int[]{255,200,  0}, new int[]{255,100,  0}, new int[]{255,200,  0} },
                { "Ghost",   "ghost",    new int[]{180,180,255}, new int[]{ 80, 80,180}, new int[]{180,180,255} },
        };
        PRESETS = new Skin[pd.length];
        for (int i = 0; i < pd.length; i++) {
            PRESETS[i] = new Skin();
            PRESETS[i].name      = (String) pd[i][0];
            PRESETS[i].shape     = (String) pd[i][1];
            int[] p = (int[]) pd[i][2];
            int[] s = (int[]) pd[i][3];
            int[] t = (int[]) pd[i][4];
            PRESETS[i].primary   = new Color(p[0], p[1], p[2]);
            PRESETS[i].secondary = new Color(s[0], s[1], s[2]);
            PRESETS[i].trail     = new Color(t[0], t[1], t[2], 175);
            PRESETS[i].updateGlow();
        }
    }

    // =========================================================================
    //  GAME STATE
    // =========================================================================

    // ── Phase / mode ──────────────────────────────────────────────────────────
    Phase    phase    = Phase.MAIN_MENU;
    GameMode gameMode = GameMode.CUBE;

    // ── Skin ──────────────────────────────────────────────────────────────────
    Skin skin     = copySkin(PRESETS[0]);   // active skin used during play
    Skin editSkin = copySkin(PRESETS[0]);   // skin being edited in editor

    // ── Player physics ────────────────────────────────────────────────────────
    float   playerY     = GROUND_Y - PS;    // player top-left y (screen space)
    float   playerVY    = 0f;               // vertical velocity (positive = downward)
    float   gravity     = G_BASE;           // current gravity (may be flipped)
    boolean onGround    = true;
    boolean holdingUp   = false;            // jump/fly key held
    boolean gravFlipped = false;            // gravity-flip state
    boolean jumpUsed    = false;            // prevents double-jump in same key hold

    // ── World scroll ──────────────────────────────────────────────────────────
    float  scrollX     = 0f;               // how far the world has scrolled
    float  scrollSpeed = 9f;               // pixels per tick
    double rotation    = 0.0;             // player rotation in radians
    float  bgOffset    = 0f;              // parallax bg scroll offset

    // ── Level tracking ────────────────────────────────────────────────────────
    int   currentLevel    = 0;
    float endFlagWX       = 0f;           // world-x of the end flag
    int   tick            = 0;
    int[] bestPct         = new int[LEVELS.length];
    int[] totalAttempts   = new int[LEVELS.length];

    // ── Collections ───────────────────────────────────────────────────────────
    final List<Obj>    objs      = new ArrayList<>();
    final List<Portal> portals   = new ArrayList<>();
    final List<Ptcl>   particles = new ArrayList<>();
    final List<Star>   starsNear = new ArrayList<>();
    final List<Star>   starsFar  = new ArrayList<>();

    // ── Practice mode ─────────────────────────────────────────────────────────
    boolean  practiceMode = false;
    float    cpScrollX    = -1f;           // checkpoint scroll position (-1 = none)
    float    cpPlayerY    = 0f;
    float    cpSpeed      = 9f;
    GameMode cpMode       = GameMode.CUBE;

    // ── Visual effects ────────────────────────────────────────────────────────
    float shakeAmt   = 0f;
    int   shakeDir   = 1;
    float flashAlpha = 0f;               // white flash on death/gravity flip
    int   deadTimer  = 0;
    int   winTimer   = 0;

    // ── Score / combo ─────────────────────────────────────────────────────────
    int score         = 0;
    int combo         = 0;
    int orbsCollected = 0;

    // ── Skin editor UI state ──────────────────────────────────────────────────
    int     editorTab  = 0;
    int     sliderDrag = -1;

    // Skin editor layout constants
    static final int SL_X   = 115;
    static final int SL_Y0  = 170;
    static final int SL_W   = 450;
    static final int SL_H   = 14;
    static final int SL_GAP = 52;
    static final String[] TAB_NAMES = { "Presets", "Primary", "Secondary", "Trail", "Shapes" };
    static final int[]    TAB_X     = { 24, 170, 316, 462, 608 };
    static final int      TAB_Y     = 72;
    static final int      TAB_W     = 118;
    static final int      TAB_H     = 34;

    // ── Level Maker ──────────────────────────────────────────────────────────
    // The custom level is always stored as 6 rows of chars, each MAKER_COLS wide.
    static final int   MAKER_COLS  = 120;    // columns in the custom level
    static final int   MAKER_ROWS  = 6;
    static final int   MAKER_CELL  = 18;     // cell size in the editor grid
    static final int   MAKER_OFF_X = 16;     // grid left margin
    static final int   MAKER_OFF_Y = 90;     // grid top margin

    char[][]    makerGrid      = new char[MAKER_ROWS][MAKER_COLS];
    int         makerScrollCol = 0;          // first visible column (scrolling)
    char        makerTool      = 'S';        // currently selected draw tool
    int         makerRow       = -1;         // last hovered row (for hover highlight)
    int         makerCol       = -1;         // last hovered col
    boolean     makerErasing   = false;      // right-click = erase
    boolean     makerTestMode  = false;      // when true, playing the custom level
    boolean     makerPlayback  = false;      // playing back a test run
    LevelDef    makerLevel     = null;       // compiled level for playback
    String      makerLevelName = "My Level"; // editable name

    // Toolbar layout
    // Tools: S B T O D H C W L G N 1 3 4 E (obstacle + portal chars)
    static final char[]   MAKER_TOOLS   = {'S','B','T','O','D','H','C','W','L','G','N','1','3','4','E'};
    static final String[] MAKER_TOOL_LB = {"Spike","Block","Tall","Orb","Pad","Ship","Cube","Wave","Ball","GFlip","GNorm","Slow","Fast","VFast","End"};
    static final int      TOOL_BTN_W    = 52;
    static final int      TOOL_BTN_H    = 28;
    static final int      TOOL_START_X  = 16;
    static final int      TOOL_Y        = 50;

    void initMakerGrid() {
        for (int r = 0; r < MAKER_ROWS; r++)
            java.util.Arrays.fill(makerGrid[r], '.');
        makerScrollCol = 0;
        makerTool = 'S';
    }

    // ── Threading ─────────────────────────────────────────────────────────────
    volatile boolean running    = true;
    Thread           gameThread = null;
    final Random     rng        = new Random();

    // =========================================================================
    //  CONSTRUCTOR
    // =========================================================================
    public Main() {
        setPreferredSize(new Dimension(W, H));
        setIgnoreRepaint(true);
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed (KeyEvent e) { onKeyPress  (e.getKeyCode()); }
            @Override public void keyReleased(KeyEvent e) { onKeyRelease(e.getKeyCode()); }
        });

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed (MouseEvent e) {
                if (phase == Phase.LEVEL_MAKER) makerMousePress(e.getX(), e.getY(), e.getButton() == MouseEvent.BUTTON3);
                else onMousePress(e.getX(), e.getY());
            }
            @Override public void mouseReleased(MouseEvent e) { onMouseRelease(); }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (phase == Phase.SKIN_EDITOR && sliderDrag >= 0) applySlider(e.getX());
                if (phase == Phase.LEVEL_MAKER) makerMousePress(e.getX(), e.getY(), makerErasing);
            }
            @Override public void mouseMoved(MouseEvent e) {
                if (phase == Phase.LEVEL_MAKER) makerUpdateHover(e.getX(), e.getY());
            }
        });

        generateStars();
    }

    /** Called from main after the frame is visible. Starts the game loop thread. */
    public void startGameThread() {
        gameThread = new Thread(this, "GeometryRun-Loop");
        gameThread.setDaemon(true);
        gameThread.start();
    }

    // =========================================================================
    //  GAME LOOP — fixed timestep 60fps, Thread.yield avoids spinning
    // =========================================================================
    @Override
    public void run() {
        final long TARGET_NS = 1_000_000_000L / 60;
        long lastTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            if (now - lastTime >= TARGET_NS) {
                lastTime += TARGET_NS;
                // Catch-up guard: if we fall more than 4 frames behind, reset
                if (now - lastTime > TARGET_NS * 4) lastTime = now;
                tick();
                renderFrame();
            }
            Thread.yield();
        }
    }

    // =========================================================================
    //  STAR GENERATION
    // =========================================================================
    void generateStars() {
        starsNear.clear();
        starsFar.clear();
        for (int i = 0; i < 60; i++) {
            Star s = new Star();
            s.x      = rng.nextFloat() * W;
            s.y      = CEIL_Y + rng.nextFloat() * (GROUND_Y - CEIL_Y);
            s.radius = 0.6f + rng.nextFloat() * 1.6f;
            s.speed  = 0.20f + rng.nextFloat() * 0.35f;
            starsFar.add(s);
        }
        for (int i = 0; i < 38; i++) {
            Star s = new Star();
            s.x      = rng.nextFloat() * W;
            s.y      = CEIL_Y + rng.nextFloat() * (GROUND_Y - CEIL_Y);
            s.radius = 0.9f + rng.nextFloat() * 2.2f;
            s.speed  = 0.50f + rng.nextFloat() * 0.55f;
            starsNear.add(s);
        }
    }

    // =========================================================================
    //  SKIN HELPERS
    // =========================================================================
    Skin copySkin(Skin src) {
        Skin n = new Skin();
        n.name      = src.name;
        n.primary   = src.primary;
        n.secondary = src.secondary;
        n.trail     = src.trail;
        n.shape     = src.shape;
        n.glowColor = src.glowColor;
        return n;
    }

    void openSkinEditor() {
        editSkin  = copySkin(skin);
        editorTab = 0;
        phase     = Phase.SKIN_EDITOR;
    }

    // =========================================================================
    //  LEVEL LOADING
    // =========================================================================
    void loadLevel(int idx) {
        currentLevel = idx;
        totalAttempts[idx]++;

        LevelDef def = LEVELS[idx];

        // Clear runtime state
        objs.clear();
        portals.clear();
        particles.clear();

        // Reset physics
        scrollX     = 0f;
        scrollSpeed = def.baseSpeed;
        gravity     = G_BASE;
        gravFlipped = false;
        playerY     = GROUND_Y - PS;
        playerVY    = 0f;
        onGround    = true;
        holdingUp   = false;
        rotation    = 0.0;
        bgOffset    = 0f;
        gameMode    = GameMode.CUBE;
        jumpUsed    = false;

        // Reset effects & score
        score         = 0;
        combo         = 0;
        orbsCollected = 0;
        shakeAmt      = 0f;
        flashAlpha    = 0f;
        deadTimer     = 0;
        winTimer      = 0;
        endFlagWX     = 0f;

        // Reset practice checkpoint
        cpScrollX = -1f;
        cpPlayerY = 0f;
        cpSpeed   = def.baseSpeed;
        cpMode    = GameMode.CUBE;

        // Parse the level rows into runtime objects
        int numRows = def.rows.length;
        int numCols = 0;
        for (String r : def.rows) numCols = Math.max(numCols, r.length());

        int laneH = (GROUND_Y - CEIL_Y - 20) / numRows;

        for (int row = 0; row < numRows; row++) {
            String rowStr = def.rows[row];
            for (int col = 0; col < rowStr.length(); col++) {
                char ch = rowStr.charAt(col);
                if (ch == '.') continue;

                float wx       = W + col * TILE;
                boolean onGnd  = (row >= numRows - 2);  // bottom two rows = ground level
                float   tileY  = CEIL_Y + 20 + row * laneH;

                switch (ch) {
                    case 'S': {
                        float sy = onGnd ? (GROUND_Y - TILE + 4) : tileY;
                        objs.add(new Obj(wx, sy, TILE - 2, TILE - 2, ObjKind.SPIKE));
                        break;
                    }
                    case 'B': {
                        float by = onGnd ? (GROUND_Y - TILE) : tileY;
                        objs.add(new Obj(wx, by, TILE, TILE, ObjKind.BLOCK));
                        break;
                    }
                    case 'T': {
                        float ty = onGnd ? (GROUND_Y - TILE * 2) : tileY;
                        objs.add(new Obj(wx, ty, TILE, TILE * 2, ObjKind.TALL_BLOCK));
                        break;
                    }
                    case 'O': {
                        float oy = onGnd ? (GROUND_Y - TILE - 24) : tileY + 6;
                        objs.add(new Obj(wx + 4, oy, 30, 30, ObjKind.ORB));
                        break;
                    }
                    case 'D': {
                        float dy = onGnd ? (GROUND_Y - TILE - 6) : tileY;
                        objs.add(new Obj(wx, dy, TILE, 8, ObjKind.PAD));
                        break;
                    }
                    case 'C': portals.add(new Portal(wx, PortalKind.MODE_CUBE));  break;
                    case 'H': portals.add(new Portal(wx, PortalKind.MODE_SHIP));  break;
                    case 'L': portals.add(new Portal(wx, PortalKind.MODE_BALL));  break;
                    case 'U': portals.add(new Portal(wx, PortalKind.MODE_UFO));   break;
                    case 'W': portals.add(new Portal(wx, PortalKind.MODE_WAVE));  break;
                    case 'R': portals.add(new Portal(wx, PortalKind.MODE_ROBOT)); break;
                    case '1': portals.add(new Portal(wx, PortalKind.SPEED_SLOW));   break;
                    case '2': portals.add(new Portal(wx, PortalKind.SPEED_NORMAL)); break;
                    case '3': portals.add(new Portal(wx, PortalKind.SPEED_FAST));   break;
                    case '4': portals.add(new Portal(wx, PortalKind.SPEED_VFAST));  break;
                    case 'G': portals.add(new Portal(wx, PortalKind.GRAVITY_FLIP)); break;
                    case 'N': portals.add(new Portal(wx, PortalKind.GRAVITY_NORM)); break;
                    case 'E': endFlagWX = wx; break;
                    default: break;
                }
            }
        }

        if (endFlagWX == 0f) endFlagWX = W + numCols * TILE - 100;

        phase = Phase.PLAYING;
    }

    // =========================================================================
    //  MAIN TICK (called 60× per second)
    // =========================================================================
    void tick() {
        tick++;

        // Decay camera shake
        if (shakeAmt > 0f) {
            shakeAmt  = Math.max(0f, shakeAmt - 0.55f);
            shakeDir  = -shakeDir;
        }
        // Decay flash
        if (flashAlpha > 0f) {
            flashAlpha = Math.max(0f, flashAlpha - 0.04f);
        }

        switch (phase) {
            case PLAYING:
                updateGame();
                break;
            case DEAD:
                deadTimer++;
                updateParticles();
                break;
            case WIN:
                winTimer++;
                updateParticles();
                spawnWinTick();
                break;
            default:
                updateParticles();
                break;
        }
    }

    // =========================================================================
    //  GAME UPDATE
    // =========================================================================
    void updateGame() {
        bgOffset  += scrollSpeed * 0.36f;
        scrollX   += scrollSpeed;

        // Step physics for current mode
        switch (gameMode) {
            case CUBE:  physicsCube();  break;
            case SHIP:  physicsShip();  break;
            case BALL:  physicsBall();  break;
            case UFO:   physicsUFO();   break;
            case WAVE:  physicsWave();  break;
            case ROBOT: physicsRobot(); break;
        }

        // Check portal triggers
        float worldPX = scrollX + PLAYER_X;
        for (Portal p : portals) {
            if (!p.used && worldPX >= p.wx && worldPX < p.wx + TILE) {
                p.used = true;
                activatePortal(p.kind);
            }
        }

        // Check orb / pad interactions
        for (Obj o : objs) {
            if (o.used) continue;
            if (o.kind == ObjKind.ORB) {
                float cx = worldPX + PS / 2f;
                float cy = playerY + PS / 2f;
                float ox = o.wx + 15f;
                float oy = o.wy + 15f;
                if (holdingUp && Math.abs(cx - ox) < 34f && Math.abs(cy - oy) < 34f) {
                    o.used = true;
                    collectOrb(o);
                }
            } else if (o.kind == ObjKind.PAD) {
                if (worldPX + PS > o.wx && worldPX < o.wx + o.w
                        && Math.abs(playerY + PS - o.wy) < 12f) {
                    o.used = true;
                    triggerPad();
                }
            }
        }

        // Check collision (death)
        if (checkCollision()) {
            handleDeath();
            return;
        }

        // Check level completion
        if (worldPX >= endFlagWX + TILE) {
            handleWin();
            return;
        }

        updateParticles();
        spawnTrailParticle();
    }

    // =========================================================================
    //  DEATH & WIN
    // =========================================================================
    void handleDeath() {
        shakeAmt   = 12f;
        flashAlpha = 0.9f;
        combo      = 0;
        spawnDeathParticles();

        int pct = completionPct();
        if (pct > bestPct[currentLevel]) bestPct[currentLevel] = pct;

        if (practiceMode && cpScrollX >= 0f) {
            // Respawn at practice checkpoint
            scrollX     = cpScrollX;
            playerY     = cpPlayerY;
            scrollSpeed = cpSpeed;
            gameMode    = cpMode;
            gravity     = G_BASE;
            gravFlipped = false;
            playerVY    = 0f;
            onGround    = true;
            jumpUsed    = false;
            // Re-mark portals/orbs already passed
            for (Portal p : portals) p.used = (p.wx < scrollX + PLAYER_X);
            for (Obj    o : objs)    if (o.kind == ObjKind.ORB || o.kind == ObjKind.PAD)
                o.used = (o.wx < scrollX + PLAYER_X);
            particles.clear();
            shakeAmt = 0f;
        } else {
            phase     = Phase.DEAD;
            deadTimer = 0;
        }
    }

    void handleWin() {
        bestPct[currentLevel] = 100;
        // Bonus score: base + speed bonus + first-attempt bonus
        score += 5000
                + (int)(scrollSpeed * 200)
                + (totalAttempts[currentLevel] == 1 ? 3000 : 0);
        spawnWinParticles();
        phase     = Phase.WIN;
        winTimer  = 0;
    }

    // =========================================================================
    //  PHYSICS — CUBE
    // =========================================================================
    void physicsCube() {
        playerVY += gravity;
        playerY  += playerVY;

        if (!resolveBlockLanding()) {
            clampToFloorCeiling();
        }

        if (onGround) jumpUsed = false;

        // Rotation: spin in air, snap to 90° multiples on ground
        if (!onGround) {
            rotation += gravFlipped ? -0.105 : 0.105;
        } else {
            rotation = Math.round(rotation / (Math.PI / 2)) * (Math.PI / 2);
        }
    }

    // =========================================================================
    //  PHYSICS — SHIP
    // =========================================================================
    void physicsShip() {
        if (holdingUp) {
            playerVY += SHIP_THRUST;
        } else {
            playerVY += gravity * 0.45f;
        }

        // Clamp to max speed
        if (playerVY < -SHIP_VY_MAX) playerVY = -SHIP_VY_MAX;
        if (playerVY >  SHIP_VY_MAX) playerVY =  SHIP_VY_MAX;

        playerVY *= SHIP_DRAG;
        playerY  += playerVY;
        clampToFloorCeiling();

        // Tilt nose based on vertical velocity
        rotation = Math.max(-0.65, Math.min(0.65, playerVY * 0.10));
    }

    // =========================================================================
    //  PHYSICS — BALL
    // =========================================================================
    void physicsBall() {
        playerVY += gravity;
        playerY  += playerVY;

        if (!gravFlipped) {
            if (playerY >= GROUND_Y - PS) {
                playerY  = GROUND_Y - PS;
                playerVY = 0f;
                onGround = true;
            } else {
                onGround = false;
            }
        } else {
            if (playerY <= CEIL_Y) {
                playerY  = CEIL_Y;
                playerVY = 0f;
                onGround = true;
            } else {
                onGround = false;
            }
        }

        if (onGround) jumpUsed = false;
        rotation += gravity > 0 ? 0.14 : -0.14;
    }

    // =========================================================================
    //  PHYSICS — UFO
    // =========================================================================
    void physicsUFO() {
        if (holdingUp) {
            playerVY = Math.max(playerVY - 1.2f, -9f);
        } else {
            playerVY = Math.min(playerVY + 0.58f, 9f);
        }
        playerY += playerVY;
        clampToFloorCeiling();
        rotation = Math.max(-0.4, Math.min(0.4, playerVY * 0.06));
    }

    // =========================================================================
    //  PHYSICS — WAVE
    // =========================================================================
    void physicsWave() {
        playerY += holdingUp ? -8.2f : 8.2f;
        clampToFloorCeiling();
        rotation = holdingUp ? -0.52 : 0.52;
    }

    // =========================================================================
    //  PHYSICS — ROBOT  (small jump, hold for extra height)
    // =========================================================================
    void physicsRobot() {
        if (holdingUp && onGround && !jumpUsed) {
            playerVY = JUMP_BASE * 0.68f;
            onGround = false;
            jumpUsed = true;
            spawnJumpParticles();
        } else if (holdingUp && !onGround && playerVY < 0f) {
            // Holding gives a little extra height
            playerVY += gravity * 0.42f;
        }

        playerVY += gravity;
        playerY  += playerVY;

        if (!resolveBlockLanding()) {
            clampToFloorCeiling();
        }

        if (onGround) jumpUsed = false;

        if (!onGround) {
            rotation += 0.085;
        } else {
            rotation = Math.round(rotation / (Math.PI / 2)) * (Math.PI / 2);
        }
    }

    // =========================================================================
    //  PHYSICS HELPERS
    // =========================================================================

    /**
     * Checks if the player is landing on top of a block this frame.
     * Returns true if the player was placed on top of a block.
     * Landing on top is safe; sides and bottom are lethal (handled by checkCollision).
     */
    boolean resolveBlockLanding() {
        float wx  = scrollX + PLAYER_X;
        float px1 = wx + 4f;
        float px2 = wx + PS - 4f;
        float py2 = playerY + PS;
        float prevPy2 = py2 - playerVY;

        for (Obj o : objs) {
            if (o.kind != ObjKind.BLOCK && o.kind != ObjKind.TALL_BLOCK) continue;
            if (px2 <= o.wx + 2f || px1 >= o.wx + o.w - 2f) continue;
            if (prevPy2 <= o.wy + 3f && py2 >= o.wy) {
                playerY  = o.wy - PS;
                playerVY = 0f;
                onGround = true;
                return true;
            }
        }
        return false;
    }

    /** Clamps the player to the floor / ceiling, setting onGround appropriately. */
    void clampToFloorCeiling() {
        if (!gravFlipped) {
            if (playerY >= GROUND_Y - PS) {
                playerY  = GROUND_Y - PS;
                playerVY = 0f;
                onGround = true;
            } else {
                onGround = false;
            }
            if (playerY < CEIL_Y) {
                playerY  = CEIL_Y;
                if (playerVY < 0f) playerVY = 2.5f;
            }
        } else {
            if (playerY <= CEIL_Y) {
                playerY  = CEIL_Y;
                playerVY = 0f;
                onGround = true;
            } else {
                onGround = false;
            }
            if (playerY > GROUND_Y - PS) {
                playerY  = GROUND_Y - PS;
                if (playerVY > 0f) playerVY = -2.5f;
            }
        }
    }

    // =========================================================================
    //  PORTALS
    // =========================================================================
    void activatePortal(PortalKind k) {
        spawnPortalFlash();
        float base = LEVELS[currentLevel].baseSpeed;

        switch (k) {
            case MODE_CUBE:
                gameMode    = GameMode.CUBE;
                gravity     = G_BASE;
                gravFlipped = false;
                jumpUsed    = false;
                break;
            case MODE_SHIP:
                gameMode = GameMode.SHIP;
                gravity  = G_BASE * 0.3f;
                break;
            case MODE_BALL:
                gameMode = GameMode.BALL;
                gravity  = G_BASE;
                jumpUsed = false;
                break;
            case MODE_UFO:
                gameMode = GameMode.UFO;
                gravity  = G_BASE * 0.18f;
                break;
            case MODE_WAVE:
                gameMode = GameMode.WAVE;
                gravity  = 0f;
                break;
            case MODE_ROBOT:
                gameMode = GameMode.ROBOT;
                gravity  = G_BASE;
                jumpUsed = false;
                break;
            case SPEED_SLOW:   scrollSpeed = base * 0.60f; break;
            case SPEED_NORMAL: scrollSpeed = base;         break;
            case SPEED_FAST:   scrollSpeed = base * 1.50f; break;
            case SPEED_VFAST:  scrollSpeed = base * 2.20f; break;
            case GRAVITY_FLIP:
                gravFlipped = !gravFlipped;
                gravity     = -gravity;
                playerVY    = -3.5f;
                flashAlpha  = 0.5f;
                break;
            case GRAVITY_NORM:
                gravFlipped = false;
                gravity     = Math.abs(gravity);
                break;
        }
    }

    // =========================================================================
    //  ORB & PAD
    // =========================================================================
    void collectOrb(Obj o) {
        playerVY      = JUMP_BASE * 1.1f;
        onGround      = false;
        orbsCollected++;
        combo++;
        score        += 100 * Math.min(combo, 10);
        shakeAmt      = 4f;

        for (int i = 0; i < 18; i++) {
            particles.add(new Ptcl(
                    o.wx - scrollX + 15f, o.wy + 15f,
                    (rng.nextFloat() - 0.5f) * 8f,
                    -(rng.nextFloat() * 7f + 1f),
                    24 + rng.nextInt(10), C_ORB, 3 + rng.nextInt(3)));
        }
    }

    void triggerPad() {
        playerVY = JUMP_BASE * 1.58f;
        onGround = false;
        shakeAmt = 5f;
        spawnJumpParticles();
    }

    // =========================================================================
    //  COLLISION DETECTION
    // =========================================================================
    boolean checkCollision() {
        float wx  = scrollX + PLAYER_X;
        float px1 = wx + 5f;
        float py1 = playerY + 5f;
        float px2 = wx + PS - 5f;
        float py2 = playerY + PS - 5f;

        // Hit ceiling or floor = death
        if (playerY <= CEIL_Y - 4f)   return true;
        if (playerY >= GROUND_Y - PS + 4f) return true;

        for (Obj o : objs) {
            if (o.kind == ObjKind.ORB || o.kind == ObjKind.PAD) continue;

            if (o.kind == ObjKind.SPIKE) {
                // Narrower hitbox for spikes (tip area)
                float sx1 = o.wx + o.w * 0.20f;
                float sx2 = o.wx + o.w * 0.80f;
                float sy1 = o.wy + o.h * 0.25f;
                if (px2 > sx1 && px1 < sx2 && py2 > sy1 && py1 < o.wy + o.h)
                    return true;
            } else {
                // Block / tall-block
                if (!(px2 > o.wx && px1 < o.wx + o.w && py2 > o.wy && py1 < o.wy + o.h))
                    continue;
                // Landing on top is safe — only side/bottom hits kill
                if (playerY + PS <= o.wy + 8f) continue;
                return true;
            }
        }
        return false;
    }

    /** Returns how far through the level the player currently is (0-100). */
    int completionPct() {
        float span = endFlagWX - W;
        if (span <= 0f) return 0;
        return (int) Math.min(100f, scrollX / span * 100f);
    }

    // =========================================================================
    //  JUMP INPUT  (shared between keyboard and mouse)
    // =========================================================================
    void doJumpPress() {
        switch (gameMode) {
            case CUBE:
                if (onGround && !jumpUsed) {
                    playerVY = JUMP_BASE;
                    onGround = false;
                    jumpUsed = true;
                    spawnJumpParticles();
                }
                break;
            case ROBOT:
                if (onGround && !jumpUsed) {
                    playerVY = JUMP_BASE * 0.68f;
                    onGround = false;
                    jumpUsed = true;
                    spawnJumpParticles();
                }
                break;
            case BALL:
                gravity     = -gravity;
                gravFlipped = !gravFlipped;
                spawnJumpParticles();
                break;
            default:
                break; // SHIP, UFO, WAVE use holdingUp continuously
        }
    }

    // =========================================================================
    //  INPUT — KEYBOARD
    // =========================================================================
    void onKeyPress(int k) {
        if (phase == Phase.PLAYING) {
            boolean isJump = k == KeyEvent.VK_SPACE || k == KeyEvent.VK_UP || k == KeyEvent.VK_W;
            if (isJump) {
                holdingUp = true;
                doJumpPress();
            }
            if (k == KeyEvent.VK_R) loadLevel(currentLevel);
            if (k == KeyEvent.VK_S) openSkinEditor();
            if (k == KeyEvent.VK_P) {
                practiceMode = !practiceMode;
                cpScrollX    = -1f;  // reset checkpoint on toggle
            }
            if (k == KeyEvent.VK_Z && practiceMode) {
                cpScrollX = scrollX;
                cpPlayerY = playerY;
                cpSpeed   = scrollSpeed;
                cpMode    = gameMode;
            }
            if (k == KeyEvent.VK_ESCAPE) phase = Phase.MAIN_MENU;

        } else if (phase == Phase.DEAD) {
            if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_R) loadLevel(currentLevel);
            if (k == KeyEvent.VK_ESCAPE) phase = Phase.MAIN_MENU;

        } else if (phase == Phase.WIN) {
            if (k == KeyEvent.VK_SPACE) {
                if (currentLevel < LEVELS.length - 1) {
                    currentLevel++;
                    loadLevel(currentLevel);
                } else {
                    phase = Phase.MAIN_MENU;
                }
            }
            if (k == KeyEvent.VK_ESCAPE) phase = Phase.LEVEL_SELECT;

        } else if (phase == Phase.MAIN_MENU) {
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) phase = Phase.LEVEL_SELECT;
            if (k == KeyEvent.VK_S)      openSkinEditor();
            if (k == KeyEvent.VK_ESCAPE) System.exit(0);

        } else if (phase == Phase.LEVEL_SELECT) {
            if (k == KeyEvent.VK_ESCAPE) phase = Phase.MAIN_MENU;

        } else if (phase == Phase.SKIN_EDITOR) {
            if (k == KeyEvent.VK_ESCAPE || k == KeyEvent.VK_ENTER) {
                skin  = copySkin(editSkin);
                phase = Phase.MAIN_MENU;
            }
        } else if (phase == Phase.LEVEL_MAKER) {
            makerKeyPress(k);
        }
    }

    void onKeyRelease(int k) {
        if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_UP || k == KeyEvent.VK_W) {
            holdingUp = false;
        }
    }

    // =========================================================================
    //  INPUT — MOUSE
    // =========================================================================
    void onMousePress(int mx, int my) {
        if (phase == Phase.PLAYING) {
            holdingUp = true;
            doJumpPress();
            return;
        }
        switch (phase) {
            case MAIN_MENU:    mainMenuClick   (mx, my); break;
            case LEVEL_SELECT: levelSelectClick(mx, my); break;
            case DEAD:
                if (inRect(mx, my, W/2-100, H/2+72, 200, 38)) phase = Phase.MAIN_MENU;
                else loadLevel(currentLevel);
                break;
            case WIN:
                if (currentLevel < LEVELS.length - 1) {
                    currentLevel++;
                    loadLevel(currentLevel);
                } else {
                    phase = Phase.MAIN_MENU;
                }
                break;
            case SKIN_EDITOR:  skinEditorClick (mx, my); break;
            case LEVEL_MAKER: makerMousePress  (mx, my, false); break;
            default: break;
        }
    }

    void onMouseRelease() {
        holdingUp  = false;
        sliderDrag = -1;
    }

    // =========================================================================
    //  UI CLICK HANDLERS
    // =========================================================================
    void mainMenuClick(int mx, int my) {
        if (inRect(mx, my, W/2-115, 220, 230, 50)) phase = Phase.LEVEL_SELECT;
        else if (inRect(mx, my, W/2-115, 288, 230, 50)) openSkinEditor();
        else if (inRect(mx, my, W/2-115, 356, 230, 50)) System.exit(0);
    }

    void levelSelectClick(int mx, int my) {
        if (inRect(mx, my, 18, 14, 104, 36)) { phase = Phase.MAIN_MENU; return; }
        // "MAKE LEVEL" button bottom-centre
        if (inRect(mx, my, W/2-80, H-50, 160, 34)) {
            initMakerGrid(); phase = Phase.LEVEL_MAKER; return;
        }
        for (int i = 0; i < LEVELS.length; i++) {
            int bx = 40  + (i % 3) * 290;
            int by = 88  + (i / 3) * 170;
            if (inRect(mx, my, bx, by, 260, 150)) {
                loadLevel(i);
                return;
            }
        }
    }

    void skinEditorClick(int mx, int my) {
        // Tab bar
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (inRect(mx, my, TAB_X[i], TAB_Y, TAB_W, TAB_H)) {
                editorTab = i;
                return;
            }
        }
        // Save & Exit button
        if (inRect(mx, my, W - 200, H - 58, 172, 42)) {
            skin  = copySkin(editSkin);
            phase = Phase.MAIN_MENU;
            return;
        }
        // Presets tab
        if (editorTab == 0) {
            for (int i = 0; i < PRESETS.length; i++) {
                int bx = 26 + (i % 4) * 164;
                int by = 146 + (i / 4) * 108;
                if (inRect(mx, my, bx, by, 152, 96)) {
                    editSkin = copySkin(PRESETS[i]);
                    return;
                }
            }
        }
        // Shapes tab
        if (editorTab == 4) {
            String[] shapes = { "cube", "diamond", "triangle", "ghost", "arrow", "ball" };
            for (int i = 0; i < shapes.length; i++) {
                int bx = 30 + (i % 3) * 195;
                int by = 154 + (i / 3) * 100;
                if (inRect(mx, my, bx, by, 180, 88)) {
                    editSkin.shape = shapes[i];
                    return;
                }
            }
        }
        // Colour slider tabs
        if (editorTab >= 1 && editorTab <= 3) {
            for (int s = 0; s < 3; s++) {
                int sy = SL_Y0 + s * SL_GAP;
                if (inRect(mx, my, SL_X, sy - 5, SL_W, SL_H + 10)) {
                    sliderDrag = s;
                    applySlider(mx);
                    return;
                }
            }
        }
    }

    void applySlider(int mx) {
        float t   = Math.max(0f, Math.min(1f, (float)(mx - SL_X) / SL_W));
        int   val = (int)(t * 255f);

        Color old = (editorTab == 1) ? editSkin.primary
                : (editorTab == 2) ? editSkin.secondary
                  :                    editSkin.trail;

        int r = old.getRed();
        int g = old.getGreen();
        int b = old.getBlue();

        if      (sliderDrag == 0) r = val;
        else if (sliderDrag == 1) g = val;
        else                      b = val;

        if (editorTab == 1) {
            editSkin.primary = new Color(r, g, b);
            editSkin.updateGlow();
        } else if (editorTab == 2) {
            editSkin.secondary = new Color(r, g, b);
        } else {
            editSkin.trail = new Color(r, g, b, 175);
        }
    }

    boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // =========================================================================
    //  PARTICLES
    // =========================================================================
    void updateParticles() {
        particles.removeIf(p -> p.life <= 0);
        // Hard cap to keep frame time bounded
        if (particles.size() > 300) {
            particles.subList(0, particles.size() - 300).clear();
        }
        for (Ptcl p : particles) {
            p.x   += p.vx;
            p.y   += p.vy;
            p.vy  += 0.10f;  // particle gravity
            p.life--;
        }
    }

    /** Ongoing stream of trail particles behind the player. */
    void spawnTrailParticle() {
        if (tick % 3 != 0 || particles.size() > 180) return;
        particles.add(new Ptcl(
                PLAYER_X + PS / 2f, playerY + PS / 2f,
                (rng.nextFloat() - 0.5f) * 1.8f,
                (rng.nextFloat() - 0.5f) * 1.8f,
                12 + rng.nextInt(8), skin.trail, 3 + rng.nextInt(3)));
    }

    void spawnJumpParticles() {
        for (int i = 0; i < 16; i++) {
            particles.add(new Ptcl(
                    PLAYER_X + PS / 2f, playerY + PS,
                    (rng.nextFloat() - 0.5f) * 7f,
                    -(rng.nextFloat() * 5.5f + 1f),
                    26 + rng.nextInt(10), skin.primary, 3 + rng.nextInt(4)));
        }
    }

    void spawnDeathParticles() {
        for (int i = 0; i < 60; i++) {
            double angle = rng.nextDouble() * Math.PI * 2.0;
            float  speed = 2f + rng.nextFloat() * 9f;
            particles.add(new Ptcl(
                    PLAYER_X + PS / 2f, playerY + PS / 2f,
                    (float)(Math.cos(angle) * speed),
                    (float)(Math.sin(angle) * speed),
                    48 + rng.nextInt(28),
                    (i % 2 == 0) ? skin.primary : C_SPIKE,
                    4 + rng.nextInt(5)));
        }
    }

    void spawnPortalFlash() {
        for (int i = 0; i < 24; i++) {
            particles.add(new Ptcl(
                    PLAYER_X + PS / 2f, playerY + PS / 2f,
                    (rng.nextFloat() - 0.5f) * 11f,
                    (rng.nextFloat() - 0.5f) * 11f,
                    28 + rng.nextInt(16), skin.primary, 3 + rng.nextInt(4)));
        }
    }

    void spawnWinParticles() {
        for (int i = 0; i < 100; i++) {
            Color c = (i % 3 == 0) ? C_GOLD : (i % 3 == 1) ? skin.primary : skin.secondary;
            particles.add(new Ptcl(
                    rng.nextFloat() * W, rng.nextFloat() * (H / 2f),
                    (rng.nextFloat() - 0.5f) * 8f,
                    -(rng.nextFloat() * 10f + 1f),
                    75 + rng.nextInt(45), c, 3 + rng.nextInt(6)));
        }
    }

    /** Called every tick while on the win screen — drip-feeds firework particles. */
    void spawnWinTick() {
        if (winTimer % 8 == 0 && particles.size() < 220) {
            Color c = (winTimer % 3 == 0) ? C_GOLD
                    : (winTimer % 3 == 1) ? skin.primary
                      : skin.secondary;
            particles.add(new Ptcl(
                    rng.nextFloat() * W, rng.nextFloat() * (H / 2f),
                    (rng.nextFloat() - 0.5f) * 5f,
                    -(rng.nextFloat() * 8f + 1f),
                    55 + rng.nextInt(35), c, 3 + rng.nextInt(4)));
        }
    }

    // =========================================================================
    //  RENDER FRAME — double-buffered via BufferStrategy
    // =========================================================================
    void renderFrame() {
        java.awt.image.BufferStrategy bs = getBufferStrategy();
        if (bs == null) {
            createBufferStrategy(2);
            return;
        }

        Graphics2D g2 = (Graphics2D) bs.getDrawGraphics();
        try {
            // Antialiasing: OFF during gameplay (too expensive at 60 fps),
            // ON for menus/editors where we have more frame budget.
            boolean inGame = (phase == Phase.PLAYING
                    || phase == Phase.DEAD
                    || phase == Phase.WIN);

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    inGame ? RenderingHints.VALUE_ANTIALIAS_OFF
                            : RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    inGame ? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
                            : RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);
            g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                    RenderingHints.VALUE_COLOR_RENDER_SPEED);

            // Apply camera shake translation
            int shx = (shakeAmt > 0.5f) ? shakeDir * (int) shakeAmt : 0;
            int shy = (shakeAmt > 0.5f) ? (int)(shakeAmt * 0.35f)   : 0;
            if (shx != 0 || shy != 0) g2.translate(shx, shy);

            switch (phase) {
                case MAIN_MENU:    renderMainMenu  (g2); break;
                case LEVEL_SELECT: renderLevelSelect(g2); break;
                case PLAYING:      renderGame       (g2); break;
                case DEAD:         renderDead        (g2); break;
                case WIN:          renderWin         (g2); break;
                case SKIN_EDITOR:  renderSkinEditor  (g2); break;
                case LEVEL_MAKER:  renderLevelMaker  (g2); break;
            }

            // White flash overlay (death / gravity flip)
            if (flashAlpha > 0.02f) {
                g2.setColor(new Color(255, 255, 255, (int)(flashAlpha * 110)));
                g2.fillRect(-shx, -shy, W, H);
            }

        } finally {
            g2.dispose();
        }
        bs.show();
        Toolkit.getDefaultToolkit().sync();  // Linux vsync
    }

    // =========================================================================
    //  BACKGROUND HELPERS
    // =========================================================================
    void renderSky(Graphics2D g2, Color top, Color bot) {
        g2.setPaint(new GradientPaint(0, 0, top, 0, H, bot));
        g2.fillRect(0, 0, W, H);
    }

    void renderStarLayer(Graphics2D g2, List<Star> layer, float parallax) {
        g2.setColor(C_STAR);
        for (Star s : layer) {
            float sx = (s.x - bgOffset * parallax * s.speed + W * 20f) % W;
            int   d  = (int)(s.radius * 2f);
            g2.fillOval((int) sx, (int) s.y, d, d);
        }
    }

    void renderGrid(Graphics2D g2) {
        g2.setColor(C_GRID);
        g2.setStroke(SK1);
        int off = (int)(bgOffset * 0.38f) % TILE;
        for (int x = -off; x < W; x += TILE) g2.drawLine(x, 0, x, H);
        for (int y = 0;    y < H; y += TILE) g2.drawLine(0, y, W, y);
    }

    void renderGround(Graphics2D g2) {
        LevelDef d = LEVELS[currentLevel];

        // Ground fill
        g2.setColor(d.groundDark);
        g2.fillRect(0, GROUND_Y, W, H - GROUND_Y);

        // Ground glow line
        g2.setColor(new Color(d.lineCol.getRed(), d.lineCol.getGreen(), d.lineCol.getBlue(), 50));
        g2.setStroke(SK4);
        g2.drawLine(0, GROUND_Y, W, GROUND_Y);
        g2.setColor(d.lineCol);
        g2.setStroke(SK2);
        g2.drawLine(0, GROUND_Y, W, GROUND_Y);

        // Scrolling vertical stripes on ground
        g2.setColor(d.lineFaint);
        g2.setStroke(SK1);
        int goff = (int)(bgOffset * 0.38f) % TILE;
        for (int x = -goff; x < W; x += TILE) g2.drawLine(x, GROUND_Y, x, H);

        // Ceiling line
        g2.drawLine(0, CEIL_Y, W, CEIL_Y);
    }

    // =========================================================================
    //  RENDER PARTICLES
    // =========================================================================
    void renderParticles(Graphics2D g2) {
        for (Ptcl p : particles) {
            float ratio = p.life / p.maxLife;
            if (ratio < 0.04f) continue;
            int a   = (int)(ratio * 210f);
            int rgb = p.col.getRGB();
            g2.setColor(new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a));
            int sz = Math.max(1, (int)(ratio * p.sz));
            g2.fillRect((int) p.x - sz / 2, (int) p.y - sz / 2, sz, sz);
        }
    }

    // =========================================================================
    //  RENDER OBSTACLES
    // =========================================================================
    void renderObstacles(Graphics2D g2) {
        LevelDef d       = LEVELS[currentLevel];
        float    worldPX = scrollX + PLAYER_X;

        for (Obj o : objs) {
            float sx = o.wx - scrollX;
            if (sx > W + 120f || sx < -120f) continue;  // frustum cull

            switch (o.kind) {

                case SPIKE: {
                    int[] xs = { (int) sx, (int)(sx + o.w / 2f), (int)(sx + o.w) };
                    int[] ys = { (int)(o.wy + o.h), (int) o.wy, (int)(o.wy + o.h) };
                    // Soft glow behind spike
                    int[] gxs = { (int) sx - 4, (int)(sx + o.w / 2f), (int)(sx + o.w + 4) };
                    int[] gys = { (int)(o.wy + o.h + 6), (int)(o.wy - 8), (int)(o.wy + o.h + 6) };
                    g2.setColor(C_SPIKE_GL);
                    g2.fillPolygon(gxs, gys, 3);
                    // Spike body
                    g2.setColor(C_SPIKE);
                    g2.fillPolygon(xs, ys, 3);
                    // Spike outline highlight
                    g2.setColor(new Color(255, 120, 150, 75));
                    g2.setStroke(SK15);
                    g2.drawPolygon(xs, ys, 3);
                    break;
                }

                case BLOCK:
                case TALL_BLOCK: {
                    // Body fill
                    g2.setColor(C_BLK);
                    g2.fillRect((int) sx, (int) o.wy, o.w, o.h);
                    // Level-coloured outline
                    g2.setColor(d.lineCol);
                    g2.setStroke(SK2);
                    g2.drawRect((int) sx, (int) o.wy, o.w, o.h);
                    // Inner inset decoration
                    g2.setColor(d.lineFaint);
                    g2.setStroke(SK1);
                    if (o.w > 12 && o.h > 12)
                        g2.drawRect((int) sx + 4, (int) o.wy + 4, o.w - 8, o.h - 8);
                    // Top-edge highlight
                    g2.setColor(new Color(d.lineCol.getRed(), d.lineCol.getGreen(), d.lineCol.getBlue(), 24));
                    g2.fillRect((int) sx, (int) o.wy, o.w, 4);
                    // Tall-block mid-line
                    if (o.kind == ObjKind.TALL_BLOCK) {
                        g2.setColor(d.lineFaint);
                        g2.setStroke(SK1);
                        g2.drawLine((int) sx, (int) o.wy + o.h / 2,
                                (int) sx + o.w, (int) o.wy + o.h / 2);
                    }
                    break;
                }

                case ORB: {
                    if (o.used) continue;
                    float pulse = (float)(0.68 + 0.32 * Math.sin(tick * 0.19 + o.wx * 0.01));
                    // Outer glow
                    int gr = (int)(26f * pulse);
                    g2.setColor(new Color(255, 215, 50, 30));
                    g2.fillOval((int) sx - gr/2, (int) o.wy - gr/2, o.w + gr, o.h + gr);
                    // Mid ring
                    gr = (int)(12f * pulse);
                    g2.setColor(new Color(255, 215, 50, 62));
                    g2.fillOval((int) sx - gr/2, (int) o.wy - gr/2, o.w + gr, o.h + gr);
                    // Body
                    g2.setColor(new Color(40, 32, 5));
                    g2.fillOval((int) sx, (int) o.wy, o.w, o.h);
                    g2.setColor(C_ORB);
                    g2.setStroke(SK2);
                    g2.drawOval((int) sx, (int) o.wy, o.w, o.h);
                    // Inner ring
                    g2.setColor(new Color(255, 230, 80, 155));
                    g2.setStroke(SK1);
                    g2.drawOval((int) sx + 6, (int) o.wy + 6, o.w - 12, o.h - 12);
                    // Shine dot
                    g2.setColor(new Color(255, 255, 200, 200));
                    g2.fillOval((int) sx + 8, (int) o.wy + 6, 7, 5);
                    // "CLICK" label when near
                    if (Math.abs(worldPX - (o.wx + 15f)) < 100f) {
                        g2.setFont(FNT_SM);
                        g2.setColor(new Color(255, 215, 50, 215));
                        g2.drawString("CLICK", (int) sx - 4, (int) o.wy - 10);
                    }
                    break;
                }

                case PAD: {
                    if (o.used) continue;
                    g2.setColor(new Color(0, 50, 35));
                    g2.fillRect((int) sx, (int) o.wy, o.w, o.h);
                    g2.setColor(C_PAD);
                    g2.setStroke(SK2);
                    g2.drawRect((int) sx, (int) o.wy, o.w, o.h);
                    // Arrow teeth
                    for (int xi = 6; xi < o.w - 6; xi += 10) {
                        g2.fillPolygon(
                                new int[]{ (int) sx + xi, (int) sx + xi + 4, (int) sx + xi + 8 },
                                new int[]{ (int) o.wy,    (int) o.wy - 9,    (int) o.wy         }, 3);
                    }
                    break;
                }
            }
        }
    }

    // =========================================================================
    //  RENDER PORTALS
    // =========================================================================
    void renderPortals(Graphics2D g2) {
        for (Portal p : portals) {
            float sx = p.wx - scrollX;
            if (sx > W + 90f || sx < -90f) continue;
            if (!p.used) renderPortal(g2, sx, p.kind);
        }
    }

    void renderPortal(Graphics2D g2, float sx, PortalKind k) {
        int    pw = 44, ph = 76;
        int    py = GROUND_Y - ph;
        Color  col;
        String lbl;

        switch (k) {
            case MODE_CUBE:   col = PC_CUBE;   lbl = "CUBE";  break;
            case MODE_SHIP:   col = PC_SHIP;   lbl = "SHIP";  break;
            case MODE_BALL:   col = PC_BALL;   lbl = "BALL";  break;
            case MODE_UFO:    col = PC_UFO;    lbl = "UFO";   break;
            case MODE_WAVE:   col = PC_WAVE;   lbl = "WAVE";  break;
            case MODE_ROBOT:  col = PC_ROBOT;  lbl = "BOT";   break;
            case SPEED_SLOW:  col = PC_SLOW;   lbl = "x0.6"; ph = 32; py = GROUND_Y - 40; break;
            case SPEED_NORMAL:col = C_WHITE;   lbl = "x1.0"; ph = 32; py = GROUND_Y - 40; break;
            case SPEED_FAST:  col = PC_FAST;   lbl = "x1.5"; ph = 32; py = GROUND_Y - 40; break;
            case SPEED_VFAST: col = PC_VFAST;  lbl = "x2.2"; ph = 32; py = GROUND_Y - 40; break;
            case GRAVITY_FLIP:col = PC_GFLIP;  lbl = "FLIP"; ph = 64; py = (GROUND_Y - 64)/2; break;
            case GRAVITY_NORM:col = PC_GNORM;  lbl = "NORM"; ph = 64; py = (GROUND_Y - 64)/2; break;
            default:          col = Color.GRAY; lbl = "?";   break;
        }

        // Animated glow rings (3 layers, pulsing)
        for (int ring = 2; ring >= 0; ring--) {
            int alpha = (int)(14 + 9 * Math.sin(tick * 0.13 + ring * 1.2));
            alpha = Math.max(0, Math.min(255, alpha));
            g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), alpha));
            int pad = (ring + 1) * 7;
            g2.fillRoundRect((int) sx - pad, py - pad, pw + pad*2, ph + pad*2, 16, 16);
        }

        // Dark body
        g2.setColor(new Color(col.getRed()/5, col.getGreen()/5, col.getBlue()/5));
        g2.fillRoundRect((int) sx, py, pw, ph, 10, 10);
        g2.setColor(col);
        g2.setStroke(SK2);
        g2.drawRoundRect((int) sx, py, pw, ph, 10, 10);

        // Scrolling inner lines
        int lineOff = tick * 2 % (ph > 36 ? ph : 32);
        g2.setColor(new Color(255, 255, 255, 88));
        g2.setStroke(SK1);
        for (int ly = py + lineOff; ly < py + ph; ly += 8)
            g2.drawLine((int) sx + 5, ly, (int) sx + pw - 5, ly);

        // Label
        g2.setFont(FNT_SM);
        g2.setColor(C_WHITE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lbl, (int) sx + (pw - fm.stringWidth(lbl)) / 2, py + ph/2 + 5);
    }

    // =========================================================================
    //  RENDER END FLAG
    // =========================================================================
    void renderEndFlag(Graphics2D g2) {
        float sx = endFlagWX - scrollX;
        if (sx < -90f || sx > W + 90f) return;

        float pulse = (float)(0.5 + 0.5 * Math.sin(tick * 0.10));

        // Glow column
        g2.setColor(new Color(255, 215, 0, (int)(18 + 14 * pulse)));
        g2.fillRect((int) sx - 20, GROUND_Y - 132, 56, 132);

        // Pole
        g2.setColor(Color.LIGHT_GRAY);
        g2.setStroke(SK2);
        g2.drawLine((int) sx, GROUND_Y, (int) sx, GROUND_Y - 126);

        // Flag
        int[] fxs = { (int) sx, (int)(sx + 56), (int) sx };
        int[] fys = { GROUND_Y - 126, GROUND_Y - 100, GROUND_Y - 74 };
        g2.setColor(C_GOLD);
        g2.fillPolygon(fxs, fys, 3);
        g2.setColor(new Color(195, 160, 0));
        g2.setStroke(SK1);
        g2.drawPolygon(fxs, fys, 3);

        g2.setFont(FNT_SM);
        g2.setColor(new Color(255, 255, 255, 195));
        g2.drawString("END", (int) sx - 8, GROUND_Y - 132);
    }

    // =========================================================================
    //  RENDER PLAYER
    // =========================================================================
    void renderPlayer(Graphics2D g2) {
        AffineTransform savedTransform = g2.getTransform();

        g2.translate(PLAYER_X + PS / 2f, playerY + PS / 2f);
        g2.rotate(rotation);

        // Outer glow disc
        g2.setColor(skin.glowColor);
        g2.fillOval(-PS/2 - 14, -PS/2 - 14, PS + 28, PS + 28);

        // Subtle pulsing secondary glow
        float gp = (float)(0.5 + 0.5 * Math.sin(tick * 0.13));
        g2.setColor(new Color(skin.primary.getRed(), skin.primary.getGreen(),
                skin.primary.getBlue(), (int)(14 * gp)));
        g2.fillOval(-PS/2 - 20, -PS/2 - 20, PS + 40, PS + 40);

        renderPlayerShape(g2, skin, PS);

        g2.setTransform(savedTransform);
    }

    /**
     * Draws the player (or a preview) shape centred at origin.
     * @param ps  size in pixels (PS for real player, smaller for previews)
     */
    void renderPlayerShape(Graphics2D g2, Skin s, int ps) {
        g2.setColor(s.primary);

        switch (s.shape) {
            case "cube": {
                g2.fillRect(-ps/2, -ps/2, ps, ps);
                g2.setColor(s.secondary);
                g2.setStroke(SK2);
                g2.drawRect(-ps/2 + 4, -ps/2 + 4, ps - 8, ps - 8);
                g2.fillRect(-6, -6, 12, 12);
                // Corner highlights
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fillRect(-ps/2, -ps/2, ps, 3);
                g2.fillRect(-ps/2, -ps/2, 3, ps);
                break;
            }
            case "diamond": {
                int[] xs = { 0, ps/2,  0, -ps/2 };
                int[] ys = { -ps/2, 0, ps/2, 0 };
                g2.fillPolygon(xs, ys, 4);
                g2.setColor(s.secondary);
                g2.setStroke(SK2);
                g2.drawPolygon(xs, ys, 4);
                int[] xs2 = { 0, ps/5,  0, -ps/5 };
                int[] ys2 = { -ps/5, 0, ps/5, 0 };
                g2.fillPolygon(xs2, ys2, 4);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.drawLine(0, -ps/2, -ps/2, 0);
                g2.drawLine(0, -ps/2,  ps/2, 0);
                break;
            }
            case "triangle": {
                int[] xs = { 0, ps/2, -ps/2 };
                int[] ys = { -ps/2, ps/2, ps/2 };
                g2.fillPolygon(xs, ys, 3);
                g2.setColor(s.secondary);
                g2.setStroke(SK2);
                g2.drawPolygon(xs, ys, 3);
                g2.setColor(new Color(255, 255, 255, 50));
                g2.drawLine(0, -ps/2, -ps/4, ps/4);
                break;
            }
            case "ghost": {
                g2.fillArc(-ps/2, -ps/2, ps, ps, 0, 180);
                int[] waveX = { -ps/2, -ps/3, -ps/6, 0, ps/6, ps/3, ps/2 };
                int[] waveY = { 0, ps/3, 0, ps/3, 0, ps/3, 0 };
                g2.fillPolygon(waveX, waveY, 7);
                g2.setColor(new Color(20, 20, 50));
                g2.fillOval(-ps/4 - 3, -ps/4 - 2, 8, 8);
                g2.fillOval( ps/4 - 5, -ps/4 - 2, 8, 8);
                g2.setColor(new Color(200, 200, 255, 80));
                g2.drawLine(-ps/4, -ps/3, ps/4, -ps/3);
                break;
            }
            case "arrow": {
                int[] ax = { -ps/2, -ps/2,  0, ps/2, ps/2,  ps/4, -ps/4 };
                int[] ay = { -ps/6,  ps/6, ps/2, ps/6, -ps/6, -ps/2, -ps/2 };
                g2.fillPolygon(ax, ay, 7);
                g2.setColor(s.secondary);
                g2.setStroke(SK15);
                g2.drawPolygon(ax, ay, 7);
                g2.setColor(new Color(255, 255, 255, 55));
                g2.drawLine(0, -ps/3, 0, ps/3);
                break;
            }
            case "ball": {
                g2.fillOval(-ps/2, -ps/2, ps, ps);
                g2.setColor(s.secondary);
                g2.setStroke(SK2);
                g2.drawOval(-ps/2 + 3, -ps/2 + 3, ps - 6, ps - 6);
                g2.fillOval(-5, -5, 10, 10);
                g2.setColor(new Color(255, 255, 255, 55));
                g2.drawArc(-ps/2 + 5, -ps/2 + 5, ps - 10, ps - 10, 210, 120);
                break;
            }
            default: {
                // Fallback: simple square
                g2.fillRect(-ps/2, -ps/2, ps, ps);
                break;
            }
        }
    }

    // =========================================================================
    //  RENDER HUD  (progress bar, score, mode, attempt counter)
    // =========================================================================
    void renderHUD(Graphics2D g2) {
        // ── Progress bar ─────────────────────────────────────────────────────
        int barX = 10, barY = 10, barW = W - 20, barH = 14;
        float pct = Math.min(1f, scrollX / Math.max(1f, endFlagWX - W));

        g2.setColor(C_BARBG);
        g2.fillRect(barX, barY, barW, barH);

        if (pct > 0f) {
            g2.setPaint(new GradientPaint(
                    barX, barY, skin.primary.darker(),
                    barX + (int)(barW * pct), barY, skin.primary));
            g2.fillRect(barX, barY, (int)(barW * pct), barH);
        }
        g2.setColor(new Color(255, 255, 255, 30));
        g2.setStroke(SK1);
        g2.drawRect(barX, barY, barW, barH);

        // Percentage text centred on bar
        g2.setFont(FNT_SM);
        g2.setColor(C_WHITE);
        String pctStr = (int)(pct * 100) + "%";
        FontMetrics pfm = g2.getFontMetrics();
        g2.drawString(pctStr, W/2 - pfm.stringWidth(pctStr)/2, barY + barH - 1);

        // ── Bottom bar ────────────────────────────────────────────────────────
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRect(0, H - 26, W, 26);

        // Mode badge (left)
        g2.setFont(FNT_MD);
        g2.setColor(skin.primary);
        g2.drawString("[" + gameMode.name() + "]", 10, H - 9);

        // Score (centre)
        g2.setFont(FNT_MD);
        g2.setColor(C_GOLD);
        String scoreStr = String.format("%07d", score);
        FontMetrics scfm = g2.getFontMetrics();
        g2.drawString(scoreStr, W/2 - scfm.stringWidth(scoreStr)/2, H - 9);

        // Combo multiplier (just right of score)
        if (combo > 1) {
            int ca = Math.min(255, combo * 50);
            g2.setFont(FNT_SM);
            g2.setColor(new Color(255, 220, 55, ca));
            g2.drawString("x" + combo,
                    W/2 + scfm.stringWidth(scoreStr)/2 + 10, H - 9);
        }

        // Level name + speed (right)
        g2.setFont(FNT_TINY);
        g2.setColor(C_DIM);
        String rightInfo = LEVELS[currentLevel].name
                + "  " + String.format("%.1fx", scrollSpeed / LEVELS[currentLevel].baseSpeed);
        FontMetrics rfm = g2.getFontMetrics();
        g2.drawString(rightInfo, W - rfm.stringWidth(rightInfo) - 10, H - 9);

        // Attempt counter (top-right, subtle)
        g2.setFont(FNT_TINY);
        g2.setColor(C_FAINT);
        String attStr = "attempt " + totalAttempts[currentLevel];
        g2.drawString(attStr, W - g2.getFontMetrics().stringWidth(attStr) - 10, 28);

        // Practice mode indicator
        if (practiceMode) {
            g2.setFont(FNT_SM);
            g2.setColor(new Color(90, 255, 140, 215));
            g2.drawString("PRACTICE " + (cpScrollX >= 0f ? "[Z\u2713]" : "[Z=checkpoint]"),
                    10, 36);
        }

        // Gravity flip indicator
        if (gravFlipped) {
            g2.setFont(FNT_SM);
            g2.setColor(new Color(255, 255, 80, 220));
            g2.drawString("INVERTED", W - 90, 36);
        }

        // Orb counter
        if (orbsCollected > 0) {
            g2.setFont(FNT_TINY);
            g2.setColor(C_ORB);
            g2.drawString("orbs:" + orbsCollected, 10, H - 28);
        }
    }

    // =========================================================================
    //  RENDER GAMEPLAY SCENE
    // =========================================================================
    void renderGame(Graphics2D g2) {
        LevelDef d = LEVELS[currentLevel];
        renderSky       (g2, d.skyTop, d.skyBot);
        renderStarLayer (g2, starsFar,  0.20f);
        renderStarLayer (g2, starsNear, 0.48f);
        renderGrid      (g2);
        renderParticles (g2);
        renderGround    (g2);
        renderObstacles (g2);
        renderPortals   (g2);
        renderEndFlag   (g2);
        renderPlayer    (g2);
        renderHUD       (g2);
    }

    // =========================================================================
    //  RENDER MAIN MENU
    // =========================================================================
    void renderMainMenu(Graphics2D g2) {
        renderSky(g2, new Color(3, 3, 18), new Color(7, 7, 45));
        renderStarLayer(g2, starsFar,  0.04f);
        renderStarLayer(g2, starsNear, 0.10f);

        // Title with multi-layer glow
        String title = "GEOMETRY RUN";
        g2.setFont(FNT_XXL);
        FontMetrics fm = g2.getFontMetrics();
        int tx = (W - fm.stringWidth(title)) / 2;
        int ty = H / 2 - 90;

        Color tc = skin.primary;
        for (int i = 16; i > 0; i -= 2) {
            g2.setColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), (int)(3.2f * i)));
            g2.drawString(title, tx, ty + i / 4);
        }
        g2.setColor(tc);
        g2.drawString(title, tx, ty);

        // Subtitle
        g2.setFont(FNT_SM);
        g2.setColor(C_DIM);
        String sub = "5 Levels  •  6 Modes  •  Orbs  •  Practice  •  Custom Skins";
        FontMetrics sfm = g2.getFontMetrics();
        g2.drawString(sub, (W - sfm.stringWidth(sub)) / 2, ty + 46);

        // Animated mini player spinning below the title
        AffineTransform saved = g2.getTransform();
        g2.translate(W / 2f, ty + 90f);
        g2.rotate(tick * 0.045);
        g2.setColor(skin.glowColor);
        g2.fillOval(-20, -20, 40, 40);
        renderPlayerShape(g2, skin, 28);
        g2.setTransform(saved);

        // Buttons
        renderButton(g2, W/2-115, 220, 230, 50, "\u25b6  PLAY",  new Color(0, 205, 168), true);
        renderButton(g2, W/2-115, 288, 230, 50, "\u2736  SKINS", tc,                     false);
        renderButton(g2, W/2-115, 356, 230, 50, "\u2715  QUIT",  new Color(215, 48, 48), false);

        // Controls hint at bottom
        g2.setFont(FNT_TINY);
        g2.setColor(C_FAINT);
        String ctrl = "SPACE=jump   R=restart   P=practice   Z=checkpoint   S=skins   ESC=quit";
        FontMetrics cfm = g2.getFontMetrics();
        g2.drawString(ctrl, (W - cfm.stringWidth(ctrl)) / 2, H - 14);

        g2.setFont(FNT_TINY);
        g2.setColor(C_FAINT);
        String si = "Skin: " + skin.name + "   Shape: " + skin.shape;
        FontMetrics sfm2 = g2.getFontMetrics();
        g2.drawString(si, (W - sfm2.stringWidth(si)) / 2, H - 28);
    }

    // =========================================================================
    //  RENDER BUTTON  (reusable)
    // =========================================================================
    void renderButton(Graphics2D g2, int x, int y, int w, int h,
                      String label, Color c, boolean filled) {
        if (filled) {
            g2.setPaint(new GradientPaint(x, y, c.darker(), x, y + h, c));
        } else {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 20));
        }
        g2.fillRoundRect(x, y, w, h, 14, 14);
        g2.setColor(c);
        g2.setStroke(SK2);
        g2.drawRoundRect(x, y, w, h, 14, 14);
        g2.setFont(FNT_BTN);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(filled ? Color.BLACK : c);
        g2.drawString(label, x + (w - fm.stringWidth(label)) / 2, y + h / 2 + 6);
    }

    // =========================================================================
    //  RENDER LEVEL SELECT
    // =========================================================================
    void renderLevelSelect(Graphics2D g2) {
        renderSky(g2, new Color(3, 3, 18), new Color(7, 7, 45));
        renderStarLayer(g2, starsFar, 0.06f);

        // Header
        g2.setFont(FNT_LG);
        g2.setColor(skin.primary);
        String hdr = "SELECT LEVEL";
        FontMetrics hfm = g2.getFontMetrics();
        g2.drawString(hdr, (W - hfm.stringWidth(hdr)) / 2, 58);

        for (int i = 0; i < LEVELS.length; i++) {
            LevelDef lv = LEVELS[i];
            int bx = 40  + (i % 3) * 290;
            int by = 88  + (i / 3) * 170;
            int bw = 260, bh = 150;

            // Card background gradient
            g2.setPaint(new GradientPaint(bx, by, lv.skyTop, bx + bw, by + bh, lv.skyBot));
            g2.fillRoundRect(bx, by, bw, bh, 16, 16);

            // Border — gold if completed
            g2.setColor(bestPct[i] == 100 ? C_GOLD : lv.lineCol);
            g2.setStroke(bestPct[i] == 100 ? SK3 : SK2);
            g2.drawRoundRect(bx, by, bw, bh, 16, 16);

            // Level number badge
            g2.setColor(lv.lineCol);
            g2.fillOval(bx + 8, by + 8, 36, 36);
            g2.setFont(new Font("Courier New", Font.BOLD, 16));
            g2.setColor(lv.skyTop);
            g2.drawString(String.valueOf(i + 1),
                    bx + 13 + (i + 1 >= 10 ? -4 : 0), by + 31);

            // Level name
            g2.setFont(new Font("Courier New", Font.BOLD, 15));
            g2.setColor(C_WHITE);
            g2.drawString(lv.name, bx + 54, by + 30);

            // Difficulty badge
            Color diffCol = lv.difficulty.equals("EASY")   ? new Color( 80, 255, 120)
                    : lv.difficulty.equals("NORMAL") ? new Color(255, 210,   0)
                      :                                   new Color(255,  80,  80);
            g2.setFont(FNT_TINY);
            g2.setColor(diffCol);
            g2.drawString(lv.difficulty, bx + 54, by + 45);

            // Attempt count
            g2.setFont(FNT_TINY);
            g2.setColor(C_FAINT);
            g2.drawString("Attempts: " + totalAttempts[i], bx + 54, by + 58);

            // Mode tags
            g2.setFont(FNT_TINY);
            g2.setColor(new Color(255, 255, 255, 130));
            String tags = "CUBE"
                    + (i >= 1 ? " SHIP" : "")
                    + (i >= 2 ? " GRAV" : "")
                    + (i >= 3 ? " BALL" : "")
                    + (i >= 4 ? " WAVE" : "");
            g2.drawString(tags, bx + 12, by + 108);

            // Best % label
            g2.setFont(FNT_SM);
            g2.setColor(C_DIM);
            g2.drawString("Best: " + bestPct[i] + "%", bx + 12, by + 72);

            // Progress bar
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRect(bx + 12, by + 78, 236, 8);
            if (bestPct[i] > 0) {
                g2.setPaint(new GradientPaint(bx + 12, by + 78, lv.lineCol.darker(),
                        bx + 248, by + 78, lv.lineCol));
                g2.fillRect(bx + 12, by + 78, (int)(236 * bestPct[i] / 100.0), 8);
            }

            // Stars
            String stars = bestPct[i] == 100 ? "\u2605\u2605\u2605"
                    : bestPct[i] >= 50  ? "\u2605\u2605\u2606"
                      : bestPct[i] >  0   ? "\u2605\u2606\u2606"
                        :                     "\u2606\u2606\u2606";
            Color starCol = bestPct[i] == 100 ? C_GOLD
                    : bestPct[i] >= 50  ? new Color(195, 195, 90)
                      :                     new Color(140, 140, 140);
            g2.setFont(new Font("Courier New", Font.BOLD, 20));
            g2.setColor(starCol);
            g2.drawString(stars, bx + 168, by + 128);
        }

        renderButton(g2, 18, 14, 104, 36, "\u2190 BACK", new Color(175, 175, 175), false);
        renderButton(g2, W/2-80, H-50, 160, 34, "\u270e MAKE LEVEL", new Color(255,180,0), false);
    }

    // =========================================================================
    //  RENDER DEAD SCREEN
    // =========================================================================
    void renderDead(Graphics2D g2) {
        LevelDef d = LEVELS[currentLevel];
        renderSky      (g2, d.skyTop, d.skyBot);
        renderGrid     (g2);
        renderParticles(g2);
        renderGround   (g2);
        renderObstacles(g2);

        // Dark overlay
        g2.setColor(C_OVERLAY);
        g2.fillRect(0, 0, W, H);

        // Panel
        int bx = (W - 400) / 2, by = (H - 265) / 2;
        g2.setColor(C_PANEL);
        g2.fillRoundRect(bx, by, 400, 265, 20, 20);
        g2.setColor(C_SPIKE);
        g2.setStroke(SK3);
        g2.drawRoundRect(bx, by, 400, 265, 20, 20);

        // "GAME OVER" title
        g2.setFont(FNT_XL);
        g2.setColor(C_SPIKE);
        FontMetrics gfm = g2.getFontMetrics();
        String go = "GAME OVER";
        g2.drawString(go, bx + (400 - gfm.stringWidth(go)) / 2, by + 64);

        // Death percentage (big)
        int dpct = completionPct();
        g2.setFont(new Font("Courier New", Font.BOLD, 48));
        g2.setColor(C_WHITE);
        String dpctStr = dpct + "%";
        FontMetrics dfm = g2.getFontMetrics();
        g2.drawString(dpctStr, bx + (400 - dfm.stringWidth(dpctStr)) / 2, by + 132);

        // Progress bar showing death point
        g2.setColor(new Color(0, 0, 0, 75));
        g2.fillRect(bx + 22, by + 147, 356, 8);
        g2.setColor(C_SPIKE);
        g2.fillRect(bx + 22, by + 147, (int)(356.0 * dpct / 100.0), 8);

        // Gold marker on bar showing personal best
        if (bestPct[currentLevel] > 0) {
            int bm = bx + 22 + (int)(356.0 * bestPct[currentLevel] / 100.0);
            g2.setColor(C_GOLD);
            g2.setStroke(SK2);
            g2.drawLine(bm, by + 145, bm, by + 157);
        }

        // Stats line
        g2.setFont(FNT_MD);
        g2.setColor(C_GOLD);
        String stats = "Best: " + bestPct[currentLevel] + "%"
                + "   Orbs: " + orbsCollected
                + "   Attempts: " + totalAttempts[currentLevel];
        FontMetrics stfm = g2.getFontMetrics();
        g2.drawString(stats, bx + (400 - stfm.stringWidth(stats)) / 2, by + 183);

        // Hint text
        g2.setFont(FNT_TINY);
        g2.setColor(C_DIM);
        String hint = "SPACE/R \u2013 retry    ESC \u2013 menu";
        FontMetrics hfm = g2.getFontMetrics();
        g2.drawString(hint, bx + (400 - hfm.stringWidth(hint)) / 2, by + 218);

        // Menu button
        renderButton(g2, W/2 - 100, by + 228, 200, 34, "Menu (ESC)",
                new Color(100, 100, 140), false);
    }

    // =========================================================================
    //  RENDER WIN SCREEN
    // =========================================================================
    void renderWin(Graphics2D g2) {
        LevelDef d = LEVELS[currentLevel];
        renderSky      (g2, d.skyTop, d.skyBot);
        renderParticles(g2);

        g2.setColor(new Color(0, 0, 0, 110));
        g2.fillRect(0, 0, W, H);

        // "LEVEL COMPLETE!" with glow
        String win = "LEVEL COMPLETE!";
        g2.setFont(FNT_XL);
        FontMetrics wfm = g2.getFontMetrics();
        int wx2 = (W - wfm.stringWidth(win)) / 2;
        for (int i = 12; i > 0; i -= 2) {
            g2.setColor(new Color(skin.primary.getRed(), skin.primary.getGreen(),
                    skin.primary.getBlue(), (int)(4.0f * i)));
            g2.drawString(win, wx2, H/2 - 52 + i/3);
        }
        g2.setColor(skin.primary);
        g2.drawString(win, wx2, H/2 - 52);

        // Level name
        g2.setFont(new Font("Courier New", Font.BOLD, 26));
        g2.setColor(C_GOLD);
        String lvName = "\u2605  " + LEVELS[currentLevel].name + "  \u2605";
        FontMetrics lfm = g2.getFontMetrics();
        g2.drawString(lvName, (W - lfm.stringWidth(lvName)) / 2, H/2 + 8);

        // Score
        g2.setFont(FNT_LG);
        g2.setColor(C_DIM);
        String scoreStr = "Score: " + String.format("%,d", score);
        FontMetrics scfm = g2.getFontMetrics();
        g2.drawString(scoreStr, (W - scfm.stringWidth(scoreStr)) / 2, H/2 + 46);

        // Stars (always 3 for completing)
        g2.setFont(new Font("Courier New", Font.BOLD, 32));
        g2.setColor(C_GOLD);
        String stars = "\u2605\u2605\u2605";
        FontMetrics stfm = g2.getFontMetrics();
        g2.drawString(stars, (W - stfm.stringWidth(stars)) / 2, H/2 + 82);

        // First-try bonus
        if (totalAttempts[currentLevel] == 1) {
            g2.setFont(FNT_SM);
            g2.setColor(new Color(255, 200, 0, 210));
            String bonus = "+3000 FIRST TRY BONUS!";
            FontMetrics bfm = g2.getFontMetrics();
            g2.drawString(bonus, (W - bfm.stringWidth(bonus)) / 2, H/2 - 80);
        }

        // Continue hint
        g2.setFont(FNT_SM);
        g2.setColor(C_DIM);
        String cont = (currentLevel < LEVELS.length - 1)
                ? "SPACE \u2013 Next Level    ESC \u2013 Select"
                : "All done!   ESC \u2013 Menu";
        FontMetrics cfm = g2.getFontMetrics();
        g2.drawString(cont, (W - cfm.stringWidth(cont)) / 2, H/2 + 113);
    }

    // =========================================================================
    //  RENDER SKIN EDITOR
    // =========================================================================
    void renderSkinEditor(Graphics2D g2) {
        renderSky(g2, new Color(3, 3, 18), new Color(9, 9, 42));

        // Header
        g2.setFont(new Font("Courier New", Font.BOLD, 28));
        g2.setColor(editSkin.primary);
        g2.drawString("SKIN CUSTOMISER", 28, 50);

        // Tab bar
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean active = (editorTab == i);
            Color tc = active ? editSkin.primary : new Color(95, 95, 135);

            g2.setColor(active
                    ? new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 26)
                    : new Color(12, 12, 38));
            g2.fillRoundRect(TAB_X[i], TAB_Y, TAB_W, TAB_H, 8, 8);

            g2.setColor(tc);
            g2.setStroke(active ? SK2 : SK1);
            g2.drawRoundRect(TAB_X[i], TAB_Y, TAB_W, TAB_H, 8, 8);

            g2.setFont(FNT_SM);
            FontMetrics tfm = g2.getFontMetrics();
            g2.drawString(TAB_NAMES[i],
                    TAB_X[i] + (TAB_W - tfm.stringWidth(TAB_NAMES[i])) / 2,
                    TAB_Y + 20);
        }

        // Content panel background
        g2.setColor(new Color(8, 8, 34));
        g2.fillRoundRect(14, 112, W - 225, H - 150, 12, 12);
        g2.setColor(new Color(38, 38, 85));
        g2.setStroke(SK1);
        g2.drawRoundRect(14, 112, W - 225, H - 150, 12, 12);

        // Tab content
        switch (editorTab) {
            case 0: renderEditorPresets(g2);                              break;
            case 1: renderColorSliders (g2, "PRIMARY COLOR",   editSkin.primary);    break;
            case 2: renderColorSliders (g2, "SECONDARY COLOR", editSkin.secondary);  break;
            case 3: renderColorSliders (g2, "TRAIL COLOR",
                    new Color(editSkin.trail.getRed(),
                            editSkin.trail.getGreen(),
                            editSkin.trail.getBlue()));              break;
            case 4: renderEditorShapes(g2);                               break;
        }

        renderSkinPreview(g2);

        renderButton(g2, W - 200, H - 58, 172, 42, "\u2714  SAVE & EXIT", editSkin.primary, true);

        g2.setFont(FNT_TINY);
        g2.setColor(C_FAINT);
        g2.drawString("ENTER or SAVE to apply  \u2022  ESC to cancel", 26, H - 10);
    }

    void renderEditorPresets(Graphics2D g2) {
        g2.setFont(FNT_SM);
        g2.setColor(C_DIM);
        g2.drawString("Choose a preset:", 28, 140);

        for (int i = 0; i < PRESETS.length; i++) {
            Skin pr = PRESETS[i];
            int  bx = 26 + (i % 4) * 164;
            int  by = 148 + (i / 4) * 108;

            g2.setPaint(new GradientPaint(bx, by, pr.primary.darker().darker(),
                    bx + 150, by + 96, pr.primary.darker()));
            g2.fillRoundRect(bx, by, 150, 96, 10, 10);

            boolean sel = editSkin.name.equals(pr.name);
            g2.setColor(sel ? pr.primary
                    : new Color(pr.primary.getRed(), pr.primary.getGreen(),
                    pr.primary.getBlue(), 85));
            g2.setStroke(sel ? SK2 : SK1);
            g2.drawRoundRect(bx, by, 150, 96, 10, 10);

            g2.setFont(FNT_MD);
            g2.setColor(pr.primary);
            g2.drawString(pr.name, bx + 10, by + 24);

            g2.setFont(FNT_TINY);
            g2.setColor(C_DIM);
            g2.drawString(pr.shape.toUpperCase(), bx + 10, by + 40);

            // Colour swatches
            g2.setColor(pr.primary);   g2.fillRect(bx + 10, by + 54, 16, 12);
            g2.setColor(pr.secondary); g2.fillRect(bx + 30, by + 54, 16, 12);
            g2.setColor(pr.trail);     g2.fillRect(bx + 50, by + 54, 16, 12);
        }
    }

    void renderColorSliders(Graphics2D g2, String title, Color cur) {
        g2.setFont(FNT_MD);
        g2.setColor(C_DIM);
        g2.drawString(title, 32, 140);

        String[] lbls = { "RED", "GREEN", "BLUE" };
        int[]    vals = { cur.getRed(), cur.getGreen(), cur.getBlue() };
        Color[]  cols = {
                new Color(255, 50, 50),
                new Color(50, 210, 50),
                new Color(50, 90, 255)
        };

        for (int s = 0; s < 3; s++) {
            int sy = SL_Y0 + s * SL_GAP;

            g2.setFont(FNT_SM);
            g2.setColor(cols[s]);
            g2.drawString(lbls[s], 32, sy + 13);

            // Track background
            g2.setColor(new Color(20, 20, 48));
            g2.fillRoundRect(SL_X, sy, SL_W, SL_H, SL_H, SL_H);

            // Track fill
            float t = vals[s] / 255f;
            g2.setPaint(new GradientPaint(SL_X, sy, cols[s].darker().darker(),
                    SL_X + SL_W, sy, cols[s]));
            g2.fillRoundRect(SL_X, sy, (int)(SL_W * t), SL_H, SL_H, SL_H);

            // Track outline
            g2.setColor(new Color(62, 62, 102));
            g2.setStroke(SK1);
            g2.drawRoundRect(SL_X, sy, SL_W, SL_H, SL_H, SL_H);

            // Handle
            int hx = SL_X + (int)(SL_W * t) - SL_H / 2;
            g2.setColor(C_WHITE);
            g2.fillOval(hx, sy - 3, SL_H + 6, SL_H + 6);
            g2.setColor(cols[s]);
            g2.setStroke(SK2);
            g2.drawOval(hx, sy - 3, SL_H + 6, SL_H + 6);

            // Value label
            g2.setFont(FNT_SM);
            g2.setColor(C_WHITE);
            g2.drawString(String.valueOf(vals[s]), SL_X + SL_W + 14, sy + 13);
        }

        // Colour swatch + hex code
        g2.setColor(cur);
        g2.fillRoundRect(32, 292, 76, 36, 8, 8);
        g2.setColor(C_FAINT);
        g2.setStroke(SK1);
        g2.drawRoundRect(32, 292, 76, 36, 8, 8);
        g2.setFont(FNT_TINY);
        g2.setColor(C_DIM);
        g2.drawString(String.format("#%02X%02X%02X",
                cur.getRed(), cur.getGreen(), cur.getBlue()), 118, 314);
    }

    void renderEditorShapes(Graphics2D g2) {
        g2.setFont(FNT_MD);
        g2.setColor(C_DIM);
        g2.drawString("Choose a shape:", 32, 140);

        String[] shapes = { "cube", "diamond", "triangle", "ghost", "arrow", "ball" };
        for (int i = 0; i < shapes.length; i++) {
            int     bx  = 30 + (i % 3) * 195;
            int     by  = 154 + (i / 3) * 100;
            boolean sel = editSkin.shape.equals(shapes[i]);

            g2.setColor(sel
                    ? new Color(editSkin.primary.getRed(), editSkin.primary.getGreen(),
                    editSkin.primary.getBlue(), 28)
                    : new Color(12, 12, 40));
            g2.fillRoundRect(bx, by, 180, 88, 10, 10);

            g2.setColor(sel ? editSkin.primary : new Color(62, 62, 105));
            g2.setStroke(sel ? SK2 : SK1);
            g2.drawRoundRect(bx, by, 180, 88, 10, 10);

            g2.setFont(FNT_MD);
            g2.setColor(sel ? editSkin.primary : new Color(150, 150, 190));
            g2.drawString(shapes[i].toUpperCase(), bx + 12, by + 50);

            // Mini shape preview
            AffineTransform savedT = g2.getTransform();
            g2.translate(bx + 152, by + 44);
            Skin tmp = new Skin();
            tmp.primary   = editSkin.primary;
            tmp.secondary = editSkin.secondary;
            tmp.shape     = shapes[i];
            renderPlayerShape(g2, tmp, 24);
            g2.setTransform(savedT);
        }
    }

    void renderSkinPreview(Graphics2D g2) {
        int px = W - 205, py = 112, pw = 188, ph = H - 188;

        g2.setColor(new Color(7, 7, 28));
        g2.fillRoundRect(px, py, pw, ph, 12, 12);
        g2.setColor(new Color(38, 38, 85));
        g2.setStroke(SK1);
        g2.drawRoundRect(px, py, pw, ph, 12, 12);

        g2.setFont(FNT_SM);
        g2.setColor(C_DIM);
        g2.drawString("PREVIEW", px + 60, py + 20);

        // Mini ground line
        g2.setColor(new Color(18, 18, 55));
        g2.fillRect(px, py + ph - 28, pw, 28);
        g2.setColor(editSkin.primary);
        g2.fillRect(px, py + ph - 28, pw, 2);

        // Spinning preview player
        AffineTransform savedT = g2.getTransform();
        g2.translate(px + pw / 2f, py + ph / 2f - 8f);
        g2.rotate(tick * 0.045);
        g2.setColor(editSkin.glowColor);
        g2.fillOval(-PS/2 - 10, -PS/2 - 10, PS + 20, PS + 20);
        renderPlayerShape(g2, editSkin, PS);
        g2.setTransform(savedT);

        // Colour swatches
        g2.setFont(FNT_TINY);
        g2.setColor(C_FAINT);
        g2.drawString("Primary",   px + 8, py + ph - 42);
        g2.setColor(editSkin.primary);
        g2.fillRect(px + 70, py + ph - 50, 42, 14);

        g2.setColor(C_FAINT);
        g2.drawString("Secondary", px + 8, py + ph - 26);
        g2.setColor(editSkin.secondary);
        g2.fillRect(px + 70, py + ph - 33, 42, 14);
    }

    // =========================================================================
    //  ENTRY POINT
    // =========================================================================

    // =========================================================================
    //  LEVEL MAKER — editor, key/mouse handlers, compiler, playback
    // =========================================================================

    /** Keyboard handler while in the level editor. */
    void makerKeyPress(int k) {
        switch (k) {
            case KeyEvent.VK_ESCAPE:
                if (makerPlayback) {
                    // Stop test and return to editor
                    makerPlayback = false;
                    phase = Phase.LEVEL_MAKER;
                } else {
                    phase = Phase.LEVEL_SELECT;
                }
                break;
            case KeyEvent.VK_LEFT:
                makerScrollCol = Math.max(0, makerScrollCol - 8);
                break;
            case KeyEvent.VK_RIGHT:
                makerScrollCol = Math.min(MAKER_COLS - visibleMakerCols(), makerScrollCol + 8);
                break;
            case KeyEvent.VK_DELETE:
                initMakerGrid();
                break;
            case KeyEvent.VK_T:
                // Test the current level
                testMakerLevel();
                break;
            case KeyEvent.VK_ENTER:
                testMakerLevel();
                break;
            default:
                // Number keys 1-9 select tool index
                if (k >= KeyEvent.VK_1 && k <= KeyEvent.VK_9) {
                    int ti = k - KeyEvent.VK_1;
                    if (ti < MAKER_TOOLS.length) makerTool = MAKER_TOOLS[ti];
                }
                break;
        }
    }

    /** How many columns are visible at once in the grid area. */
    int visibleMakerCols() {
        return (W - MAKER_OFF_X * 2) / MAKER_CELL;
    }

    /** Called when mouse is pressed or dragged on the level maker screen. */
    void makerMousePress(int mx, int my, boolean erase) {
        makerErasing = erase;

        // ── Toolbar row ────────────────────────────────────────────────────────
        if (my >= TOOL_Y - 2 && my <= TOOL_Y + TOOL_BTN_H + 2) {
            for (int i = 0; i < MAKER_TOOLS.length; i++) {
                int bx = TOOL_START_X + i * (TOOL_BTN_W + 4);
                if (mx >= bx && mx <= bx + TOOL_BTN_W) {
                    if (!erase) makerTool = MAKER_TOOLS[i];
                    return;
                }
            }
        }

        // ── Scroll arrows ─────────────────────────────────────────────────────
        if (my >= H - 46 && my <= H - 20) {
            if (mx >= 16 && mx <= 58) {
                makerScrollCol = Math.max(0, makerScrollCol - 8);
                return;
            }
            if (mx >= W - 58 && mx <= W - 16) {
                makerScrollCol = Math.min(MAKER_COLS - visibleMakerCols(), makerScrollCol + 8);
                return;
            }
            // "TEST" button
            if (mx >= W/2 - 55 && mx <= W/2 + 55 && my >= H - 48 && my <= H - 14) {
                testMakerLevel(); return;
            }
            // "CLEAR" button
            if (mx >= W/2 + 68 && mx <= W/2 + 168 && my >= H - 48 && my <= H - 14) {
                initMakerGrid(); return;
            }
            // "BACK" button
            if (mx >= W/2 - 168 && mx <= W/2 - 68 && my >= H - 48 && my <= H - 14) {
                phase = Phase.LEVEL_SELECT; return;
            }
        }

        // ── Grid cells ────────────────────────────────────────────────────────
        int gridX1 = MAKER_OFF_X;
        int gridY1 = MAKER_OFF_Y;
        int gridX2 = gridX1 + visibleMakerCols() * MAKER_CELL;
        int gridY2 = gridY1 + MAKER_ROWS * MAKER_CELL;

        if (mx >= gridX1 && mx < gridX2 && my >= gridY1 && my < gridY2) {
            int col = makerScrollCol + (mx - gridX1) / MAKER_CELL;
            int row = (my - gridY1) / MAKER_CELL;
            if (col >= 0 && col < MAKER_COLS && row >= 0 && row < MAKER_ROWS) {
                makerGrid[row][col] = erase ? '.' : makerTool;
            }
        }
    }

    /** Update hover highlight on mouse move. */
    void makerUpdateHover(int mx, int my) {
        int gridX1 = MAKER_OFF_X;
        int gridY1 = MAKER_OFF_Y;
        int gridX2 = gridX1 + visibleMakerCols() * MAKER_CELL;
        int gridY2 = gridY1 + MAKER_ROWS * MAKER_CELL;
        if (mx >= gridX1 && mx < gridX2 && my >= gridY1 && my < gridY2) {
            makerCol = makerScrollCol + (mx - gridX1) / MAKER_CELL;
            makerRow = (my - gridY1) / MAKER_CELL;
        } else {
            makerRow = makerCol = -1;
        }
    }

    /**
     * Compile the makerGrid into a LevelDef, inject an end flag at the
     * last column, and launch playback.
     */
    void testMakerLevel() {
        // Ensure there's an end flag — place one at col MAKER_COLS-8 if none
        boolean hasEnd = false;
        for (int r = 0; r < MAKER_ROWS && !hasEnd; r++)
            for (int c = 0; c < MAKER_COLS && !hasEnd; c++)
                if (makerGrid[r][c] == 'E') hasEnd = true;
        if (!hasEnd) makerGrid[0][MAKER_COLS - 8] = 'E';

        makerLevel = compileMakerLevel();
        makerPlayback = true;

        // Reset attempt counter so we don't inflate totalAttempts
        loadCustomLevel(makerLevel);
    }

    /** Turn makerGrid char arrays into a LevelDef with default appearance. */
    LevelDef compileMakerLevel() {
        String[] rows = new String[MAKER_ROWS];
        for (int r = 0; r < MAKER_ROWS; r++)
            rows[r] = new String(makerGrid[r]);
        return new LevelDef(
                makerLevelName,
                new Color(5, 18, 55), new Color(10, 40, 100),
                new Color(0, 180, 220), new Color(0, 230, 255),
                9.0f, "CUSTOM",
                rows
        );
    }

    /**
     * Load a custom LevelDef for playback without incrementing the
     * built-in level attempt counters.
     */
    void loadCustomLevel(LevelDef def) {
        // Re-use loadLevel logic but don't touch bestPct/totalAttempts
        objs.clear();
        portals.clear();
        particles.clear();

        scrollX     = 0f;
        scrollSpeed = def.baseSpeed;
        gravity     = G_BASE;
        gravFlipped = false;
        playerY     = GROUND_Y - PS;
        playerVY    = 0f;
        onGround    = true;
        holdingUp   = false;
        rotation    = 0.0;
        bgOffset    = 0f;
        gameMode    = GameMode.CUBE;
        jumpUsed    = false;
        score       = 0;
        combo       = 0;
        orbsCollected = 0;
        shakeAmt    = 0f;
        flashAlpha  = 0f;
        deadTimer   = 0;
        winTimer    = 0;
        endFlagWX   = 0f;
        cpScrollX   = -1f;
        cpPlayerY   = 0f;
        cpSpeed     = def.baseSpeed;
        cpMode      = GameMode.CUBE;

        int numRows = def.rows.length;
        int numCols = 0;
        for (String r : def.rows) numCols = Math.max(numCols, r.length());
        int laneH = (GROUND_Y - CEIL_Y - 20) / numRows;

        for (int row = 0; row < numRows; row++) {
            String rowStr = def.rows[row];
            for (int col = 0; col < rowStr.length(); col++) {
                char ch = rowStr.charAt(col);
                if (ch == '.') continue;
                float wx      = W + col * TILE;
                boolean onGnd = (row >= numRows - 2);
                float   tileY = CEIL_Y + 20 + row * laneH;
                switch (ch) {
                    case 'S': { float sy = onGnd?(GROUND_Y-TILE+4):tileY; objs.add(new Obj(wx,sy,TILE-2,TILE-2,ObjKind.SPIKE)); break; }
                    case 'B': { float by = onGnd?(GROUND_Y-TILE):tileY;   objs.add(new Obj(wx,by,TILE,TILE,ObjKind.BLOCK)); break; }
                    case 'T': { float ty = onGnd?(GROUND_Y-TILE*2):tileY; objs.add(new Obj(wx,ty,TILE,TILE*2,ObjKind.TALL_BLOCK)); break; }
                    case 'O': { float oy = onGnd?(GROUND_Y-TILE-24):tileY+6; objs.add(new Obj(wx+4,oy,30,30,ObjKind.ORB)); break; }
                    case 'D': { float dy = onGnd?(GROUND_Y-TILE-6):tileY; objs.add(new Obj(wx,dy,TILE,8,ObjKind.PAD)); break; }
                    case 'C': portals.add(new Portal(wx,PortalKind.MODE_CUBE));  break;
                    case 'H': portals.add(new Portal(wx,PortalKind.MODE_SHIP));  break;
                    case 'L': portals.add(new Portal(wx,PortalKind.MODE_BALL));  break;
                    case 'U': portals.add(new Portal(wx,PortalKind.MODE_UFO));   break;
                    case 'W': portals.add(new Portal(wx,PortalKind.MODE_WAVE));  break;
                    case 'R': portals.add(new Portal(wx,PortalKind.MODE_ROBOT)); break;
                    case '1': portals.add(new Portal(wx,PortalKind.SPEED_SLOW));   break;
                    case '2': portals.add(new Portal(wx,PortalKind.SPEED_NORMAL)); break;
                    case '3': portals.add(new Portal(wx,PortalKind.SPEED_FAST));   break;
                    case '4': portals.add(new Portal(wx,PortalKind.SPEED_VFAST));  break;
                    case 'G': portals.add(new Portal(wx,PortalKind.GRAVITY_FLIP)); break;
                    case 'N': portals.add(new Portal(wx,PortalKind.GRAVITY_NORM)); break;
                    case 'E': endFlagWX = wx; break;
                    default: break;
                }
            }
        }
        if (endFlagWX == 0f) endFlagWX = W + numCols * TILE - 100;

        // Override currentLevel to 0 so HUD uses a valid index
        // (doesn't affect bestPct/totalAttempts because we use makerLevel reference)
        phase = Phase.PLAYING;
    }

    // ── Override handleDeath / handleWin in test mode ──────────────────────
    // We intercept via a flag set in the game state; the existing handleDeath
    // already re-uses cpScrollX for practice. For the maker we just restart.
    // This is handled by checking makerPlayback in the key handlers above.

    // =========================================================================
    //  RENDER LEVEL MAKER
    // =========================================================================

    /** Row labels shown at the left edge of the grid. */
    static final String[] ROW_LABELS = { "Ceil", "Mid", "Mode", "Speed", "Orbs", "Floor" };

    /** Colour coding per row. */
    static final Color[] ROW_COLORS = {
            new Color(200,100,255), new Color(100,200,255),
            new Color(100,255,100), new Color(255,200,50),
            new Color(255,130,50),  new Color(255,80,80)
    };

    void renderLevelMaker(Graphics2D g2) {
        // Background
        g2.setPaint(new GradientPaint(0,0,new Color(4,4,22),0,H,new Color(8,8,40)));
        g2.fillRect(0, 0, W, H);

        // ── Header ──────────────────────────────────────────────────────────
        g2.setFont(new Font("Courier New", Font.BOLD, 22));
        g2.setColor(new Color(100,210,255));
        g2.drawString("LEVEL MAKER", MAKER_OFF_X, 32);

        g2.setFont(FNT_TINY);
        g2.setColor(new Color(150,150,200));
        g2.drawString("LMB=draw  RMB=erase  T/ENTER=test  DEL=clear  ←→=scroll  ESC=back",
                200, 32);

        // ── Toolbar ─────────────────────────────────────────────────────────
        renderMakerToolbar(g2);

        // ── Grid ────────────────────────────────────────────────────────────
        renderMakerGrid(g2);

        // ── Row labels (left side) ───────────────────────────────────────────
        g2.setFont(FNT_TINY);
        for (int r = 0; r < MAKER_ROWS; r++) {
            g2.setColor(ROW_COLORS[r]);
            g2.drawString(ROW_LABELS[r],
                    2, MAKER_OFF_Y + r * MAKER_CELL + MAKER_CELL - 4);
        }

        // ── Mini preview bar at bottom ───────────────────────────────────────
        renderMakerMinimap(g2);

        // ── Bottom button bar ────────────────────────────────────────────────
        renderMakerButtons(g2);
    }

    void renderMakerToolbar(Graphics2D g2) {
        for (int i = 0; i < MAKER_TOOLS.length; i++) {
            int    bx  = TOOL_START_X + i * (TOOL_BTN_W + 4);
            boolean sel = (makerTool == MAKER_TOOLS[i]);
            Color  tc  = toolColor(MAKER_TOOLS[i]);

            // Button bg
            g2.setColor(sel ? new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 45)
                    : new Color(10, 10, 30));
            g2.fillRoundRect(bx, TOOL_Y, TOOL_BTN_W, TOOL_BTN_H, 6, 6);

            // Border
            g2.setColor(sel ? tc : new Color(50, 50, 90));
            g2.setStroke(sel ? SK2 : SK1);
            g2.drawRoundRect(bx, TOOL_Y, TOOL_BTN_W, TOOL_BTN_H, 6, 6);

            // Char label
            g2.setFont(FNT_TINY);
            g2.setColor(tc);
            g2.drawString(String.valueOf(MAKER_TOOLS[i]),
                    bx + 5, TOOL_Y + 13);

            // Name label beneath char
            g2.setFont(FNT_MICRO);
            g2.setColor(new Color(180,180,220));
            g2.drawString(MAKER_TOOL_LB[i], bx + 3, TOOL_Y + 25);
        }

        // Tool number hints
        g2.setFont(FNT_MICRO);
        g2.setColor(new Color(80,80,110));
        for (int i = 0; i < Math.min(9, MAKER_TOOLS.length); i++) {
            int bx = TOOL_START_X + i * (TOOL_BTN_W + 4);
            g2.drawString(String.valueOf(i+1), bx + TOOL_BTN_W - 10, TOOL_Y + 13);
        }
    }

    /** Returns a display colour for each tool character. */
    Color toolColor(char tool) {
        switch (tool) {
            case 'S': return C_SPIKE;
            case 'B': return new Color(100,160,255);
            case 'T': return new Color(60,100,200);
            case 'O': return C_ORB;
            case 'D': return C_PAD;
            case 'H': return PC_SHIP;
            case 'C': return PC_CUBE;
            case 'W': return PC_WAVE;
            case 'L': return PC_BALL;
            case 'G': return PC_GFLIP;
            case 'N': return PC_GNORM;
            case '1': return PC_SLOW;
            case '3': return PC_FAST;
            case '4': return PC_VFAST;
            case 'E': return C_GOLD;
            default:  return Color.LIGHT_GRAY;
        }
    }

    void renderMakerGrid(Graphics2D g2) {
        int visibleCols = visibleMakerCols();
        int gx = MAKER_OFF_X;
        int gy = MAKER_OFF_Y;
        int gw = visibleCols * MAKER_CELL;
        int gh = MAKER_ROWS  * MAKER_CELL;

        // Background
        g2.setColor(new Color(6, 6, 24));
        g2.fillRect(gx, gy, gw, gh);

        // Cells
        for (int r = 0; r < MAKER_ROWS; r++) {
            for (int vc = 0; vc < visibleCols; vc++) {
                int    col = makerScrollCol + vc;
                if (col >= MAKER_COLS) break;
                char   ch  = makerGrid[r][col];
                int    cx  = gx + vc * MAKER_CELL;
                int    cy  = gy + r  * MAKER_CELL;

                // Row-alternating background
                g2.setColor(r % 2 == 0
                        ? new Color(10,10,32)
                        : new Color(8, 8, 26));
                g2.fillRect(cx, cy, MAKER_CELL, MAKER_CELL);

                // Hover highlight
                if (r == makerRow && col == makerCol) {
                    g2.setColor(new Color(255,255,255,22));
                    g2.fillRect(cx, cy, MAKER_CELL, MAKER_CELL);
                }

                // Cell content
                if (ch != '.') {
                    Color tc = toolColor(ch);
                    g2.setColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 55));
                    g2.fillRect(cx+1, cy+1, MAKER_CELL-2, MAKER_CELL-2);
                    g2.setColor(tc);
                    g2.setFont(FNT_MICRO);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(String.valueOf(ch),
                            cx + (MAKER_CELL - fm.stringWidth(String.valueOf(ch))) / 2,
                            cy + MAKER_CELL - 4);
                    // Tiny outline
                    g2.setColor(new Color(tc.getRed(), tc.getGreen(), tc.getBlue(), 120));
                    g2.drawRect(cx+1, cy+1, MAKER_CELL-3, MAKER_CELL-3);
                }
            }
        }

        // Column grid lines
        g2.setColor(new Color(255,255,255,10));
        g2.setStroke(SK1);
        for (int vc = 0; vc <= visibleCols; vc++) {
            int cx = gx + vc * MAKER_CELL;
            g2.drawLine(cx, gy, cx, gy + gh);
        }
        // Row grid lines
        for (int r = 0; r <= MAKER_ROWS; r++) {
            int cy = gy + r * MAKER_CELL;
            g2.drawLine(gx, cy, gx + gw, cy);
        }

        // Row separators with colour
        for (int r = 0; r < MAKER_ROWS; r++) {
            int cy = gy + r * MAKER_CELL;
            g2.setColor(new Color(ROW_COLORS[r].getRed(), ROW_COLORS[r].getGreen(),
                    ROW_COLORS[r].getBlue(), 28));
            g2.fillRect(gx, cy, gw, 2);
        }

        // Outer border
        g2.setColor(new Color(60,90,160));
        g2.setStroke(SK2);
        g2.drawRect(gx, gy, gw, gh);

        // Column numbers above grid
        g2.setFont(FNT_MICRO);
        g2.setColor(new Color(100,100,150));
        for (int vc = 0; vc < visibleCols; vc += 5) {
            int col = makerScrollCol + vc;
            if (col >= MAKER_COLS) break;
            int cx = gx + vc * MAKER_CELL;
            g2.drawString(String.valueOf(col), cx + 1, gy - 2);
        }

        // Scroll position indicator
        g2.setFont(FNT_TINY);
        g2.setColor(new Color(120,150,220));
        g2.drawString("Col " + makerScrollCol + "-" + (makerScrollCol+visibleCols-1)
                + " / " + MAKER_COLS, gx + gw + 8, gy + 14);
    }

    /** Minimap: a tiny full-level overview below the grid. */
    void renderMakerMinimap(Graphics2D g2) {
        int mx  = MAKER_OFF_X;
        int my  = MAKER_OFF_Y + MAKER_ROWS * MAKER_CELL + 8;
        int mw  = W - MAKER_OFF_X * 2 - 10;
        int mh  = 28;
        float cellW = (float) mw / MAKER_COLS;

        // Background
        g2.setColor(new Color(5,5,20));
        g2.fillRect(mx, my, mw, mh);
        g2.setColor(new Color(40,40,80));
        g2.setStroke(SK1);
        g2.drawRect(mx, my, mw, mh);

        // Dots for each placed cell
        for (int r = 0; r < MAKER_ROWS; r++) {
            float rowY = my + (float) r / MAKER_ROWS * mh;
            for (int c = 0; c < MAKER_COLS; c++) {
                if (makerGrid[r][c] != '.') {
                    Color tc = toolColor(makerGrid[r][c]);
                    g2.setColor(tc);
                    int dotX = mx + (int)(c * cellW);
                    int dotY = (int) rowY;
                    int dotW = Math.max(2, (int) cellW);
                    int dotH = Math.max(2, mh / MAKER_ROWS - 1);
                    g2.fillRect(dotX, dotY, dotW, dotH);
                }
            }
        }

        // Viewport rectangle showing currently visible area
        int vpX = mx + (int)(makerScrollCol * cellW);
        int vpW = Math.min(mw, (int)(visibleMakerCols() * cellW));
        g2.setColor(new Color(255,255,255,55));
        g2.drawRect(vpX, my, vpW, mh);

        g2.setFont(FNT_TINY);
        g2.setColor(new Color(100,130,200));
        g2.drawString("OVERVIEW  [←→ scroll  |  DEL clear]",
                mx + mw + 14, my + 18);
    }

    void renderMakerButtons(Graphics2D g2) {
        int by = H - 48;

        // Semi-transparent bottom strip
        g2.setColor(new Color(0,0,0,140));
        g2.fillRect(0, by - 6, W, H - by + 6);

        renderButton(g2, W/2 - 168, by, 90, 34, "← BACK",  new Color(160,160,180), false);
        renderButton(g2, W/2 -  55, by, 110, 34, "▶ TEST", new Color(80,220,120),  true);
        renderButton(g2, W/2 +  68, by, 90, 34, "DEL CLEAR",    new Color(220,80,80),   false);

        // Scroll arrows
        renderButton(g2, 16, by, 42, 34, "◄", new Color(120,120,180), false);
        renderButton(g2, W - 58, by, 42, 34, "►", new Color(120,120,180), false);

        // Hotkey legend
        g2.setFont(FNT_MICRO);
        g2.setColor(new Color(80,80,120));
        g2.drawString("T=test  DEL=clear  1-9=tool  ←→=scroll  ESC=back", W/2-150, H - 3);
    }

    // =========================================================================
    //  END OF LEVEL MAKER
    // =========================================================================

    public static void main(String[] args) {
        // Enable OpenGL hardware acceleration on supported platforms
        System.setProperty("sun.java2d.opengl", "true");

        JFrame frame = new JFrame("Geometry Run \u2013 Ultimate Edition");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        Main game = new Main();
        frame.add(game);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        game.requestFocusInWindow();
        game.startGameThread();
    }
}