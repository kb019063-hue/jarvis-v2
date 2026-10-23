package com.arena.duelist;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.Locale;

public final class MainActivity extends Activity {

    private static MainActivity instance;

    private FrameLayout root;
    private GameView view;
    private static TextToSpeech tts;
    private static boolean ttsReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFF0B0F17);
            getWindow().setNavigationBarColor(0xFF0B0F17);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0F17);
        view = new GameView(this);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        hideSystemBars();

        initAudio();
        initTts();
    }

    private void hideSystemBars() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
    }

    private void initAudio() {
        try {
            SoundFx.init(this);
        } catch (Throwable ignored) {
        }
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        tts.setLanguage(Locale.US);
                        tts.setSpeechRate(1.05f);
                        ttsReady = true;
                    }
                }
            });
        } catch (Throwable t) {
            ttsReady = false;
        }
    }

    static void speak(String text) {
        if (!ttsReady || tts == null) {
            return;
        }
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "duelist");
        } catch (Throwable ignored) {
        }
    }

    static void buzz(View v, int ms) {
        if (v == null || ms <= 0) {
            return;
        }
        try {
            Vibrator vibrator = (Vibrator) v.getContext()
                    .getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(
                        ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // Minimum supported SDK is 24; this deprecated overload is the
                // compatible duration-based API for Android 7.x.
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    static void keepBright(View v, boolean on) {
        try {
            v.setKeepScreenOn(on);
        } catch (Throwable ignored) {
        }
    }

    static void playSfx(int id) {
        SoundFx.play(id);
    }

    @Override
    public void onBackPressed() {
        if (view != null && view.game().backPressed()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (view != null) {
            view.stopLoop();
        }
        if (tts != null) {
            try {
                tts.stop();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (view != null) {
            view.reacquire();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Throwable ignored) {
            }
        }
        tts = null;
        ttsReady = false;
        try {
            SoundFx.release();
        } catch (Throwable ignored) {
        }
        instance = null;
    }
}
