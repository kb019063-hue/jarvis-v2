package com.arena.duelist;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Game surface. Owns the world + render loop. Input is forwarded as simple
 * pointer events; the Game logic decides what they mean per screen.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback {

    private static final long FRAME_MS = 16;
    private static final float FIXED_DT = 1f / 60f;
    private static final int MAX_STEPS = 5;

    private GameThread thread;
    private final Game game;
    private volatile boolean running;

    public GameView(Context context) {
        super(context);
        setFocusable(true);
        setKeepScreenOn(true);
        getHolder().addCallback(this);
        game = new Game(context, new Game.Host() {
            @Override
            public void announce(String text) {
                MainActivity.speak(text);
            }

            @Override
            public void setBright(boolean on) {
                MainActivity.keepBright(GameView.this, on);
            }

            @Override
            public void vibrate(int ms) {
                MainActivity.buzz(GameView.this, ms);
            }

            @Override
            public void sfx(int id) {
                MainActivity.playSfx(id);
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (running) {
            return;
        }
        running = true;
        thread = new GameThread(getHolder());
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        game.resize(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopLoop();
    }

    public void stopLoop() {
        running = false;
        GameThread t = thread;
        thread = null;
        if (t != null) {
            t.quit();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                game.pointerDown(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                int n = e.getPointerCount();
                for (int i = 0; i < n; i++) {
                    game.pointerMove(e.getPointerId(i), e.getX(i), e.getY(i));
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                game.pointerUp(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                game.pointerCancel();
                break;
            }
            default:
                break;
        }
        return true;
    }

    /** Fixed-ish timestep render/draw loop. */
    private final class GameThread extends Thread {

        private final SurfaceHolder holder;
        private volatile boolean alive = true;

        GameThread(SurfaceHolder holder) {
            super("duelist-loop");
            this.holder = holder;
        }

        void quit() {
            alive = false;
        }

        @Override
        public void run() {
            long last = System.nanoTime();
            float accumulator = 0f;
            while (alive && running) {
                long now = System.nanoTime();
                float elapsed = (now - last) / 1_000_000_000f;
                last = now;
                accumulator += Math.min(elapsed, 0.10f);
                int steps = 0;
                while (accumulator >= FIXED_DT && steps < MAX_STEPS) {
                    game.tick(FIXED_DT);
                    accumulator -= FIXED_DT;
                    steps++;
                }
                if (steps == MAX_STEPS) {
                    // Do not retain a spiral-of-death backlog after a long hitch.
                    accumulator = 0f;
                }

                Canvas c = null;
                try {
                    c = holder.lockCanvas();
                    if (c != null) {
                        synchronized (holder) {
                            game.draw(c);
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (c != null) {
                        try {
                            holder.unlockCanvasAndPost(c);
                        } catch (Throwable ignored) {
                        }
                    }
                }

                long spent = (System.nanoTime() - now) / 1_000_000L;
                long sleep = FRAME_MS - spent;
                if (sleep > 0) {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException ie) {
                        return;
                    }
                }
            }
        }
    }

    /** Convenience accessor used by MainActivity for back-press handling. */
    public Game game() {
        return game;
    }

    /** Called on resume: restart the loop if the surface is still alive. */
    public void reacquire() {
        if (!running && getHolder().getSurface().isValid()) {
            surfaceCreated(getHolder());
        }
    }
}
