package com.arena.duelist;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.Random;

/** Whole game: screens, match flow, combat resolution, HUD, touch controls. */
public final class Game {

    public interface Host {
        void announce(String text);

        void setBright(boolean on);

        void vibrate(int ms);

        void sfx(int id);
    }

    // screens
    private static final int S_TITLE = 0;
    private static final int S_MAPS = 1;
    private static final int S_ARSENAL = 2;
    private static final int S_SETTINGS = 3;
    private static final int S_STYLE = 4;
    private static final int S_PLAY = 5;
    private static final int S_RESULT = 6;
    private static final int S_HOW = 7;

    // match phases
    private static final int PH_INTRO = 0;
    private static final int PH_FIGHT = 1;
    private static final int PH_ROUND_END = 2;
    private static final int PH_MATCH_END = 3;
    private static final int PH_SURVIVAL_BREAK = 4;
    private static final int PH_BOSS_BREAK = 5;

    // modes
    private static final int M_QUICK = 0;
    private static final int M_LOCAL = 1;
    private static final int M_SURVIVAL = 2;
    private static final int M_BOSS = 3;
    private static final int M_FOOTBALL_CPU = 4;
    private static final int M_FOOTBALL_LOCAL = 5;
    private static final int PLAYER_COLOR = 0xFF48E879;
    private static final int CPU_COLOR = 0xFFFFE52E;

    private static final String[] BOSS_NAMES = {
            "IRONCLAD", "SKY LANCER", "NIGHTFALL", "VOID WITCH", "THE OVERLORD"
    };
    private static final int[] BOSS_WEAPONS = {
            Weapons.HAMMER, Weapons.SPEAR, Weapons.DUAL_SABERS, Weapons.WAND, Weapons.ENERGY_BLADE
    };
    private static final int[] BOSS_COLORS = {
            0xFFB8C0CC, 0xFF51E5FF, 0xFFB28BFF, 0xFFD9A0FF, 0xFFFF6B8A
    };
    private static final float[] BOSS_HEALTH = {130f, 150f, 170f, 195f, 225f};

    // actions
    private static final int A_NONE = 0;
    private static final int A_QUICK = 1;
    private static final int A_LOCAL = 2;
    private static final int A_SURVIVAL = 3;
    private static final int A_ARSENAL = 4;
    private static final int A_SETTINGS = 5;
    private static final int A_STYLE = 6;
    private static final int A_BACK = 7;
    private static final int A_RESUME = 8;
    private static final int A_RESTART = 9;
    private static final int A_QUIT = 10;
    private static final int A_REMATCH = 11;
    private static final int A_MENU = 12;
    private static final int A_HOW = 13;
    private static final int A_MAPS_FROM_PAUSE = 14;
    private static final int A_BOSS = 15;
    private static final int A_FOOTBALL_CPU = 16;
    private static final int A_FOOTBALL_LOCAL = 17;
    private static final int A_SOUND = 20;
    private static final int A_VOICE = 21;
    private static final int A_GRAVITY = 22;
    private static final int A_INSTANT = 23;
    private static final int A_SHIELD = 24;
    private static final int A_DIFF = 25;
    private static final int A_RESET = 26;
    private static final int A_MAP_BASE = 100;
    private static final int A_WEAPON_BASE = 200;
    private static final int A_COLOR_BASE = 300;
    private static final int A_HAIR_BASE = 400;

    private final Context ctx;
    private final Host host;
    public final Profile profile;

    private final Particles fx = new Particles();
    private final Random rnd = new Random();
    private final ArrayList<Projectile> shots = new ArrayList<Projectile>();
    private final ArrayList<Pickup> pickups = new ArrayList<Pickup>();
    private final ArrayList<Btn> uiBtns = new ArrayList<Btn>();
    private final Cpu cpu = new Cpu();
    private final Football football = new Football();

    private Maps.Arena arena;
    private int arenaIndex;
    private final Fighter[] fighters = new Fighter[2];

    private int screen = S_TITLE;
    private int pendingMode = M_QUICK;
    private int mode = M_QUICK;
    private int phase = PH_INTRO;
    private float phaseT;
    private int roundNumber = 1;
    private int roundWinsA;
    private int roundWinsB;
    private boolean roundDraw;
    private int wave = 1;
    private int bossIndex;
    private int bossesDefeated;
    private boolean paused;
    private boolean countAnnounced;
    private int lastCount = -1;
    private String resultTitle = "";
    private String resultSub = "";
    private int resultCoins;
    private boolean playerWon;
    private float resultDelay;
    private int confirmBuy = -1;
    private boolean confirmReset;

    private float w = 960, h = 540;
    private float scale = 1f;
    private float viewW = 960f;
    private float viewH = 540f;
    private float camX, camY;
    private static final float ZOOM = 1.45f;
    /** Camera zoom, eased between ZOOM and ZOOM_MIN as the fighters separate. */
    private float zoom = ZOOM;
    private static final float ZOOM_MIN = 0.40f;
    private boolean cameraReady;
    private float zoomHold;
    private float shake;
    private float hitStop;
    private float time;
    private float spawnTimer;

    // controls
    private final Stick stickA = new Stick();
    private final Stick stickB = new Stick();
    private final RectF pauseRect = new RectF();
    private final Fighter previewFighter = new Fighter(0, "PREVIEW", 0, 0, false);

    public Game(Context ctx, Host host) {
        this.ctx = ctx;
        this.host = host;
        profile = new Profile(ctx);
        arena = Maps.ALL[0];
        arenaIndex = 0;
        fighters[0] = new Fighter(0, "YOU", 480, 520, false);
        fighters[1] = new Fighter(1, "CPU", 920, 520, true);
        fighters[0].color = profile.skinColor;
        fighters[0].hair = profile.hairStyle;
        fighters[1].hair = 0;
        resetMatch(M_QUICK, 0, false);
    }

    public synchronized void resize(int pw, int ph) {
        if (pw <= 0 || ph <= 0) {
            return;
        }
        w = pw;
        h = ph;
        scale = h / 540f;
        viewH = 540f;
        viewW = w / scale;
        layoutControls();
        cameraReady = false;
        updateCamera(0f);
    }

    private void layoutControls() {
        float stickY = viewH - 92f;
        float leftX = 92f;
        float rightX = viewW - 92f;
        stickA.setBase(leftX, stickY, 74f);
        stickB.setBase(rightX, stickY, 74f);



        pauseRect.set(viewW - 60f, 88f, viewW - 20f, 128f);
    }

    // ------------------------------------------------------------------ input

    public synchronized void pointerDown(int id, float sx, float sy) {
        float x = sx / scale;
        float y = sy / scale;

        if (screen == S_PLAY) {
            if (paused) {
                handleMenuTap(x, y);
                return;
            }
            if (pauseRect.contains(x, y)) {
                paused = true;
                // drop last frame's menu hitboxes so an early tap cannot hit a
                // stale button from the screen we came from
                uiBtns.clear();
                releaseControls();
                return;
            }
            // Countdown, K.O. and result transitions must not accept attacks or
            // jumps. Physics continues, but combat input only belongs to FIGHT.
            if (phase != PH_FIGHT) {
                return;
            }
            if (isLocal()) {
                // One stick per player: hold to move/aim/attack, release for
                // the equipped weapon's skill. No separate button clusters.
                if (stickA.grab(id, x, y)) {
                    return;
                }
                if (stickB.grab(id, x, y)) {
                    return;
                }
            } else if (x < viewW * 0.50f) {
                // The single-player stick floats to the thumb on the left half.
                if (stickA.grabAnywhere(id, x, y)) {
                    return;
                }
            }
            return;
        }

        if (screen == S_RESULT) {
            handleMenuTap(x, y);
            return;
        }
        handleMenuTap(x, y);
    }

    public synchronized void pointerMove(int id, float sx, float sy) {
        float x = sx / scale;
        float y = sy / scale;
        stickA.move(id, x, y);
        stickB.move(id, x, y);
    }

    public synchronized void pointerUp(int id, float sx, float sy) {
        float x = sx / scale;
        float y = sy / scale;
        if (stickA.pointer == id) {
            // ACTION_UP can carry a newer position than the last MOVE event.
            // Sample it before interpreting the release direction.
            stickA.move(id, x, y);
            if (screen == S_PLAY && !paused && phase == PH_FIGHT) {
                useAbility(fighters[0], stickA.dx, stickA.dy);
            }
        }
        if (isLocal() && stickB.pointer == id) {
            stickB.move(id, x, y);
            if (screen == S_PLAY && !paused && phase == PH_FIGHT) {
                useAbility(fighters[1], stickB.dx, stickB.dy);
            }
        }
        stickA.release(id);
        stickB.release(id);
    }

    /** A cancelled gesture releases all sticks but is not a deliberate skill flick. */
    public synchronized void pointerCancel() {
        stickA.release();
        stickB.release();
    }

    private void handleMenuTap(float x, float y) {
        for (int i = uiBtns.size() - 1; i >= 0; i--) {
            Btn b = uiBtns.get(i);
            if (b.r.contains(x, y)) {
                act(b.action);
                return;
            }
        }
    }

    private void act(int action) {
        sfx(SoundFx.CLICK);
        // No screen transition may leave active hitboxes from the old screen.
        uiBtns.clear();
        // "tap again to confirm" states: any other tap cancels them, which keeps
        // a stale confirmation from spending coins or wiping progress later
        boolean keepReset = confirmReset && action == A_RESET;
        boolean keepBuy = confirmBuy >= 0 && action == A_WEAPON_BASE + confirmBuy;
        if (!keepReset) {
            confirmReset = false;
        }
        if (!keepBuy) {
            confirmBuy = -1;
        }
        if (action >= A_MAP_BASE && action < A_MAP_BASE + 100) {
            int idx = action - A_MAP_BASE;
            if (idx >= 0 && idx < Maps.ALL.length) {
                resetMatch(pendingMode, idx, true);
            }
            return;
        }
        if (action >= A_WEAPON_BASE && action < A_WEAPON_BASE + 100) {
            int idx = action - A_WEAPON_BASE;
            if (idx < 0 || idx >= Weapons.COUNT) {
                return;
            }
            if (profile.unlocked(idx)) {
                profile.starter = idx;
                profile.save();
                announce(Weapons.name(idx));
            } else if (profile.coins < Weapons.DEFS[idx].cost) {
                announce("Not enough coins");
            } else if (confirmBuy == idx) {
                // second tap: actually spend the coins
                profile.buy(idx);
                profile.starter = idx;
                profile.save(); // buy() saved before starter was assigned
                announce("Unlocked " + Weapons.name(idx));
                host.vibrate(20);
            } else {
                confirmBuy = idx;
                announce("Tap again to buy for " + Weapons.DEFS[idx].cost);
            }
            return;
        }
        if (action >= A_COLOR_BASE && action < A_COLOR_BASE + 100) {
            profile.skinColor = SKINS[action - A_COLOR_BASE];
            fighters[0].color = profile.skinColor;
            profile.save();
            return;
        }
        if (action >= A_HAIR_BASE && action < A_HAIR_BASE + 10) {
            profile.hairStyle = action - A_HAIR_BASE;
            fighters[0].hair = profile.hairStyle;
            profile.save();
            return;
        }

        switch (action) {
            case A_QUICK:
                pendingMode = M_QUICK;
                screen = S_MAPS;
                break;
            case A_LOCAL:
                pendingMode = M_LOCAL;
                screen = S_MAPS;
                break;
            case A_SURVIVAL:
                pendingMode = M_SURVIVAL;
                screen = S_MAPS;
                break;
            case A_BOSS:
                pendingMode = M_BOSS;
                screen = S_MAPS;
                break;
            case A_FOOTBALL_CPU:
                resetMatch(M_FOOTBALL_CPU, 0, true);
                break;
            case A_FOOTBALL_LOCAL:
                resetMatch(M_FOOTBALL_LOCAL, 0, true);
                break;
            case A_ARSENAL:
                screen = S_ARSENAL;
                break;
            case A_SETTINGS:
                screen = S_SETTINGS;
                break;
            case A_STYLE:
                screen = S_STYLE;
                break;
            case A_HOW:
                screen = S_HOW;
                break;
            case A_BACK:
                screen = S_TITLE;
                break;
            case A_RESUME:
                paused = false;
                break;
            case A_RESTART:
                paused = false;
                resetMatch(mode, arenaIndex, true);
                break;
            case A_QUIT:
                paused = false;
                screen = S_TITLE;
                profile.save();
                break;
            case A_REMATCH:
                resetMatch(mode, arenaIndex, true);
                break;
            case A_MENU:
                screen = S_TITLE;
                break;
            case A_MAPS_FROM_PAUSE:
                paused = false;
                // keep the mode we are actually playing; otherwise picking an
                // arena after a pause silently restarted as Quick Fight
                pendingMode = mode;
                if (isFootball()) {
                    resetMatch(mode, 0, true);
                } else {
                    screen = S_MAPS;
                }
                break;
            case A_SOUND:
                profile.sound = !profile.sound;
                profile.save();
                break;
            case A_VOICE:
                profile.voice = !profile.voice;
                profile.save();
                break;
            case A_GRAVITY:
                profile.gravity = !profile.gravity;
                profile.save();
                break;
            case A_INSTANT:
                profile.instantKO = !profile.instantKO;
                profile.save();
                break;
            case A_SHIELD:
                profile.energyShield = !profile.energyShield;
                profile.save();
                break;
            case A_DIFF:
                profile.difficulty = (profile.difficulty + 1) % 4;
                profile.save();
                announce(DIFF_NAMES[profile.difficulty]);
                break;
            case A_RESET:
                if (!confirmReset) {
                    confirmReset = true;
                    announce("Tap reset again to wipe progress");
                    break;
                }
                confirmReset = false;
                profile.coins = 250;
                profile.wins = 0;
                profile.losses = 0;
                profile.streak = 0;
                profile.bestStreak = 0;
                profile.starter = 0;
                for (int i = 0; i < Weapons.COUNT; i++) {
                    profile.weaponUnlocked[i] = Weapons.DEFS[i].cost == 0;
                }
                profile.save();
                break;
            default:
                break;
        }
    }

