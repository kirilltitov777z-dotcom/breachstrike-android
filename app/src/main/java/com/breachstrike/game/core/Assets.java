package com.breachstrike.game.core;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public final class Assets {
    private final AssetManager am;
    private final SoundPool pool;
    private final HashMap<String, Integer> textures = new HashMap<>();
    private final HashMap<String, Integer> sounds = new HashMap<>();
    private final HashMap<Integer, Integer> soundIds = new HashMap<>();
    private float volume = 0.8f;
    private int fallbackTex = -1;

    public Assets(Context ctx) {
        am = ctx.getAssets();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(16).setAudioAttributes(attrs).build();
        loadSounds();
    }

    private void loadSounds() {
        try {
            String[] files = am.list("sfx");
            if (files == null) return;
            for (String f : files) {
                if (!f.endsWith(".wav")) continue;
                String key = f.substring(0, f.length() - 4);
                try {
                    AssetFileDescriptor fd = am.openFd("sfx/" + f);
                    int id = pool.load(fd, 1);
                    soundIds.put(id, 0);
                    sounds.put(key, id);
                    fd.close();
                } catch (IOException e) {
                    Log.w("BreachAssets", "sound " + f + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.w("BreachAssets", "sfx list failed: " + e.getMessage());
        }
    }

    public void setVolume(float v) { volume = v; }

    public void play(String name, float vol) {
        Integer id = sounds.get(name);
        if (id == null) return;
        pool.play(id, volume * vol, volume * vol, 1, 0, 1f);
    }

    public void play(String name) { play(name, 1f); }

    public int tex(String name) {
        Integer cached = textures.get(name);
        if (cached != null) return cached;
        int id = loadTexture(name);
        textures.put(name, id);
        return id;
    }

    private int loadTexture(String name) {
        Bitmap bmp = null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            // Source PNGs are stored at 1536/1024 px so the APK ships a real HD
            // asset pack. Phones do not need that many pixels on screen: decode
            // at half size to keep GL memory around 35 MB instead of 140 MB.
            boolean small = name.equals("mat_flash") || name.equals("mat_blade");
            opts.inSampleSize = small ? 4 : 2;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            bmp = BitmapFactory.decodeStream(am.open("tex/" + name + ".png"), null, opts);
            // GLES2 only allows REPEAT + mipmaps on power-of-two textures.
            // Skins decode to 768x768 (NPOT): scale them to 512x512.
            if (bmp != null && name.startsWith("skin_")
                    && (bmp.getWidth() != 512 || bmp.getHeight() != 512)) {
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 512, 512, true);
                if (scaled != bmp) {
                    bmp.recycle();
                    bmp = scaled;
                }
            }
        } catch (Exception e) {
            Log.w("BreachAssets", "texture " + name + ": " + e.getMessage());
        }
        if (bmp == null) {
            if (fallbackTex < 0) fallbackTex = solid(0xFFFF00FF);
            return fallbackTex;
        }
        int id = upload(bmp);
        bmp.recycle();
        return id;
    }

    public int solid(int argb) {
        String key = "solid:" + argb;
        Integer c = textures.get(key);
        if (c != null) return c;
        Bitmap bmp = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(argb);
        int id = upload(bmp);
        bmp.recycle();
        textures.put(key, id);
        return id;
    }

    private int upload(Bitmap bmp) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return ids[0];
    }

    public ArrayList<String> soundNames() {
        return new ArrayList<>(sounds.keySet());
    }

    public void release() {
        try {
            pool.release();
        } catch (Throwable ignored) {}
    }
}
