package com.arena.duelist;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Tiny synthesizer: generates WAV blips at first launch and plays them through
 * SoundPool. Keeps the APK asset-free while still sounding like something.
 */
public final class SoundFx {

    public static final int HIT = 0;
    public static final int KO = 1;
    public static final int JUMP = 2;
    public static final int THROW = 3;
    public static final int PICKUP = 4;
    public static final int CLICK = 5;
    public static final int SHIELD = 6;
    public static final int COUNT = 7;

    private static SoundPool pool;
    private static final int[] ids = new int[COUNT];
    private static boolean ready;
    private static boolean enabled = true;

    public static void init(Context ctx) {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build();
            for (int i = 0; i < COUNT; i++) {
                File f = new File(ctx.getCacheDir(), "sfx" + i + ".wav");
                if (!f.exists() || f.length() < 44) {
                    writeWav(f, build(i));
                }
                ids[i] = pool.load(f.getAbsolutePath(), 1);
            }
            ready = true;
        } catch (Throwable t) {
            ready = false;
        }
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static void play(int id) {
        play(id, 1f);
    }

    public static void play(int id, float volume) {
        if (!ready || !enabled || pool == null || id < 0 || id >= COUNT) {
            return;
        }
        try {
            pool.play(ids[id], volume, volume, 1, 0, 1f);
        } catch (Throwable ignored) {
        }
    }

    public static void release() {
        ready = false;
        try {
            if (pool != null) {
                pool.release();
            }
        } catch (Throwable ignored) {
        }
        pool = null;
    }

    // ---------------------------------------------------------------- synthesis

    private static final int RATE = 22050;

    private static short[] build(int id) {
        switch (id) {
            case HIT:
                return noiseBurst(0.16f, 900f, 0.9f, true);
            case KO:
                return boom();
            case JUMP:
                return sweep(0.13f, 240f, 620f, 0.35f);
            case THROW:
                return noiseBurst(0.14f, 2400f, 0.45f, false);
            case PICKUP:
                return chime();
            case SHIELD:
                return sweep(0.2f, 900f, 420f, 0.3f);
            default:
                return sweep(0.05f, 1200f, 1200f, 0.25f);
        }
    }

    private static short[] sweep(float seconds, float f0, float f1, float amp) {
        int n = (int) (RATE * seconds);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = f0 + (f1 - f0) * t;
            phase += 2 * Math.PI * f / RATE;
            double env = Math.pow(1 - t, 1.7);
            double s = Math.signum(Math.sin(phase)) * 0.55 + Math.sin(phase) * 0.45;
            out[i] = (short) (s * env * amp * 32767);
        }
        return out;
    }

    private static short[] noiseBurst(float seconds, float toneMix, float amp, boolean thump) {
        int n = (int) (RATE * seconds);
        short[] out = new short[n];
        Random r = new Random(7);
        float lp = 0;
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            float noise = r.nextFloat() * 2 - 1;
            lp += (noise - lp) * 0.35f;
            double env = Math.exp(-t * 5.2);
            phase += 2 * Math.PI * (150 - 60 * t) / RATE;
            double body = thump ? Math.sin(phase) * 0.8 : 0;
            double s = lp * 0.85 + body;
            s = s * env * amp;
            out[i] = (short) Math.max(-1, Math.min(1, s) * 32767);
        }
        return out;
    }

    private static short[] boom() {
        int n = (int) (RATE * 0.6f);
        short[] out = new short[n];
        Random r = new Random(11);
        double phase = 0;
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double env = Math.exp(-t * 3.2);
            phase += 2 * Math.PI * (110 - 70 * t) / RATE;
            lp += ((r.nextFloat() * 2 - 1) - lp) * 0.12f;
            double s = Math.sin(phase) * 0.8 + lp * 0.5;
            out[i] = (short) Math.max(-1, Math.min(1, s * env) * 32767);
        }
        return out;
    }

    private static short[] chime() {
        int n = (int) (RATE * 0.22f);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = t < 0.5 ? 880 : 1320;
            double env = Math.exp(-t * 4.5);
            double s = Math.sin(2 * Math.PI * f * i / RATE) * env * 0.35;
            out[i] = (short) (s * 32767);
        }
        return out;
    }

    private static void writeWav(File f, short[] samples) throws IOException {
        int dataLen = samples.length * 2;
        byte[] header = new byte[44];
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        putInt(header, 4, 36 + dataLen);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        putInt(header, 16, 16);
        putShort(header, 20, 1);
        putShort(header, 22, 1);
        putInt(header, 24, RATE);
        putInt(header, 28, RATE * 2);
        putShort(header, 32, 2);
        putShort(header, 34, 16);
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        putInt(header, 40, dataLen);

        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(header);
            byte[] data = new byte[dataLen];
            for (int i = 0; i < samples.length; i++) {
                data[i * 2] = (byte) (samples[i] & 0xFF);
                data[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
            }
            out.write(data);
        } finally {
            out.close();
        }
    }

    private static void putInt(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static void putShort(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private SoundFx() {
    }
}