    /** System interruptions pause explicitly; resuming requires a deliberate tap. */
    public synchronized void suspend() {
        releaseControls();
        if (screen == S_PLAY) {
            paused = true;
            uiBtns.clear();
        }
    }

    public synchronized boolean backPressed() {
        if (screen == S_PLAY) {
            if (paused) {
                screen = S_TITLE;
                paused = false;
            } else {
                paused = true;
            }
            uiBtns.clear();
            releaseControls();
            return true;
        }
        if (screen != S_TITLE) {
            screen = S_TITLE;
            // Back is a screen transition too: do not leave the old menu's
            // buttons tappable until the next draw rebuilds the title hitboxes.
            uiBtns.clear();
            releaseControls();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------- loop

    public synchronized void tick(float dt) {
        if (dt <= 0f) {
            return;
        }
        dt = Math.min(dt, 0.05f);
        time += dt;
        fx.update(dt);
        if (shake > 0) {
            shake = Math.max(0, shake - dt * 26f);
        }

        if (screen == S_TITLE) {
            titleIdle(dt);
            return;
        }
        if (screen == S_PLAY) {
            if (paused) {
                return;
            }
            stepMatch(dt);
        }
    }

    private void titleIdle(float dt) {
        pickups.clear();
        shots.clear();
    }

    private boolean isFootball() {
        return mode == M_FOOTBALL_CPU || mode == M_FOOTBALL_LOCAL;
    }

    private boolean isLocal() {
        return mode == M_LOCAL || mode == M_FOOTBALL_LOCAL;
    }

    private void resetMatch(int newMode, int map, boolean startNow) {
        releaseControls();
        mode = newMode;
        arenaIndex = Math.max(0, Math.min(Maps.ALL.length - 1, map));
        arena = isFootball() ? Maps.FOOTBALL : Maps.ALL[arenaIndex];
        float ax = Maps.safeSpawnX(arena, arena.spawnAx, arena.spawnAy + Fighter.FEET);
        float bx = Maps.safeSpawnX(arena, arena.spawnBx, arena.spawnBy + Fighter.FEET);
        fighters[0].reset(ax, arena.spawnAy);
        fighters[1].reset(bx, arena.spawnBy);
        fighters[0].maxHealth = 100;
        fighters[0].health = 100;
        fighters[1].maxHealth = 100;
        fighters[1].health = 100;
        fighters[0].cpu = false;
        fighters[1].cpu = !isLocal();
        fighters[1].name = isLocal() ? "P2" : "CPU";
        fighters[0].name = "P1";
        fighters[0].color = profile.skinColor;
        fighters[0].hair = profile.hairStyle;
        fighters[1].color = CPU_COLOR;
        fighters[1].hair = 0;
        fighters[1].accent = 0xFFFF4D6D;
        fighters[0].accent = 0xFF4D9BFF;
        fighters[0].giveWeapon(profile.starter);
        if (newMode == M_BOSS) {
            spawnBoss(0);
        } else {
            fighters[1].giveWeapon(rndWeapon());
        }
        if (newMode == M_SURVIVAL) {
            // same formula spawnWave() uses, so wave 1 is not the hardest wave
            float hpm = 70f + 18f;
            fighters[1].maxHealth = hpm;
            fighters[1].health = hpm;
        }
        if (isFootball()) {
            fighters[0].giveWeapon(Weapons.FIST);
            fighters[1].giveWeapon(Weapons.FIST);
            football.reset();
        }
        cpu.reset();
        shots.clear();
        pickups.clear();
        fx.clear();
        roundWinsA = 0;
        roundWinsB = 0;
        roundDraw = false;
        roundNumber = 1;
        wave = 1;
        bossIndex = 0;
        bossesDefeated = 0;
        spawnTimer = 2.5f;
        phase = PH_INTRO;
        // 3.4s so the overlay really counts 3 - 2 - 1; with 2.6s the "3" was
        // never visible and the announcer started at "Two"
        phaseT = 3.4f;
        lastCount = -1;
        countAnnounced = false;
        paused = false;
        resultDelay = 0;
        hitStop = 0f;
        cameraReady = false;
        zoomHold = 0f;
        updateCamera(0f);
        layoutControls();
        if (startNow) {
            screen = S_PLAY;
            announce("Ready");
        }
    }

    private int rndWeapon() {
        if (rnd.nextFloat() < 0.45f) {
            return Weapons.FIST;
        }
        return 1 + rnd.nextInt(Weapons.COUNT - 1);
    }

    private void spawnBoss(int index) {
        int idx = Math.max(0, Math.min(BOSS_NAMES.length - 1, index));
        bossIndex = idx;
        Fighter boss = fighters[1];
        float bx = Maps.safeSpawnX(arena, arena.spawnBx, arena.spawnBy + Fighter.FEET);
        boss.reset(bx, arena.spawnBy);
        boss.maxHealth = BOSS_HEALTH[idx] + profile.difficulty * 12f;
        boss.health = boss.maxHealth;
        boss.color = BOSS_COLORS[idx];
        boss.accent = BOSS_COLORS[idx];
        boss.hair = 0;
        boss.name = BOSS_NAMES[idx];
        boss.cpu = true;
        boss.giveWeapon(BOSS_WEAPONS[idx]);
        cpu.reset();
    }

    private void spawnNextBoss() {
        spawnBoss(bossesDefeated);
        Fighter player = fighters[0];
        player.health = Math.min(player.maxHealth, player.health + 28f);
        player.shield = player.maxShield;
        player.abilityT = Math.min(player.abilityT, 0.7f);
        phase = PH_FIGHT;
        spawnTimer = 3f;
        shake = 8f;
    }

    // ------------------------------------------------------------------ match

    private void stepMatch(float dt) {
        spawnRagdolls();
        if (hitStop > 0) {
            hitStop -= dt;
            return;
        }

        updateRagdolls(dt);
        phaseT -= dt;

        switch (phase) {
            case PH_INTRO:
                stepIntro();
                for (int i = 0; i < 2; i++) {
                    Fighter f = fighters[i];
                    f.update(dt, arena, isFootball() || profile.gravity,
                            !isFootball() && profile.energyShield, false);
                }
                break;
            case PH_FIGHT:
                stepFight(dt);
                break;
            case PH_ROUND_END:
                updateWorldFx(dt);
                if (phaseT <= 0) {
                    nextRound();
                }
                break;
            case PH_SURVIVAL_BREAK:
                updateWorldFx(dt);
                if (phaseT <= 0) {
                    spawnWave();
                }
                break;
            case PH_BOSS_BREAK:
                updateWorldFx(dt);
                if (phaseT <= 0) {
                    spawnNextBoss();
                }
                break;
            case PH_MATCH_END:
                updateWorldFx(dt);
                if (phaseT <= 0 && screen != S_RESULT) {
                    finishMatch();
                }
                break;
            default:
                break;
        }
        updateCamera(dt);
    }

    private void stepIntro() {
        int count = (int) Math.ceil(phaseT - 0.6f);
        if (count > 0 && count != lastCount) {
            lastCount = count;
            if (profile.voice) {
                announce(count == 3 ? "Three" : count == 2 ? "Two" : "One");
            }
        }
        if (phaseT <= 0.6f && !countAnnounced) {
            countAnnounced = true;
            phase = PH_FIGHT;
            shake = 5f;
            if (profile.voice) {
                announce("Fight!");
            }
            host.vibrate(30);
        }
    }

    private void stepFight(float dt) {
        if (isFootball()) {
            stepFootball(dt);
            return;
        }
        spawnTimer -= dt;
        if (spawnTimer <= 0 && pickups.size() < 3 && mode != M_SURVIVAL) {
            spawnTimer = 5.5f + rnd.nextFloat() * 3f;
            spawnPickup();
        }

        readControls(dt);

        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            f.landed = false;
            f.update(dt, arena, profile.gravity, profile.energyShield);
            fireIfNeeded(f);
            if (f.landed) {
                fx.dust(f.x, f.y + Fighter.FEET, 0x88FFFFFF, 5, 20f);
                f.landed = false;
            }
        }

        // fighter-vs-fighter soft push
        Fighter a = fighters[0];
        Fighter b = fighters[1];
        if (!a.dead && !b.dead) {
            float dx = b.x - a.x;
            float adx = Math.abs(dx);
            if (adx < Fighter.HALF_W * 2f && Math.abs(b.y - a.y) < 60f) {
                float push = (Fighter.HALF_W * 2f - adx) * 0.5f;
                float s = dx >= 0 ? 1f : -1f;
                a.x -= s * push;
                b.x += s * push;
                a.vx -= s * 40f;
                b.vx += s * 40f;
            }
        }

        resolveMelee(a, b);
        resolveMelee(b, a);
        updateShots(dt);
        updatePickups(dt);
        checkHazards(a, dt);
        checkHazards(b, dt);
        checkBounds(a);
        checkBounds(b);

        if (mode == M_SURVIVAL) {
            // A mutual KO is still a player death: never award a phantom wave,
            // spawn another enemy, and only then show the defeat screen.
            if (a.dead && phase == PH_FIGHT) {
                endMatch(false);
            } else if (b.dead && phase == PH_FIGHT) {
                onEnemyDown();
            }
        } else if (mode == M_BOSS) {
            // Tournament progression only counts a clean boss takedown. A
            // simultaneous player K.O. remains a loss, as in Survival.
            if (a.dead && phase == PH_FIGHT) {
                endMatch(false);
            } else if (b.dead && phase == PH_FIGHT) {
                onBossDown();
            }
        } else {
            if ((a.dead || b.dead) && phase == PH_FIGHT) {
                onRoundEnd(a.dead);
            }
        }
    }

    /** Football has ball contact, not weapon damage, ammo drops or K.O. victories. */
    private void stepFootball(float dt) {
        applyStick(fighters[0], stickA);
        Fighter b = fighters[1];
        if (isLocal()) {
            applyStick(b, stickB);
        } else {
            // Approach from the right so the CPU shoots at the LEFT goal.
            float targetX = Math.min(Football.WIDTH - 35f, football.x + 65f);
            float dx = targetX - b.x;
            float move = Math.max(-1f, Math.min(1f, dx / 65f));
            boolean near = Math.abs(football.x - b.x) < 160f;
            boolean jump = near && football.y < b.y - 25f && b.onGround;
            b.input(move, -1f, -0.15f, jump, near, false);
            if (near && b.abilityT <= 0f) useAbility(b, -1f, -0.18f);
        }
        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            f.update(dt, arena, true, false);
            // The pitch is enclosed: jumping cannot leave play or kill a player.
            if (f.y - 50f < Football.CEILING) {
                f.y = Football.CEILING + 50f;
                f.vy = Math.max(0f, f.vy);
            }
        }
        int scorer = football.update(dt, fighters);
        if (scorer >= 0) {
            if (scorer == 0) roundWinsA++; else roundWinsB++;
            phase = PH_ROUND_END;
            phaseT = 1.6f;
            releaseControls();
            announce("Goal!");
            sfx(SoundFx.KO);
            host.vibrate(45);
        }
    }

