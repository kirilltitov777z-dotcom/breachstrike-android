package com.breachstrike.game.render;

import android.content.Context;
import android.opengl.GLSurfaceView;

import com.breachstrike.game.core.Assets;
import com.breachstrike.game.core.Save;
import com.breachstrike.game.game.GameConfig;
import com.breachstrike.game.game.GameState;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class GameView extends GLSurfaceView implements GLSurfaceView.Renderer {
    public interface Listener {
        void onMatchEnd(GameState gs);
        void onPausePressed(GameView view);
    }

    private final Assets assets;
    private final Save save;
    private final GameState state;
    private final GameRenderer renderer;
    public final HudView hud;
    private final Listener listener;
    private long last;
    private boolean ended;
    private volatile boolean paused;
    private boolean failed;
    private final float[] fpsAvg = {60f};

    public GameView(Context c, GameConfig cfg, Save save, Listener l) {
        super(c);
        this.save = save;
        this.listener = l;
        assets = new Assets(c);
        assets.setVolume(save.volume());
        state = new GameState(cfg, assets, save, new GameState.Listener() {
            @Override
            public void onMatchEnd(GameState gs) {
                // handled from the GL thread below
            }
        });
        renderer = new GameRenderer(assets, state);
        hud = new HudView(c, this);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        last = System.nanoTime();
    }

    public GameState getState() { return state; }
    public Assets getAssets() { return assets; }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            renderer.init();
        } catch (Throwable t) {
            com.breachstrike.game.CrashLog.write(getContext(), "GL init", t);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);
        renderer.resize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (failed) return;
        try {
            frame();
        } catch (Throwable t) {
            failed = true;
            com.breachstrike.game.CrashLog.write(getContext(), "GL frame", t);
        }
    }

    private void frame() {
        long now = System.nanoTime();
        float dt = (now - last) / 1e9f;
        last = now;
        if (dt > 0.08f) dt = 0.08f;
        fpsAvg[0] = fpsAvg[0] * 0.92f + (1f / Math.max(0.001f, dt)) * 0.08f;
        state.fps = fpsAvg[0];
        if (!paused) {
            state.update(dt);
        } else {
            state.look(0, 0);
        }
        renderer.draw(dt);
        hud.postInvalidate();
        if (state.matchOver && !ended) {
            ended = true;
            post(new Runnable() {
                @Override
                public void run() {
                    if (listener != null) listener.onMatchEnd(state);
                }
            });
        }
    }

    public void pauseGame() {
        paused = true;
    }

    public void resumeGame() {
        paused = false;
        last = System.nanoTime();
    }

    public void dispose() {
        try {
            assets.release();
        } catch (Throwable ignored) {}
    }

    public void pausePressed() {
        if (listener != null) listener.onPausePressed(this);
    }
}
