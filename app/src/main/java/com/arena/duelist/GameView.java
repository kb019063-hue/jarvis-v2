package com.arena.duelist;

import android.content.Context;
import android.graphics.Canvas;
import android.net.wifi.WifiManager;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Game surface. Owns the world + render loop. Input is forwarded as simple
 * pointer events; the Game logic decides what they mean per screen.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback {

    private static final long FRAME_MS = 16;

    private GameThread thread;
    private final Game game;
    private volatile boolean running;
    private WifiManager.WifiLock wifiLock;

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
        releaseWifi();
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
                int n = e.getPointerCount();
                for (int i = 0; i < n; i++) {
                    game.pointerUp(e.getPointerId(i), e.getX(i), e.getY(i));
                }
                break;
            }
            default:
                break;
        }
        return true;
    }

    public void acquireWifi() {
        try {
            WifiManager m = (WifiManager) getContext().getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (m != null && wifiLock == null) {
                wifiLock = m.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "duelist:wifi");
                wifiLock.acquire();
            }
        } catch (Throwable ignored) {
        }
    }

    public void releaseWifi() {
        try {
            if (wifiLock != null) {
                wifiLock.release();
            }
        } catch (Throwable ignored) {
        }
        wifiLock = null;
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
            while (alive && running) {
                long now = System.nanoTime();
                float dt = (now - last) / 1_000_000_000f;
                last = now;
                if (dt > 0.05f) {
                    dt = 0.05f;
                }

                game.tick(dt);

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