    private void readControls(float dt) {
        Fighter a = fighters[0];
        applyStick(a, stickA);
        if (isLocal()) {
            applyStick(fighters[1], stickB);
        } else if (fighters[1].cpu) {
            Fighter b = fighters[1];
            int lvl = mode == M_SURVIVAL ? Math.min(3, 1 + wave / 3)
                    : (mode == M_BOSS ? Math.min(3, profile.difficulty + 1) : profile.difficulty);
            cpu.think(b, a, arena, dt, lvl);
            boolean attack = cpu.attack();
            boolean special = attack && cpu.skillSafe(b, arena) && b.abilityT <= 0f && rnd.nextFloat() < 0.40f
                    && useAbility(b, cpu.aimX(), cpu.aimY());
            b.input(cpu.move(), cpu.aimX(), cpu.aimY(), cpu.jump(),
                    attack && !special, cpu.shield());
        }
    }

    /** Supreme-Duelist-style one-stick input for one human fighter. */
    private void applyStick(Fighter f, Stick s) {
        float jx = s.dx;
        float jy = s.dy;
        float mag = (float) Math.sqrt(jx * jx + jy * jy);
        float ax = mag > 0.12f ? jx / mag : f.facing;
        float ay = mag > 0.12f ? jy / mag : 0f;
        float mx = Math.abs(jx) < 0.12f ? 0f : jx;

        boolean jump = false;
        if (jy < -0.58f && s.jumpReady) {
            jump = true;
            s.jumpReady = false;
        }
        if (jy > -0.35f) {
            s.jumpReady = true;
        }
        boolean shield = jy > 0.58f;
        // Directional movement is also the basic weapon action. Releasing the
        // same stick fires the separate charged skill in pointerUp().
        boolean attack = mag > 0.32f && !shield;
        f.input(mx, ax, ay, jump, attack, shield);
    }

    /** Spawn a projectile at the start of a ranged/throw swing. */
    private void fireIfNeeded(Fighter f) {
        if (f.dead || !f.attacking || f.firedThisSwing) {
            return;
        }
        Weapons.Def d = Weapons.DEFS[f.weapon];
        if (d.type == Weapons.MELEE) {
            return;
        }
        f.firedThisSwing = true;
        spawnShot(f, f.weapon, f.aimX, f.aimY, 1f, 1f);
        sfx(SoundFx.THROW);
        host.vibrate(8);

        if (f.ammo > 0) {
            f.ammo--;
            if (f.ammo <= 0) {
                f.ammo = -1;
                f.weapon = Weapons.FIST;
                fx.floatText(f.x, f.y - 60, "OUT OF AMMO", 0x88FFFFFF);
            }
        }
    }

    /** Creates one visible projectile without consuming basic-attack ammo. */
    private void spawnShot(Fighter f, int weapon, float dirX, float dirY,
                           float damageScale, float speedScale) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.2f) {
            dirX = f.facing;
            dirY = 0f;
            len = 1f;
        }
        dirX /= len;
        dirY /= len;
        Weapons.Def d = Weapons.DEFS[weapon];
        float speed = (d.speed > 0f ? d.speed : 900f) * speedScale;
        float ox = f.handX() + dirX * 14f;
        float oy = f.handY() + dirY * 14f;
        Projectile p = new Projectile(weapon, f.id == 0 ? 0 : 1, ox, oy,
                dirX * speed, dirY * speed);
        p.damage = d.damage * damageScale;
        shots.add(p);
        fx.sparks(ox, oy, d.color, 8, 150f);
    }

    /**
     * Release-triggered weapon skill, the control mechanic at the heart of
     * Supreme Duelist. Every weapon has a real, distinct skill and a cooldown.
     */
    private boolean useAbility(Fighter f, float dirX, float dirY) {
        if (f.dead || f.down || f.hurtT > 0f || f.spawnT > 0f || f.abilityT > 0f) {
            return false;
        }
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 0.35f) {
            return false;
        }
        dirX /= len;
        dirY /= len;
        f.aimX = dirX;
        f.aimY = dirY;
        f.weaponAngle = (float) Math.atan2(dirY, dirX);
        f.facing = dirX >= 0f ? 1 : -1;
        if (isFootball()) {
            if (!football.powerKick(f, dirX, dirY)) return false;
            f.abilityT = 2.4f;
            sfx(SoundFx.HIT);
            fx.ring(football.x, football.y, 0xFFFFD166, 32f, 0.3f);
            return true;
        }
        int w = f.weapon;
        f.abilityT = abilityCooldown(w);
        float angle = f.weaponAngle;

        switch (w) {
            case 0: // Fists: rushing power jab
                abilityDash(f, dirX, dirY, 650f, 1.65f);
                break;
            case 1: // Sword: directional dash slash
                abilityDash(f, dirX, dirY, 820f, 1.45f);
                break;
            case 2: // Spear: long lunge
                abilityDash(f, dirX, dirY, 940f, 1.35f);
                break;
            case 3: // Dual sabers: spinning all-around strike
                abilityShockwave(f, 16f, 430f, 135f);
                break;
            case 4: // Kunai: three-blade fan
                spreadShots(f, w, angle, 3, 0.17f, 0.80f, 1.08f);
                break;
            case 5: // Hammer: heavy shockwave
                abilityShockwave(f, 24f, 850f, 190f);
                break;
            case 6: // Bow: triple-arrow volley
                spreadShots(f, w, angle, 3, 0.13f, 0.80f, 1f);
                break;
            case 7: // Wand: five seeking bolts
                spreadShots(f, w, angle, 5, 0.15f, 0.65f, 1.05f);
                break;
            case 8: // Bomb: cluster throw
                spreadShots(f, w, angle, 3, 0.22f, 0.90f, 0.92f);
                break;
            case 9: // Boomerang: twin returning blades
                spreadShots(f, w, angle, 2, 0.18f, 0.85f, 1.22f);
                break;
            case 10: // Pistol: three-round spread, as in the original's Gun
                spreadShots(f, w, angle, 3, 0.12f, 0.85f, 1.05f);
                break;
            case 11: // Energy blade: dash plus a plasma wave
                abilityDash(f, dirX, dirY, 880f, 1.35f);
                spawnShot(f, w, dirX, dirY, 0.65f, 1.20f);
                break;
            default:
                return false;
        }
        fx.ring(f.x, f.y - 6f, Weapons.color(w), 34f, 0.35f);
        sfx(SoundFx.THROW);
        host.vibrate(18);
        return true;
    }

    private void abilityDash(Fighter f, float dx, float dy, float speed, float boost) {
        f.vx = dx * speed;
        f.vy = dy * speed;
        if (f.onGround && dy >= -0.2f) {
            f.vy -= 80f;
        }
        f.onGround = false;
        f.swingBoost = boost;
        f.startAttack();
    }

    private void spreadShots(Fighter f, int weapon, float angle, int count,
                             float spread, float damageScale, float speedScale) {
        for (int i = 0; i < count; i++) {
            float a = angle + (i - (count - 1) * 0.5f) * spread;
            spawnShot(f, weapon, (float) Math.cos(a), (float) Math.sin(a),
                    damageScale, speedScale);
        }
    }

    private void abilityShockwave(Fighter f, float damage, float knock, float radius) {
        Fighter foe = fighters[1 - f.id];
        fx.ring(f.x, f.y, Weapons.color(f.weapon), radius * 0.55f, 0.45f);
        fx.sparks(f.x, f.y, Weapons.color(f.weapon), 28, 380f);
        shake = Math.max(shake, 10f);
        if (foe.dead) {
            return;
        }
        float dx = foe.x - f.x;
        float dy = foe.y - f.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < radius) {
            float dir = dx >= 0f ? 1f : -1f;
            foe.damage(damage, dir * knock, -260f, false);
            fx.floatText(foe.x, foe.y - 60f, "-" + (int) damage, 0xFFFF8FA0);
            hitStop = 0.05f;
        }
    }

    private float abilityCooldown(int weapon) {
        switch (weapon) {
            case 0: return 2.4f;
            case 1: return 3.0f;
            case 2: return 3.4f;
            case 3: return 3.2f;
            case 4: return 3.6f;
            case 5: return 4.5f;
            case 6: return 3.8f;
            case 7: return 4.2f;
            case 8: return 5.0f;
            case 9: return 4.4f;
            case 10: return 3.6f;
            case 11: return 4.5f;
            default: return 3.5f;
        }
    }

    private void resolveMelee(Fighter a, Fighter b) {
        if (a.dead || b.dead || !a.meleeActive() || a.hitThisSwing) {
            return;
        }

        // Collision follows the rendered weapon, not a broad rectangle around
        // the fighter. Every simulated joint is a potential contact point, so
        // aiming above, below or away from a flopped body really matters.
        float hx = a.handX();
        float hy = a.handY();
        float tx = a.tipX();
        float ty = a.tipY();
        float contact = sweptWeaponContactDistance(a, b, hx, hy, tx, ty);
        if (contact > 23f) {
            return;
        }

        a.hitThisSwing = true;
        Weapons.Def d = Weapons.DEFS[a.weapon];
        float boost = a.swingBoost > 0f ? a.swingBoost : 1f;
        float damage = d.damage * boost;
        boolean shielded = b.shieldOn && b.shield > 0;
        float attackAngle = a.currentWeaponAngle();
        float kx = (float) Math.cos(attackAngle) * d.knock * boost;
        float ky = (float) Math.sin(attackAngle) * d.knock * 0.45f
                - 90f - d.knock * 0.12f;
        b.facing = kx >= 0f ? 1 : -1;
        b.damage(damage, kx, ky, false);
        float ix = (a.x + b.x) * 0.5f;
        float iy = (a.y + b.y) * 0.5f - 4f;
        if (shielded) {
            sfx(SoundFx.SHIELD);
            fx.sparks(ix, iy, 0xFF7DE8FF, 14, 260f);
            fx.ring(ix, iy, 0xFF8FF0FF, 20, 0.3f);
            shake = Math.max(shake, 3f);
        } else {
            sfx(SoundFx.HIT);
            fx.sparks(ix, iy, 0xFFFFE38A, 16, 320f);
            fx.blood(ix, iy, b.accent, 6, kx >= 0f ? 1f : -1f);
            fx.ring(ix, iy, 0xFFFFD166, 16, 0.22f);
            shake = Math.max(shake, 6f);
            hitStop = 0.045f;
            host.vibrate(12);
            fx.floatText(ix, iy - 34f, "-" + (int) damage, 0xFFFF8FA0);
        }
        if (b.health <= 0f) {
            fx.ring(b.x, b.y, 0xFFFF5D5D, 40, 0.5f);
            shake = 12f;
        }
    }

    /**
     * Minimum body distance across the blade's segment over the previous frame.
     * Sampling the interpolated segment catches quick slashes and dash attacks
     * that would otherwise tunnel between two 60 Hz collision checks.
     */
    private float sweptWeaponContactDistance(Fighter attacker, Fighter target,
                                             float hx, float hy, float tx, float ty) {
        if (!attacker.prevWeaponPoseValid) {
            return weaponContactDistance(target, hx, hy, tx, ty);
        }
        float best = Float.MAX_VALUE;
        final int samples = 6;
        for (int i = 0; i <= samples; i++) {
            float t = i / (float) samples;
            float shx = attacker.prevWeaponHandX
                    + (hx - attacker.prevWeaponHandX) * t;
            float shy = attacker.prevWeaponHandY
                    + (hy - attacker.prevWeaponHandY) * t;
            float stx = attacker.prevWeaponTipX
                    + (tx - attacker.prevWeaponTipX) * t;
            float sty = attacker.prevWeaponTipY
                    + (ty - attacker.prevWeaponTipY) * t;
            best = Math.min(best, weaponContactDistance(target, shx, shy, stx, sty));
        }
        return best;
    }

    /** Minimum distance from a fighter's body to the current weapon segment. */
    private float weaponContactDistance(Fighter f, float x1, float y1, float x2, float y2) {
        if (f.body == null) {
            return pointSegmentDistance(f.x, f.y - 6f, x1, y1, x2, y2);
        }
        float best = Float.MAX_VALUE;
        for (int i = 0; i < Ragdoll.N; i++) {
            best = Math.min(best, pointSegmentDistance(
                    f.body.x[i], f.body.y[i], x1, y1, x2, y2));
        }
        return best;
    }

    /** Swept projectile test against the torso or each actual ragdoll joint. */
    private float projectileContactT(Fighter f, Projectile p) {
        if (f.body == null) {
            return Projectile.segmentCircleEntry(p.prevX, p.prevY, p.x, p.y,
                    f.x, f.y - 6f, p.r + 24f);
        }
        float first = Float.MAX_VALUE;
        for (int i = 0; i < Ragdoll.N; i++) {
            float t = Projectile.segmentCircleEntry(p.prevX, p.prevY, p.x, p.y,
                    f.body.x[i], f.body.y[i], p.r + f.body.collisionRadius(i));
            if (t >= 0f) {
                first = Math.min(first, t);
            }
        }
        return first == Float.MAX_VALUE ? -1f : first;
    }

    private float pointSegmentDistance(float px, float py,
                                       float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        float t = len2 < 0.001f ? 0f
                : ((px - x1) * dx + (py - y1) * dy) / len2;
        t = Math.max(0f, Math.min(1f, t));
        float qx = x1 + dx * t;
        float qy = y1 + dy * t;
        float ox = px - qx;
        float oy = py - qy;
        return (float) Math.sqrt(ox * ox + oy * oy);
    }

    private void updateShots(float dt) {
        for (int i = shots.size() - 1; i >= 0; i--) {
            Projectile p = shots.get(i);
            p.update(dt);

            Fighter foe = fighters[1 - p.owner];
            Fighter self = fighters[p.owner];

            if (p.special == 2 && !foe.dead) {
                float dx = foe.x - p.x;
                float dy = foe.y - 6f - p.y;
                float len = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                float sp = 560f;
                p.vx += (dx / len) * 900f * dt;
                p.vy += (dy / len) * 900f * dt;
                float cur = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
                p.vx = p.vx / cur * sp;
                p.vy = p.vy / cur * sp;
            }
            if (p.special == 3) {
                if (!p.returning && p.timer > 0.45f) {
                    p.returning = true;
                    p.hitThisPass = false; // fresh pass on the way back
                }
                if (p.returning) {
                    float dx = self.x - p.x;
                    float dy = self.y - 6f - p.y;
                    float len = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
                    p.vx = dx / len * 900f;
                    p.vy = dy / len * 900f;
                    if (len < 30f) {
                        p.dead = true;
                    }
                }
            }

            // Sweep the projectile path for this frame; point-only checks let
            // fast shots tunnel through thin platforms at low frame rates.
            float wallT = Float.MAX_VALUE;
            for (int k = 0; k < arena.platforms.size(); k++) {
                float impactT = Projectile.segmentRectEntry(arena.platforms.get(k).r,
                        p.prevX, p.prevY, p.x, p.y, p.r);
                if (impactT >= 0f) {
                    wallT = Math.min(wallT, impactT);
                }
            }
            boolean canHit = p.special != 3 || (!p.hitThisPass && p.hitCount < 2);
            float hitT = !p.dead && !foe.dead && canHit ? projectileContactT(foe, p) : -1f;
            // Resolve the earliest event on the ORIGINAL segment, not list order.
            boolean hitWall = wallT != Float.MAX_VALUE && (hitT < 0f || wallT <= hitT);
            if (hitWall) {
                p.x = p.prevX + (p.x - p.prevX) * wallT;
                p.y = p.prevY + (p.y - p.prevY) * wallT;
            }
            if (hitWall || p.x < arena.bounds.left - 200 || p.x > arena.bounds.right + 200
                    || p.y > arena.bounds.bottom + 300) {
                if (p.special == 1) {
                    explode(p);
                } else {
                    fx.sparks(p.x, p.y, p.color, 8, 160f);
                }
                p.dead = true;
            }

            if (!p.dead && !foe.dead && p.owner != foe.id) {
                // A boomerang survives its hit, so it is capped to one hit per
                // pass and two per throw. Without this it re-hit every frame it
                // overlapped - six hits for a single throw.
                if (hitT >= 0f) {
                    // Place the impact at first contact on this frame's path,
                    // not beyond the fighter on a fast frame.
                    p.x = p.prevX + (p.x - p.prevX) * hitT;
                    p.y = p.prevY + (p.y - p.prevY) * hitT;
                    if (p.special == 3) {
                        p.hitCount++;
                        p.hitThisPass = true;
                    }
                    if (p.special == 1) {
                        explode(p);
                    } else {
                        float n = 1f;
                        if (p.special == 3) {
                            n = p.returning ? 1f : 0.8f;
                        }
                        foe.facing = p.vx >= 0 ? 1 : -1;
                        foe.damage(p.damage * n,
                                (p.vx > 0 ? 1 : -1) * p.knock, -120f, false);
                        sfx(SoundFx.HIT);
                        fx.sparks(p.x, p.y, p.color, 12, 240f);
                        fx.floatText(p.x, p.y - 26f, "-" + (int) (p.damage * n), 0xFFFF8FA0);
                        shake = Math.max(shake, 4f);
                        hitStop = 0.03f;
                        host.vibrate(10);
                    }
                    if (!(p.special == 3 && !p.returning)) {
                        p.dead = true;
                    }
                }
            }

            if (p.dead) {
                shots.remove(i);
            }
        }
    }

    private void explode(Projectile p) {
        fx.ring(p.x, p.y, 0xFFFFB347, 26, 0.45f);
        fx.ring(p.x, p.y, 0xFFFF7A3C, 50, 0.55f);
        fx.sparks(p.x, p.y, 0xFFFFC46B, 34, 460f);
        fx.sparks(p.x, p.y, 0xFF8A5A2A, 18, 260f);
        shake = Math.max(shake, 14f);
        host.vibrate(30);
        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            if (f.dead) {
                continue;
            }
            float dx = f.x - p.x;
            float dy = (f.y - 6f) - p.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 130f) {
                float t = 1f - d / 130f;
                f.damage(30f * t + 8f, (dx >= 0 ? 1 : -1) * (420f * t + 120f), -300f * t - 60f, false);
                fx.floatText(f.x, f.y - 50f, "-" + (int) (30 * t + 8), 0xFFFF8FA0);
            }
        }
    }

    private void updatePickups(float dt) {
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup pk = pickups.get(i);
            pk.t += dt;
            pk.vy += 1500f * dt;
            pk.y += pk.vy * dt;
            for (int k = 0; k < arena.platforms.size(); k++) {
                RectF r = arena.platforms.get(k).r;
                if (pk.x > r.left && pk.x < r.right && pk.y + 12 > r.top && pk.y + 12 < r.top + 40
                        && pk.vy > 0) {
                    pk.y = r.top - 12;
                    pk.vy = 0;
                    pk.onGround = true;
                }
            }
            for (int f = 0; f < 2; f++) {
                Fighter ft = fighters[f];
                if (ft.dead) {
                    continue;
                }
                if (Math.abs(ft.x - pk.x) < 38f && Math.abs(ft.y - pk.y) < 60f) {
                    ft.giveWeapon(pk.weapon);
                    sfx(SoundFx.PICKUP);
                    fx.ring(pk.x, pk.y, Weapons.color(pk.weapon), 18, 0.35f);
                    fx.floatText(pk.x, pk.y - 40f, Weapons.name(pk.weapon), Weapons.color(pk.weapon));
                    pickups.remove(i);
                    break;
                }
            }
        }
    }

    private void spawnPickup() {
        if (arena.platforms.isEmpty()) {
            return;
        }
        Maps.Platform pl = arena.platforms.get(rnd.nextInt(arena.platforms.size()));
        float x = pl.r.left + 40 + rnd.nextFloat() * Math.max(20, pl.r.width() - 80);
        Pickup pk = new Pickup();
        pk.weapon = rndWeapon();
        pk.x = x;
        pk.y = pl.r.top - 160;
        pickups.add(pk);
    }

    private void checkHazards(Fighter f, float dt) {
        if (f.dead) {
            return;
        }
        for (int i = 0; i < arena.hazards.size(); i++) {
            Maps.Hazard hz = arena.hazards.get(i);
            RectF r = hz.r;
            float halfW = f.down ? 30f : Fighter.HALF_W;
            float top = f.down ? 16f : (f.crouch ? Fighter.CROUCH_HEAD : Fighter.HEAD);
            float bot = f.down ? 16f : Fighter.FEET;
            boolean inside = f.x + halfW > r.left && f.x - halfW < r.right
                    && f.y + bot > r.top && f.y - top < r.bottom;
            if (!inside) {
                continue;
            }
            if (profile.instantKO) {
                f.kill(0);
                fx.ring(f.x, f.y, 0xFFFF9F43, 34, 0.5f);
                fx.blood(f.x, f.y, f.accent, 10, 0);
                shake = 9f;
                return;
            }
            float dps = hz.type == Maps.HAZ_LAVA ? 45f : (hz.type == Maps.HAZ_SPIKES ? 35f : 26f);
            f.health -= dps * dt;
            f.flashT = 0.08f;
            if (rnd.nextFloat() < dt * 8f) {
                fx.sparks(f.x, f.y + 20, hz.type == Maps.HAZ_LAVA ? 0xFFFF7A3C : 0xFF6BD8FF,
                        3, 120f);
            }
            if (f.health <= 0) {
                f.health = 0;
                f.kill(0);
                shake = 8f;
            }
        }
    }

    private void checkBounds(Fighter f) {
        if (!f.dead && f.y > arena.bounds.bottom + 140) {
            f.kill(0);
        }
    }

    private void onRoundEnd(boolean aDead) {
        phase = PH_ROUND_END;
        phaseT = 2.2f;
        boolean bDead = fighters[1].dead;
        if (aDead && bDead) {
            // A draw does not award a point. This also prevents a 2-2 score from
            // being sent through endMatch(false) as a contradictory "defeat".
            roundDraw = true;
        } else if (bDead) {
            roundWinsA++;
        } else {
            roundWinsB++;
        }
        shots.clear(); // no post-K.O. projectile can kill the declared winner
        releaseControls();
        shake = 10f;
        fx.ring((fighters[0].x + fighters[1].x) * 0.5f,
                (fighters[0].y + fighters[1].y) * 0.5f - 10, 0xFFFFE38A, 60, 0.7f);
        if (profile.voice) {
            announce(roundDraw ? "Double K O!" : "K O!");
        }
        sfx(SoundFx.KO);
        host.vibrate(60);
    }

    private void nextRound() {
        if (isFootball()) {
            if (roundWinsA >= 3 || roundWinsB >= 3) {
                endMatch(roundWinsA > roundWinsB);
                return;
            }
            releaseControls();
            fighters[0].reset(arena.spawnAx, arena.spawnAy);
            fighters[1].reset(arena.spawnBx, arena.spawnBy);
            fighters[0].giveWeapon(Weapons.FIST);
            fighters[1].giveWeapon(Weapons.FIST);
            football.reset();
            phase = PH_INTRO;
            phaseT = 3.4f;
            lastCount = -1;
            countAnnounced = false;
            return;
        }
        if (roundWinsA >= 2 || roundWinsB >= 2) {
            endMatch(roundWinsA > roundWinsB);
            return;
        }
        roundNumber++;
        roundDraw = false;
        releaseControls();
        fighters[0].reset(Maps.safeSpawnX(arena, arena.spawnAx, arena.spawnAy + Fighter.FEET),
                arena.spawnAy);
        fighters[1].reset(Maps.safeSpawnX(arena, arena.spawnBx, arena.spawnBy + Fighter.FEET),
                arena.spawnBy);
        fighters[0].giveWeapon(profile.starter);
        fighters[1].giveWeapon(rndWeapon());
        shots.clear();
        pickups.clear();
        phase = PH_INTRO;
        phaseT = 3.4f;
        lastCount = -1;
        countAnnounced = false;
        cpu.reset();
        hitStop = 0f;
        cameraReady = false;
        zoomHold = 0f;
        updateCamera(0f);
    }

    private void onEnemyDown() {
        shots.clear();
        releaseControls();
        wave++;
        fighters[0].score = wave - 1;
        profile.addCoins(20);
        fx.floatText(fighters[1].x, fighters[1].y - 60, "+20", 0xFFFFD166);
        phase = PH_SURVIVAL_BREAK;
        phaseT = 1.8f;
        shake = 8f;
        if (profile.voice) {
            announce("Wave " + wave);
        }
    }

    private void onBossDown() {
        shots.clear();
        pickups.clear();
        releaseControls();
        bossesDefeated++;
        profile.addCoins(25);
        fx.floatText(fighters[1].x, fighters[1].y - 60f, "+25", 0xFFFFD166);
        shake = 10f;
        sfx(SoundFx.KO);
        host.vibrate(65);
        if (bossesDefeated >= BOSS_NAMES.length) {
            endMatch(true);
            return;
        }
        phase = PH_BOSS_BREAK;
        phaseT = 2.0f;
        if (profile.voice) {
            announce(BOSS_NAMES[bossIndex] + " defeated");
        }
    }

    private void spawnWave() {
        Fighter p = fighters[0];
        Fighter e = fighters[1];
        // Survival used to cap the player at 100 HP while enemy HP climbed 26
        // per wave (+26% of the player's whole bar), so it became unwinnable by
        // about wave 4. Now the player grows a little and the curve is gentler.
        p.maxHealth = Math.min(140f, 100f + (wave - 1) * 6f);
        p.health = Math.min(p.maxHealth, p.health + 30f);
        p.shield = p.maxShield;
        float hpm = 70f + wave * 18f;
        e.reset(Maps.safeSpawnX(arena, arena.spawnBx, arena.spawnBy + Fighter.FEET),
                arena.spawnBy);
        e.maxHealth = hpm;
        e.health = hpm;
        e.color = CPU_COLOR;
        e.accent = 0xFFFF4D6D;
        e.giveWeapon(rndWeapon());
        e.name = "WAVE " + wave;
        e.cpu = true;
        cpu.reset();
        phase = PH_FIGHT;
        spawnTimer = 3f;
    }

    private void endMatch(boolean won) {
        playerWon = won;
        phase = PH_MATCH_END;
        phaseT = 1.3f;
        if (won) {
            resultTitle = "VICTORY";
            resultCoins = mode == M_SURVIVAL ? 40 : (mode == M_BOSS ? 100 : 70);
            profile.wins++;
            profile.streak++;
            profile.bestStreak = Math.max(profile.bestStreak, profile.streak);
        } else {
            resultTitle = "DEFEAT";
            resultCoins = 15;
            profile.losses++;
            profile.streak = 0;
        }
        if (mode == M_SURVIVAL) {
            resultSub = "Reached wave " + wave + "  ·  " + (wave - 1) + " takedowns";
        } else if (mode == M_BOSS) {
            resultSub = "Bosses defeated " + bossesDefeated + "/" + BOSS_NAMES.length
                    + "  ·  " + arena.name;
        } else {
            resultSub = (isFootball() ? "Goals " : "Rounds ") + roundWinsA + " - " + roundWinsB + "  ·  " + arena.name;
        }
        profile.addCoins(resultCoins);
        profile.save();
        host.vibrate(won ? 40 : 20);
        if (profile.voice) {
            announce(won ? "Victory" : "Defeat");
        }
        if (won) {
            fx.confetti(viewW, viewH);
        }
    }

    private void finishMatch() {
        screen = S_RESULT;
        paused = false;
        uiBtns.clear();
    }

    private void updateWorldFx(float dt) {
        for (int i = 0; i < 2; i++) {
            fighters[i].update(dt, arena, isFootball() || profile.gravity,
                    !isFootball() && profile.energyShield, false);
        }
        updateShots(dt);
        updatePickups(dt);
    }

    /**
     * Hands a dead fighter over to its jointed body. Runs before the hit-stop
     * guard so the corpse appears on the same frame as the KO, not three frames
     * of hit-stop later (which read as the loser blinking out of existence).
     */
    private void spawnRagdolls() {
        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            if (f.justDown && !f.dead) {
                f.justDown = false;
                f.limp = 1f; // the body it already has goes fully limp
                fx.dust(f.x, f.y + Fighter.FEET, 0x77FFFFFF, 7, 30f);
                fx.ring(f.x, f.y + Fighter.FEET - 10f, 0xFFFFE38A, 24, 0.3f);
                fx.floatText(f.x, f.y - 70f, "DOWN!", 0xFFFFC46B);
                shake = Math.max(shake, 7f);
                sfx(SoundFx.HIT);
                host.vibrate(25);
            }
            if (f.justDied) {
                f.justDied = false;
                f.ensureBody();
                // keep the pose it died in, don't teleport it
                f.body.reborn();
                fx.dust(f.x, f.y + Fighter.FEET, 0x66FFFFFF, 6, 26f);
            }
        }
    }

    private void updateRagdolls(float dt) {
        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            if (f.down && f.body == null) {
                f.ensureBody();
            }
            Ragdoll body = f.body;
            if (body == null || !f.ragdolled()) {
                continue; // standing bodies are stepped by Fighter.update
            }
            body.facing = f.facing;
            body.hair = f.hair;
            body.accent = f.accent;
            body.update(dt, arena, null, null, 0f, profile.gravity ? 1f : 0f);

            if (f.down) {
                // the hips are the fighter's real position while it is prone, so
                // hazards, the camera and projectiles all see the body
                f.x = body.x[Ragdoll.HIPS];
                f.y = body.y[Ragdoll.HIPS];
                boolean settled = body.quiet && f.downT > 0.85f;
                if (settled || f.downT > 1.9f || body.gone()) {
                    recoverFromRagdoll(f);
                }
            } else if (body.gone()) {
                f.body = null; // corpse has faded out
            }
        }
    }

    /** Stands a knocked-down fighter back up on whatever surface it is lying on. */
    private void recoverFromRagdoll(Fighter f) {
        if (f.dead) {
            return;
        }
        Ragdoll body = f.body;
        float hx = body != null ? body.x[Ragdoll.HIPS] : f.x;
        float hy = body != null ? body.y[Ragdoll.HIPS] : f.y;

        if (hy > arena.bounds.bottom + 60f) {
            f.down = false;
            f.kill(0); // fell out of the world while prone
            return;
        }

        float bestTop = Float.NaN;
        for (int i = 0; i < arena.platforms.size(); i++) {
            RectF r = arena.platforms.get(i).r;
            if (hx < r.left + Fighter.HALF_W || hx > r.right - Fighter.HALF_W) {
                continue;
            }
            if (r.top < hy - 40f || r.top > hy + 90f) {
                continue; // not the surface the body is lying on
            }
            if (Float.isNaN(bestTop) || r.top < bestTop) {
                bestTop = r.top;
            }
        }
        float nx = Math.max(arena.bounds.left + Fighter.HALF_W,
                Math.min(arena.bounds.right - Fighter.HALF_W, hx));
        float ny = Float.isNaN(bestTop) ? hy - 10f : bestTop - Fighter.FEET;

        f.recoverAt(nx, ny);
        fx.dust(nx, ny + Fighter.FEET, 0x88FFFFFF, 5, 22f);
        sfx(SoundFx.JUMP);
    }

    /** Track live positions, never a corpse falling below the arena. */
    private float focusX(Fighter f) {
        float x = f.down && f.body != null ? f.body.x[Ragdoll.HIPS] : f.x;
        return Math.max(arena.bounds.left, Math.min(arena.bounds.right, x));
    }

    private float focusY(Fighter f) {
        float y = f.down && f.body != null ? f.body.y[Ragdoll.HIPS] : f.y;
        float floor = arena.platforms.get(0).r.top;
        return Math.max(floor - 520f, Math.min(floor + 25f, y));
    }

    private void updateCamera(float dt) {
        if (isFootball()) {
            zoom = Math.min(viewW / 1560f, (viewH - 180f) / 500f);
            camX = (Football.WIDTH - viewW / zoom) * 0.5f;
            camY = Football.CEILING - 95f / zoom;
            cameraReady = true;
            return;
        }
        if (arena.platforms.isEmpty()) return;
        // Keep the last combat framing during a K.O./result. Corpses are free
        // to fall/fade without dragging the camera out of the fighting space.
        if (cameraReady && phase != PH_INTRO && phase != PH_FIGHT) return;
        float minX = Math.min(focusX(fighters[0]), focusX(fighters[1])) - 100f;
        float maxX = Math.max(focusX(fighters[0]), focusX(fighters[1])) + 100f;
        float minY = Math.min(focusY(fighters[0]), focusY(fighters[1])) - Fighter.HEAD - 35f;
        float maxY = Math.max(focusY(fighters[0]), focusY(fighters[1])) + Fighter.FEET + 22f;
        final float top = 105f, bottom = viewH - 180f;
        float fit = Math.min((viewW - 96f) / (maxX - minX), (bottom - top) / (maxY - minY));
        float targetZoom = Math.max(ZOOM_MIN, Math.min(ZOOM, fit));
        if (cameraReady && dt > 0f) {
            zoomHold = Math.max(0f, zoomHold - dt);
            if (targetZoom < zoom - 0.07f) {
                // Fit immediately: do not clip a fighter while slowly zooming out.
                zoom = targetZoom;
                zoomHold = 1.0f;
            } else if (targetZoom > zoom + 0.12f && zoomHold <= 0f) {
                zoom += (targetZoom - zoom) * (1f - (float) Math.exp(-2f * dt));
            }
        } else {
            zoom = targetZoom;
        }
        float targetX = (minX + maxX) * 0.5f - viewW / (2f * zoom);
        float floor = arena.platforms.get(0).r.top;
        float targetY = floor - (bottom - 20f) / zoom;
        float low = maxY - bottom / zoom;
        float high = minY - top / zoom;
        targetY = Math.max(low, Math.min(high, targetY));
        if (!cameraReady || dt <= 0f) {
            camX = targetX;
            camY = targetY;
        } else {
            float k = 1f - (float) Math.exp(-6f * dt);
            camX += (targetX - camX) * k;
            camY += (targetY - camY) * k;
            // Soft follow inside a hard visibility envelope, clear of HUD/sticks.
            camX = Math.max(maxX - (viewW - 48f) / zoom,
                    Math.min(minX - 48f / zoom, camX));
            camY = Math.max(low, Math.min(high, camY));
        }
        cameraReady = true;
    }

    // ------------------------------------------------------------------ draw

    public synchronized void draw(Canvas canvas) {
        Painter g = new Painter(canvas);
        canvas.drawColor(0xFF0B0F17);

        if (screen == S_PLAY || screen == S_RESULT) {
            drawWorld(g);
            if (screen == S_PLAY) {
                drawHud(g);
            }
            if (paused) {
                drawPause(g);
            }
            if (screen == S_RESULT) {
                drawResult(g);
            }
            return;
        }

        drawMenuBackground(g, screen);
        uiBtns.clear();
        if (screen == S_TITLE) {
            drawTitle(g);
        } else if (screen == S_MAPS) {
            drawMaps(g);
        } else if (screen == S_ARSENAL) {
            drawArsenal(g);
        } else if (screen == S_SETTINGS) {
            drawSettings(g);
        } else if (screen == S_STYLE) {
            drawStyle(g);
        } else if (screen == S_HOW) {
            drawHow(g);
        }
    }

    private void beginUi(Canvas c) {
        c.save();
        c.scale(scale, scale);
    }

    private void drawWorld(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);

        float shx = 0, shy = 0;
        if (shake > 0.2f) {
            shx = (rnd.nextFloat() - 0.5f) * shake;
            shy = (rnd.nextFloat() - 0.5f) * shake;
        }

        // sky
        g.gradient(0, 0, viewW, viewH, arena.skyTop, arena.skyBottom);

        c.save();
        c.scale(zoom * 0.55f, zoom * 0.55f);
        c.translate(-camX * 0.35f + shx * 0.4f, -camY * 0.2f + shy * 0.4f);
        drawBackdrop(g);
        c.restore();

        c.save();
        c.scale(zoom, zoom);
        c.translate(-camX + shx / zoom, -camY + shy / zoom);
        drawArena(g);
        if (isFootball()) football.draw(g);
        for (int i = 0; i < pickups.size(); i++) {
            drawPickup(g, pickups.get(i));
        }
        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            if (f.ragdolled()) {
                if (f.body != null) {
                    f.body.draw(g);
                    f.drawRagdollWeapon(g);
                }
                continue;
            }
            g.alpha(40);
            g.oval(f.x, f.y + Fighter.FEET - 1f, 22, 6, 0xFF000000);
            g.reset();
            f.draw(g);
        }
        for (int i = 0; i < shots.size(); i++) {
            drawShot(g, shots.get(i));
        }
        fx.draw(g);
        c.restore();

        if (screen == S_PLAY && !paused) {
            drawControls(g);
        }
        if (screen == S_PLAY) {
            drawIntroOverlay(g);
        }
        c.restore();
        g.reset();
    }

    private void drawBackdrop(Painter g) {
        float baseY = arena.bounds.bottom;
        int deco = arena.deco;
        if (arenaIndex == 1) {
            for (int i = 0; i < 24; i++) {
                float x = i * 92f - 60f;
                float hgt = 130f + ((i * 73) % 210);
                g.rect(x, baseY - hgt - 40, 66, hgt, Painter.withAlpha(deco, 150));
                for (int win = 0; win < 4; win++) {
                    g.rect(x + 12, baseY - hgt - 20 + win * 34, 8, 10, 0x33FFE38A);
                    g.rect(x + 34, baseY - hgt - 20 + win * 34, 8, 10, 0x22FFE38A);
                }
            }
        } else if (arenaIndex == 5) {
            for (int i = 0; i < 20; i++) {
                float x = i * 105f - 40f + (i % 3) * 14;
                g.rect(x, baseY - 360, 15, 360, 0xFF2E5A32);
                g.rect(x + 3, baseY - 360, 5, 360, 0xFF4E8A46);
                g.circle(x + 7, baseY - 366, 40, 0xFF2E5A32);
                g.circle(x + 4, baseY - 372, 26, 0xFF3E7440);
            }
        } else if (arenaIndex == 6) {
            for (int i = 0; i < 40; i++) {
                float x = (i * 137f) % 1600;
                float y = 80 + ((i * 91) % 420);
                g.circle(x, y, 2.2f, 0x88FFFFFF);
            }
        } else {
            for (int i = 0; i < 7; i++) {
                float x = i * 230f - 120f;
                float hgt = 190 + ((i * 53) % 140);
                Path hill = new Path();
                hill.moveTo(x, baseY);
                hill.lineTo(x + 150, baseY - hgt);
                hill.lineTo(x + 320, baseY);
                hill.close();
                g.path(hill, Painter.withAlpha(deco, 55));
            }
        }
    }

    private void drawArena(Painter g) {
        for (int i = 0; i < arena.hazards.size(); i++) {
            Maps.Hazard hz = arena.hazards.get(i);
            RectF hr = hz.r;
            if (hz.type == Maps.HAZ_WATER) {
                g.rect(hr.left, hr.top, hr.width(), hr.height(), 0xFF1E5A88);
                g.rect(hr.left, hr.top, hr.width(), 12f, 0xFF3D8FC4);
                for (int k = 0; k < 16; k++) {
                    float wx = hr.left + (k / 16f) * hr.width() + 18;
                    float wy = hr.top + 14 + (float) Math.sin(time * 2.2f + k) * 5f;
                    g.stroke(wx, wy, wx + 46, wy, 3, 0x77BFEBFF);
                }
            } else if (hz.type == Maps.HAZ_LAVA) {
                g.rect(hr.left, hr.top, hr.width(), hr.height(), 0xFF6E1E0A);
                g.rect(hr.left, hr.top, hr.width(), 10f, 0xFFFF7A3C);
                for (int k = 0; k < 18; k++) {
                    float wx = hr.left + (k / 18f) * hr.width() + 22;
                    float wy = hr.top + 12 + (float) Math.sin(time * 1.7f + k * 1.3f) * 6f;
                    g.circle(wx, wy, 7, 0x99FFA347);
                }
            } else {
                g.rect(hr.left, hr.top, hr.width(), hr.height(), 0x44140A20);
                for (int k = 0; k < 20; k++) {
                    float wx = hr.left + 16 + k * (hr.width() / 20f);
                    Path spike = new Path();
                    spike.moveTo(wx - 9, hr.top + 26);
                    spike.lineTo(wx, hr.top);
                    spike.lineTo(wx + 9, hr.top + 26);
                    spike.close();
                    g.path(spike, 0xFFB8B8CC);
                }
            }
        }

        for (int i = 0; i < arena.platforms.size(); i++) {
            Maps.Platform p = arena.platforms.get(i);
            RectF r = p.r;
            int top = arena.accent;
            int body = arena.ground;
            if (p.style == 1) {
                body = Painter.mix(arena.ground, 0xFF8A5A2A, 0.45f);
                top = Painter.mix(arena.accent, 0xFFD8A45A, 0.5f);
            } else if (p.style == 2) {
                body = Painter.mix(arena.ground, 0xFF6A7A8A, 0.5f);
                top = Painter.mix(arena.accent, 0xFFB8C8D8, 0.5f);
            } else if (p.style == 3) {
                body = Painter.mix(arena.ground, 0xFF4AD8E8, 0.35f);
                top = Painter.mix(arena.accent, 0xFF9AF0FF, 0.6f);
            }
            g.rrect(r.left, r.top, r.width(), r.height(), 8, body);
            g.rect(r.left, r.top, r.width(), 7f, top);
            g.rect(r.left, r.top + 7f, r.width(), 3f, Painter.withAlpha(0xFF000000, 60));
        }

    }

    private void drawShot(Painter g, Projectile p) {
        g.c.save();
        g.c.translate(p.x, p.y);
        g.c.rotate((float) Math.toDegrees(p.angle));
        if (p.special == 1) {
            g.circle(0, 0, p.r, 0xFF3A3A44);
            g.circle(-3, -3, 3, 0xFF6A6A78);
            g.stroke(6, -12, 14, -20, 2, 0xFFD8A45A);
        } else if (p.weapon == 6) {
            g.stroke(-20, 0, 16, 0, 3, 0xFFE8D8B8);
            g.path(triangle(16, -6, 28, 0, 16, 6), p.color);
        } else if (p.weapon == 9) {
            g.circle(0, 0, 8, Painter.withAlpha(p.color, 90));
            g.path(diamond(0, 0, 13), p.color);
        } else {
            g.rrect(-12, -3, 24, 6, 3, p.color);
            g.circle(11, 0, 4, 0xFFDDDDDD);
        }
        g.c.restore();
        g.reset();
    }

    private void drawPickup(Painter g, Pickup pk) {
        float bob = (float) Math.sin(pk.t * 3f) * 4f;
        g.circle(pk.x, pk.y + bob, 22, Painter.withAlpha(Weapons.color(pk.weapon), 40));
        g.line.setColor(Painter.withAlpha(Weapons.color(pk.weapon), 150));
        g.line.setStrokeWidth(2);
        g.c.drawCircle(pk.x, pk.y + bob, 20, g.line);
        g.c.save();
        g.c.translate(pk.x - 14, pk.y + bob);
        g.circle(0, 0, 7, Weapons.color(pk.weapon));
        g.stroke(-6, 0, 12, 0, 4, Weapons.color(pk.weapon));
        g.c.restore();
        g.reset();
        g.size(11);
        g.textCenter(Weapons.name(pk.weapon), pk.x, pk.y + bob - 28, 0xCCFFFFFF);
    }

    private void drawControls(Painter g) {
        drawStick(g, stickA, 0xFF4D9BFF, fighters[0]);
        if (isLocal()) {
            drawStick(g, stickB, 0xFFFF6B8A, fighters[1]);
        }

        // pause
        g.rrect(pauseRect.left, pauseRect.top, pauseRect.width(), pauseRect.height(), 10,
                0x66000000);
        g.rect(pauseRect.left + 14, pauseRect.top + 11, 5, 22, 0xCCFFFFFF);
        g.rect(pauseRect.left + 25, pauseRect.top + 11, 5, 22, 0xCCFFFFFF);
    }

    private void drawStick(Painter g, Stick s, int color, Fighter f) {
        g.line.setColor(0x44FFFFFF);
        g.line.setStrokeWidth(3);
        g.c.drawCircle(s.baseX, s.baseY, s.radius, g.line);
        g.circle(s.baseX, s.baseY, s.radius, 0x14000000);
        float kx = s.baseX + s.dx * s.radius * 0.8f;
        float ky = s.baseY + s.dy * s.radius * 0.8f;
        g.circle(kx, ky, 34, Painter.withAlpha(color, 120));
        g.circle(kx, ky, 28, Painter.withAlpha(color, 220));
        g.line.setColor(0x22FFFFFF);
        g.line.setStrokeWidth(2);
        g.c.drawCircle(s.baseX, s.baseY, s.radius * 0.55f, g.line);

        float charge = 1f - Math.max(0f, Math.min(1f,
                f.abilityT / abilityCooldown(f.weapon)));
        boolean ready = f.abilityT <= 0f && !f.dead;
        g.line.setColor(ready ? 0xFFFFD166 : Painter.withAlpha(color, 150));
        g.line.setStrokeWidth(4f);
        float ring = s.radius + 8f;
        g.c.drawArc(new RectF(s.baseX - ring, s.baseY - ring,
                s.baseX + ring, s.baseY + ring), -90f, 360f * charge, false, g.line);
        g.size(10f);
        float labelY = s.baseY + s.radius + 25f;
        if (labelY > viewH - 5f) {
            labelY = s.baseY - s.radius - 12f;
        }
        g.textCenter(ready ? (isFootball() ? "RELEASE · POWER KICK" : "RELEASE · SKILL") : "SKILL " + (int) (charge * 100f) + "%",
                s.baseX, labelY, ready ? 0xFFFFD166 : 0x99FFFFFF);
    }


    private void drawIntroOverlay(Painter g) {
        if (phase == PH_INTRO) {
            int count = (int) Math.ceil(phaseT - 0.6f);
            String s = count > 0 ? String.valueOf(count) : "FIGHT!";
            if (count > 0) {
                g.line.setColor(0x66FFE38A);
                g.line.setStrokeWidth(4f);
                g.c.drawCircle(viewW / 2, viewH / 2 - 14, 66, g.line);
                g.line.setColor(0x33FFFFFF);
                g.line.setStrokeWidth(2f);
                g.c.drawCircle(viewW / 2, viewH / 2 - 14, 82, g.line);
            }
            g.size(count > 0 ? 96 : 78);
            g.textShadowCenter(s, viewW / 2, viewH / 2 + 20, count > 0 ? 0xFFFFE38A : 0xFFFF7A9A, 5);
            g.size(14);
            g.textCenter(arena.name, viewW / 2, viewH - 26, 0x99FFFFFF);
        } else if (phase == PH_ROUND_END) {
            g.size(roundDraw ? 62 : 84);
            g.textShadowCenter(isFootball() ? "GOAL!" : (roundDraw ? "DOUBLE K.O." : "K.O."),
                    viewW / 2, viewH / 2, 0xFFFF6B8A, 5);
        } else if (phase == PH_BOSS_BREAK) {
            g.size(46);
            g.textShadowCenter("BOSS DEFEATED", viewW / 2, viewH / 2 - 14,
                    0xFFFFD166, 4);
            g.size(20);
            g.textCenter("NEXT: " + BOSS_NAMES[bossesDefeated], viewW / 2,
                    viewH / 2 + 28, 0xDDEEEEFF);
        }
    }

    private void drawHud(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.rect(0, 0, viewW, 78, 0xE60A0E16);
        g.rect(0, 78, viewW, 2, 0x22FFFFFF);
        float barW = Math.min(260f, viewW * 0.26f);
        if (isFootball()) {
            g.size(16);
            g.text(fighters[0].name + " · GREEN", 20, 34, 0xFF6BE88A);
            g.text("ATTACK RIGHT GOAL", 20, 58, 0xAAFFFFFF);
            g.align(Paint.Align.RIGHT);
            g.text(fighters[1].name + " · YELLOW", viewW - 20, 34, 0xFFFFE52E);
            g.text("ATTACK LEFT GOAL", viewW - 20, 58, 0xAAFFFFFF);
            g.align(Paint.Align.LEFT);
        } else {
            drawHealth(g, 20, 22, barW, fighters[0], false);
            drawHealth(g, viewW - 20 - barW, 22, barW, fighters[1], true);
        }

        g.size(15);
        g.align(Paint.Align.CENTER);
        String mid = isFootball() ? "FOOTBALL · FIRST TO 3" : mode == M_SURVIVAL ? "WAVE " + wave
                : (mode == M_BOSS ? "BOSS " + (bossIndex + 1) + " / " + BOSS_NAMES.length
                : "ROUND " + roundNumber);
        g.textCenter(mid, viewW / 2, 40, 0xCCFFFFFF);
        if (mode == M_SURVIVAL) {
            g.textCenter("TAKEDOWNS " + (wave - 1), viewW / 2, 62, 0xFFFFE38A);
        } else if (mode == M_BOSS) {
            g.textCenter("DEFEATED " + bossesDefeated, viewW / 2, 62, 0xFFFFE38A);
        } else {
            g.textCenter(roundWinsA + " - " + roundWinsB, viewW / 2, 62, 0xFFFFE38A);
        }
        g.align(Paint.Align.LEFT);
        c.restore();
    }

    private void drawHealth(Painter g, float x, float y, float w, Fighter f, boolean right) {
        g.rrect(x, y, w, 16, 8, 0x66000000);
        float pct = Math.max(0, f.health / f.maxHealth);
        float fw = (w - 4) * pct;
        int col = pct > 0.55f ? 0xFF6BE88A : (pct > 0.25f ? 0xFFFFC46B : 0xFFFF5D5D);
        if (right) {
            g.rrect(x + 2 + (w - 4 - fw), y + 2, fw, 12, 6, col);
        } else {
            g.rrect(x + 2, y + 2, fw, 12, 6, col);
        }
        if (profile.energyShield) {
            float sp = Math.max(0, f.shield / f.maxShield);
            float sw = (w - 4) * sp;
            int sc = 0xAA6FE8FF;
            if (right) {
                g.rrect(x + 2 + (w - 4 - sw), y + 19, sw, 5, 2.5f, sc);
            } else {
                g.rrect(x + 2, y + 19, sw, 5, 2.5f, sc);
            }
        }
        g.size(13);
        if (right) {
            g.align(Paint.Align.RIGHT);
            g.text(f.name + "  ·  " + Weapons.name(f.weapon)
                    + (f.ammo >= 0 ? " " + f.ammo : ""), x + w, y + 42, 0xCCFFFFFF);
        } else {
            g.text(f.name + "  ·  " + Weapons.name(f.weapon)
                    + (f.ammo >= 0 ? " " + f.ammo : ""), x, y + 42, 0xCCFFFFFF);
        }
        g.align(Paint.Align.LEFT);
    }

    // ------------------------------------------------------------- menu draw

    private void drawMenuBackground(Painter g, int which) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.gradient(0, 0, viewW, viewH, 0xFF0A0E18, 0xFF1C2740);
        // soft vignette blobs
        for (int i = 0; i < 4; i++) {
            float bx = (float) (Math.sin(time * 0.25f + i * 1.7) * 0.5 + 0.5) * viewW;
            float by = (float) (Math.cos(time * 0.2f + i * 2.1) * 0.5 + 0.5) * viewH;
            g.circle(bx, by, 150 + i * 40, Painter.withAlpha(i % 2 == 0 ? 0xFF2A4A8A : 0xFF6A2A5A, 26));
        }
        for (int i = 0; i < 26; i++) {
            float px = (i * 137.5f + time * (12 + i % 7)) % (viewW + 80) - 40;
            float py = (i * 71.3f) % viewH;
            g.circle(px, py, 1.6f, 0x33FFFFFF);
        }
        c.restore();
        g.reset();
    }

    private void drawTitle(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.reset();

        // sparring silhouettes
        float t = time * 2.2f;
        float px1 = viewW * 0.26f + (float) Math.sin(t) * 40f;
        float px2 = viewW * 0.40f + (float) Math.cos(t * 1.1f) * 40f;
        silhouettes(g, px1, viewH - 120, 1, t, PLAYER_COLOR);
        silhouettes(g, px2, viewH - 120, -1, t + 1.7f, CPU_COLOR);

        g.size(Math.min(56f, viewW * 0.06f));
        g.textShadow("STICK", 46, 108, 0xFFFFFFFF, 3);
        g.textShadow("DUELIST", 46, 108 + Math.min(56f, viewW * 0.06f) + 4f, 0xFFFFC46B, 3);
        g.size(13);
        g.text("RAGDOLL ARENA  ·  v0.3.3", 48, 108 + Math.min(56f, viewW * 0.06f) * 2f + 12f, 0x88FFFFFF);

        // coins pill
        float pillW = 128;
        g.rrect(viewW - pillW - 18, 16, pillW, 34, 17, 0x33FFFFFF);
        g.circle(viewW - pillW + 2, 33, 10, 0xFFFFC46B);
        g.size(16);
        g.text(String.valueOf(profile.coins), viewW - pillW + 20, 39, 0xFFFFFFFF);

        float by = 128;
        float bx = Math.max(viewW * 0.52f, 40);
        float bw = Math.min(320, viewW - bx - 30);
        bw = Math.min(300, Math.max(220, bw));
        bx = viewW - bw - 30;

        float sportW = Math.min(290f, bx - 76f);
        btn(g, 46, 290, sportW, 48, "FOOTBALL · VS CPU", A_FOOTBALL_CPU, 0xFF9BE86B);
        btn(g, 46, 346, sportW, 48, "FOOTBALL · 2 PLAYERS", A_FOOTBALL_LOCAL, 0xFF51E5FF);

        btn(g, bx, by, bw, 52, "QUICK FIGHT", A_QUICK, 0xFFFF6B8A);
        btn(g, bx, by + 60, bw, 52, "LOCAL DUEL  ·  2P", A_LOCAL, 0xFF4D9BFF);
        btn(g, bx, by + 120, bw, 52, "SURVIVAL", A_SURVIVAL, 0xFF9BE86B);
        btn(g, bx, by + 180, bw, 52, "BOSS TOURNAMENT", A_BOSS, 0xFFFF6B8A);
        btn(g, bx, by + 240, bw, 44, "ARSENAL", A_ARSENAL, 0xFF7D8BFF);
        btn(g, bx, by + 292, bw, 44, "CUSTOMIZE", A_STYLE, 0xFFFFC46B);
        btn(g, bx, by + 344, bw, 44, "SETTINGS", A_SETTINGS, 0xFF8A93A8);
        btn(g, bx - 78, by + 344, 68, 44, "?", A_HOW, 0xFF8A93A8);

        g.size(11);
        g.text("Duels, survival and football · offline", 48, viewH - 24, 0x66FFFFFF);
        c.restore();
    }

    private void silhouettes(Painter g, float x, float y, int face, float t, int color) {
        int fill = Painter.withAlpha(color, 112);
        int edge = 0x6610141D;
        g.c.save();
        g.c.translate(x, y);
        float lean = (float) Math.sin(t) * 4f;
        g.stroke(0, -34, 0, 10, 7f, edge);
        g.stroke(0, -34, 0, 10, 4.5f, fill);
        g.circle(0, -46, 13.5f, edge);
        g.circle(0, -46, 11f, fill);
        g.stroke(0, -24, lean - 14, 0, 5.5f, edge);
        g.stroke(0, -24, lean - 14, 0, 3f, fill);
        g.stroke(0, -24, face * 22, -10, 5.5f, edge);
        g.stroke(0, -24, face * 22, -10, 3f, fill);
        g.stroke(0, 10, -16, 40, 6f, edge);
        g.stroke(0, 10, -16, 40, 3.5f, fill);
        g.stroke(0, 10, 16, 40, 6f, edge);
        g.stroke(0, 10, 16, 40, 3.5f, fill);
        g.c.restore();
        g.reset();
    }

    private void btn(Painter g, float x, float y, float w, float h, String label, int action, int tint) {
        g.rrect(x, y, w, h, 12, Painter.withAlpha(tint, 60));
        g.rrectStroke(x, y, w, h, 12, 2f, Painter.withAlpha(tint, 180));
        g.size(Math.min(22f, h * 0.42f));
        g.textShadowCenter(label, x + w / 2, y + h / 2 + 7, 0xFFFFFFFF, 1.5f);
        Btn b = new Btn();
        b.r.set(x, y, x + w, y + h);
        b.action = action;
        uiBtns.add(b);
    }

    private void header(Painter g, String title) {
        g.size(34);
        g.textShadow(title, 40, 68, 0xFFFFFFFF, 2.5f);
        float bw = 96;
        btn(g, viewW - bw - 34, 30, bw, 44, "BACK", A_BACK, 0xFF8A93A8);
    }

    private void drawMaps(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        String mapTitle = pendingMode == M_LOCAL ? "LOCAL DUEL"
                : (pendingMode == M_SURVIVAL ? "SURVIVAL"
                : (pendingMode == M_BOSS ? "BOSS TOURNAMENT" : "QUICK FIGHT"));
        header(g, mapTitle);
        g.size(14);
        g.text("Choose an arena", 42, 96, 0x99FFFFFF);

        // 4 columns: a 3-wide grid only fits 9 cards, which silently hid the
        // 10th arena (Chaos Pit) on every screen size
        int cols = 4;
        float cw = (viewW - 80 - (cols - 1) * 14) / cols;
        float ch = 88;
        float startX = 40;
        float startY = 118;
        for (int i = 0; i < Maps.ALL.length; i++) {
            int col = i % cols;
            int row = i / cols;
            float x = startX + col * (cw + 14);
            float y = startY + row * (ch + 14);
            if (y + ch > viewH - 10) {
                break;
            }
            Maps.Arena a = Maps.ALL[i];
            boolean sel = i == arenaIndex;
            g.rrect(x, y, cw, ch, 12, 0x55000000);
            // mini preview
            g.gradient(x + 8, y + 8, cw - 16, ch - 34, a.skyTop, a.skyBottom);
            for (int k = 0; k < a.platforms.size() && k < 6; k++) {
                RectF r = a.platforms.get(k).r;
                float px = x + 8 + (r.left / a.bounds.width()) * (cw - 16);
                float pw = Math.max(8, (r.width() / a.bounds.width()) * (cw - 16));
                float py = y + 8 + (r.top / a.bounds.height()) * (ch - 34);
                g.rect(px, py, Math.min(pw, cw - 16 - (px - x - 8)), 5, a.accent);
            }
            g.rrectStroke(x, y, cw, ch, 12, sel ? 3f : 1.5f,
                    sel ? 0xFFFFC46B : 0x33FFFFFF);
            g.size(15);
            g.textCenter(a.name, x + cw / 2, y + ch - 10, 0xFFFFFFFF);
            addBtn(x, y, cw, ch, A_MAP_BASE + i);
        }
        c.restore();
    }

    private void drawArsenal(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, "ARSENAL");
        g.size(14);
        g.text("Tap to equip  ·  locked weapons cost coins  ·  starter: "
                + Weapons.name(profile.starter), 40, 96, 0x99FFFFFF);

        int cols = 4;
        float cw = (viewW - 80 - (cols - 1) * 14) / cols;
        float ch = 104;
        float startY = 114;
        for (int i = 0; i < Weapons.COUNT; i++) {
            int col = i % cols;
            int row = i / cols;
            float x = 40 + col * (cw + 14);
            float y = startY + row * (ch + 12);
            if (y + ch > viewH - 8) {
                break;
            }
            Weapons.Def d = Weapons.DEFS[i];
            boolean owned = profile.unlocked(i);
            boolean equipped = profile.starter == i;
            boolean confirming = !owned && confirmBuy == i;
            g.rrect(x, y, cw, ch, 12, equipped ? 0x44FFC46B : 0x44000000);
            g.rrectStroke(x, y, cw, ch, 12, equipped || confirming ? 2.5f : 1.2f,
                    equipped ? 0xFFFFC46B : (confirming ? 0xFFFF6B8A : 0x33FFFFFF));
            g.circle(x + 26, y + 30, 15, Painter.withAlpha(d.color, owned ? 220 : 60));
            g.size(15);
            g.text(d.name, x + 48, y + 26, owned ? 0xFFFFFFFF : 0x77FFFFFF);
            g.size(10);
            g.text(d.blurb, x + 10, y + 56, 0x88FFFFFF);
            if (confirming) {
                g.size(13);
                g.text("TAP AGAIN · " + d.cost, x + 10, y + 84, 0xFFFF6B8A);
            } else if (!owned) {
                g.size(13);
                g.text("LOCKED · " + d.cost, x + 10, y + 84, 0xFFFFC46B);
            } else if (equipped) {
                g.size(12);
                g.text("EQUIPPED", x + 10, y + 84, 0xFFFFC46B);
            } else {
                g.size(12);
                g.text("OWNED", x + 10, y + 84, 0x889BE86B);
            }
            addBtn(x, y, cw, ch, A_WEAPON_BASE + i);
        }
        c.restore();
    }

    private static final int[] SKINS = {
            PLAYER_COLOR, CPU_COLOR, 0xFFF2F2F2, 0xFF2A2A32,
            0xFFFF6B8A, 0xFF4D9BFF, 0xFFD9A0FF, 0xFF51E5FF,
    };

    private static final String[] DIFF_NAMES = {"EASY", "NORMAL", "HARD", "INSANE"};

    private void drawStyle(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, "CUSTOMIZE");

        g.size(16);
        g.text("BODY COLOR", 44, 116, 0xCCFFFFFF);
        for (int i = 0; i < SKINS.length; i++) {
            float x = 60 + i * 74;
            float y = 150;
            g.circle(x, y, 26, SKINS[i]);
            g.line.setColor(profile.skinColor == SKINS[i] ? 0xFFFFC46B : 0x33FFFFFF);
            g.line.setStrokeWidth(profile.skinColor == SKINS[i] ? 4 : 2);
            g.c.drawCircle(x, y, 30, g.line);
            addBtn(x - 32, y - 32, 64, 64, A_COLOR_BASE + i);
            g.reset();
        }

        g.size(16);
        g.text("HAIR", 44, 240, 0xCCFFFFFF);
        String[] hairs = {"NONE", "SPIKY", "BAND", "CAP"};
        for (int i = 0; i < 4; i++) {
            float x = 44 + i * 130;
            float y = 262;
            btn(g, x, y, 116, 44, hairs[i], A_HAIR_BASE + i,
                    profile.hairStyle == i ? 0xFFFFC46B : 0xFF8A93A8);
        }

        // preview (cached: this screen used to allocate a Fighter every frame)
        Fighter p = previewFighter;
        p.color = profile.skinColor;
        p.hair = profile.hairStyle;
        p.weapon = profile.starter;
        p.spawnT = 0;
        p.x = viewW * 0.5f;
        p.y = viewH - 96;
        p.onGround = true;
        g.c.save();
        g.c.translate(p.x, p.y);
        g.c.scale(1.75f, 1.75f);
        g.c.translate(-p.x, -p.y);
        p.draw(g);
        g.c.restore();
        g.size(12);
        g.textCenter("PREVIEW", p.x, viewH - 14, 0x66FFFFFF);
        c.restore();
    }

    private void drawSettings(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, "SETTINGS");

        // compact rows: with 62-unit rows the reset row and the footer collided
        float y = 110;
        float rowH = 54;
        float x = 44;
        float wRow = Math.min(560, viewW - 88);
        toggleRow(g, x, y, wRow, rowH, "Gravity", profile.gravity, A_GRAVITY);
        toggleRow(g, x, y + rowH, wRow, rowH, "Instant KO on hazards", profile.instantKO, A_INSTANT);
        toggleRow(g, x, y + rowH * 2, wRow, rowH, "Energy shield", profile.energyShield, A_SHIELD);
        toggleRow(g, x, y + rowH * 3, wRow, rowH, "Sound", profile.sound, A_SOUND);
        toggleRow(g, x, y + rowH * 4, wRow, rowH, "Voice announcer", profile.voice, A_VOICE);

        float y2 = y + rowH * 5 + 10;
        g.size(15);
        g.text("CPU DIFFICULTY", x + 16, y2 + 26, 0xCCFFFFFF);
        btn(g, x + 180, y2 + 2, 190, 40, DIFF_NAMES[profile.difficulty], A_DIFF, 0xFF7D8BFF);

        float y3 = y2 + 56;
        g.size(12);
        g.text(confirmReset ? "Tap again to wipe everything" : "Wipes coins, unlocks and records",
                x + 16, y3 + 28, confirmReset ? 0xFFFF6B8A : 0x88FFFFFF);
        btn(g, x + 250, y3 + 4, 190, 40, confirmReset ? "TAP AGAIN!" : "RESET PROGRESS",
                A_RESET, 0xFFFF6B8A);

        g.size(11);
        g.text("Wins " + profile.wins + "  ·  Losses " + profile.losses
                + "  ·  Best streak " + profile.bestStreak, x, viewH - 32, 0x88FFFFFF);
        g.text("Stick Duelist v0.3.3 · built in Arena Agent Mode", x, viewH - 14, 0x66FFFFFF);
        c.restore();
    }

    private void toggleRow(Painter g, float x, float y, float w, float h, String label,
                           boolean on, int action) {
        g.rrect(x, y, w, h - 10, 10, 0x33000000);
        g.size(16);
        g.text(label, x + 16, y + h / 2 + 1, 0xDDEEEEFF);
        float sw = 66, sh = 30;
        float sx = x + w - sw - 16;
        float sy = y + (h - 10 - sh) / 2;
        g.rrect(sx, sy, sw, sh, sh / 2, on ? 0xFF4D9BFF : 0x55FFFFFF);
        g.circle(sx + (on ? sw - sh / 2 : sh / 2), sy + sh / 2, sh / 2 - 3, 0xFFFFFFFF);
        addBtn(x, y, w, h, action);
    }

    private void drawHow(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        header(g, "HOW TO PLAY");
        String[] lines = {
                "1P — hold the left stick; 2P — each player uses their own stick",
                "Hold a direction to move, aim your weapon and basic-attack",
                "Push up to jump · push down to shield (or crouch when shields are off)",
                "RELEASE a held stick while its ring is gold to use your weapon skill",
                "Every weapon has a different skill; its ring refills after each use",
                "Weapons drop from the sky. Walk over one to grab it",
                "Break an opponent's poise and they go DOWN, then recover on their own",
                "Water and lava end you instantly (unless Instant KO is off)",
                "First to 2 rounds wins · a double K.O. replays without a point",
                "Boss Tournament: defeat five escalating opponents and recover between fights",
                "Football: first to 3 goals · contact kicks · release near the ball for a power kick",
        };
        g.size(14);
        for (int i = 0; i < lines.length; i++) {
            g.text(lines[i], 46, 118 + i * 26, i % 2 == 0 ? 0xDDEEEEFF : 0xBBDDEEFF);
        }
        g.size(13);
        g.text("Tips: Hammer releases a shockwave. Spear has the longest reach. "
                + "Sword dashes in any direction.", 46, 118 + lines.length * 26 + 14, 0x99FFC46B);
        c.restore();
    }

    private void drawPause(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.rect(0, 0, viewW, viewH, 0xCC06080F);
        uiBtns.clear();
        float pw = 340;
        float px = (viewW - pw) / 2;
        float py = 90;
        g.rrect(px, py, pw, 360, 18, 0xFF141C2C);
        g.rrectStroke(px, py, pw, 360, 18, 2, 0x33FFFFFF);
        g.size(26);
        g.textCenter("PAUSED", viewW / 2, py + 52, 0xFFFFFFFF);
        float bx = px + 30;
        float bw = pw - 60;
        btn(g, bx, py + 82, bw, 48, "RESUME", A_RESUME, 0xFF9BE86B);
        btn(g, bx, py + 138, bw, 48, "RESTART", A_RESTART, 0xFF4D9BFF);
        btn(g, bx, py + 194, bw, 48, isFootball() ? "RESET PITCH" : "CHANGE ARENA", A_MAPS_FROM_PAUSE, 0xFF7D8BFF);
        btn(g, bx, py + 250, bw, 48, "QUIT TO MENU", A_QUIT, 0xFFFF6B8A);
        c.restore();
    }

    private void drawResult(Painter g) {
        Canvas c = g.c;
        c.save();
        c.scale(scale, scale);
        g.rect(0, 0, viewW, viewH, 0xB306080F);
        uiBtns.clear();
        float pw = 460;
        float ph = 300;
        float px = (viewW - pw) / 2;
        float py = (viewH - ph) / 2;
        g.rrect(px, py, pw, ph, 20, 0xFF141C2C);
        g.rrectStroke(px, py, pw, ph, 20, 2.5f, playerWon ? 0xFFFFC46B : 0xFF8A93A8);

        g.size(46);
        g.textShadowCenter(resultTitle, viewW / 2, py + 72, playerWon ? 0xFFFFD166 : 0xFFB8C0D0, 3);
        g.size(15);
        g.textCenter(resultSub, viewW / 2, py + 108, 0xCCFFFFFF);
        g.size(20);
        g.textCenter("+" + resultCoins + " coins", viewW / 2, py + 146, 0xFFFFC46B);
        g.size(12);
        g.textCenter("Total: " + profile.coins, viewW / 2, py + 166, 0x88FFFFFF);

        float bw = (pw - 80) / 2;
        btn(g, px + 26, py + ph - 86, bw, 52, "REMATCH", A_REMATCH, 0xFF9BE86B);
        btn(g, px + 46 + bw, py + ph - 86, bw, 52, "MENU", A_MENU, 0xFF4D9BFF);
        c.restore();
    }

    private void addBtn(float x, float y, float w, float h, int action) {
        Btn b = new Btn();
        b.r.set(x, y, x + w, y + h);
        b.action = action;
        uiBtns.add(b);
    }

    /** Drops every held pointer and every queued gameplay action. */
    private void releaseControls() {
        stickA.release();
        stickB.release();
        for (int i = 0; i < 2; i++) {
            Fighter f = fighters[i];
            f.moveX = 0f;
            f.jumpQueued = false;
            f.attackQueued = false;
            f.shieldHeld = false;
        }
    }

    private void announce(String text) {
        if (profile.voice) {
            host.announce(text);
        }
    }

    private void sfx(int id) {
        if (profile.sound) {
            host.sfx(id);
        }
    }

    // ------------------------------------------------------------ test hooks

    void debugStartMatch(int m, int map) {
        pendingMode = m;
        resetMatch(m, map, true);
    }

    Fighter debugFighter(int i) {
        return fighters[i];
    }

    int debugScreen() {
        return screen;
    }

    int debugPhase() {
        return phase;
    }

    void debugStartFight() {
        phase = PH_FIGHT;
        phaseT = 30f;
        countAnnounced = true;
    }

    boolean debugDown(int i) {
        return fighters[i].down;
    }

    float debugPoise(int i) {
        return fighters[i].poise;
    }

    /** Fighters whose physics body is in charge (knocked down or dead). */
    int debugRagdollCount() {
        int n = 0;
        for (int i = 0; i < 2; i++) {
            if (fighters[i].ragdolled()) {
                n++;
            }
        }
        return n;
    }

    float debugLimp(int i) {
        return fighters[i].limp;
    }

    float debugPoseError(int i) {
        return fighters[i].poseError();
    }

    boolean debugUseAbility(int i, float dx, float dy) {
        return useAbility(fighters[i], dx, dy);
    }

    void debugSeed(long seed) { rnd.setSeed(seed); cpu.seed(seed); }

    float[] debugCamera() { return new float[] { camX, camY, zoom }; }

    void debugStepRagdolls(float dt) { updateRagdolls(dt); }

    Football debugFootball() { return football; }

    boolean debugPaused() { return paused; }

    void debugAddShot(Projectile shot) { shots.add(shot); }

    void debugUpdateShots(float dt) { updateShots(dt); }

    void debugSetArena(Maps.Arena value) { arena = value; }

    int debugShotCount() {
        return shots.size();
    }

    void debugSetRoundWins(int a, int b) {
        roundWinsA = a;
        roundWinsB = b;
    }

    int debugRoundWinsA() {
        return roundWinsA;
    }

    int debugRoundWinsB() {
        return roundWinsB;
    }

    int debugWave() {
        return wave;
    }

    int debugBossesDefeated() {
        return bossesDefeated;
    }

    int debugBossIndex() {
        return bossIndex;
    }

    void debugSuppressPickups() {
        spawnTimer = Float.MAX_VALUE;
    }

    // --------------------------------------------------------------- helpers

    private Path triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        Path p = new Path();
        p.moveTo(x1, y1);
        p.lineTo(x2, y2);
        p.lineTo(x3, y3);
        p.close();
        return p;
    }

    private Path diamond(float x, float y, float r) {
        Path p = new Path();
        p.moveTo(x, y - r);
        p.lineTo(x + r, y);
        p.lineTo(x, y + r);
        p.lineTo(x - r, y);
        p.close();
        return p;
    }

    // ------------------------------------------------------------- inner types

    private static final class Btn {
        final RectF r = new RectF();
        int action;
    }

    private static final class Pickup {
        int weapon;
        float x, y, vy;
        float t;
        boolean onGround;
    }


    private static final class Stick {
        float baseX, baseY, radius = 84f;
        /** Resting position, restored when the floating stick is released. */
        float homeX, homeY;
        int pointer = -1;
        float dx, dy;
        float startX, startY;
        boolean jumpReady = true;

        void setBase(float x, float y, float r) {
            homeX = x;
            homeY = y;
            baseX = x;
            baseY = y;
            radius = r;
        }

        boolean grab(int id, float px, float py) {
            if (pointer != -1) {
                return false;
            }
            float ddx = px - baseX;
            float ddy = py - baseY;
            if (ddx * ddx + ddy * ddy < (radius + 34) * (radius + 34)) {
                pointer = id;
                startX = px;
                startY = py;
                move(id, px, py);
                return true;
            }
            return false;
        }

        /**
         * Single-player stick: the whole left half of the screen is the stick,
         * and it floats to wherever the thumb lands. It used to keep its base at
         * the drawn position, so touching anywhere far from it produced instant
         * full-speed movement in that direction.
         */
        boolean grabAnywhere(int id, float px, float py) {
            if (pointer != -1) {
                return false;
            }
            pointer = id;
            startX = px;
            startY = py;
            baseX = px;
            baseY = py;
            move(id, px, py);
            return true;
        }

        void move(int id, float px, float py) {
            if (pointer != id) {
                return;
            }
            float ddx = px - baseX;
            float ddy = py - baseY;
            float len = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (len < 0.001f) {
                dx = 0;
                dy = 0;
                return;
            }
            float max = radius;
            float clamped = Math.min(len, max);
            dx = (ddx / len) * (clamped / max);
            dy = (ddy / len) * (clamped / max);
        }

        void release(int id) {
            if (pointer == id) {
                pointer = -1;
                dx = 0;
                dy = 0;
                jumpReady = true;
                baseX = homeX;
                baseY = homeY;
            }
        }

        boolean active() {
            return pointer != -1;
        }

        void release() {
            pointer = -1;
            dx = 0f;
            dy = 0f;
            jumpReady = true;
            baseX = homeX;
            baseY = homeY;
        }
    }
}
