package com.breachstrike.game.render;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.breachstrike.game.game.Elo;
import com.breachstrike.game.game.GameConfig;
import com.breachstrike.game.game.GameState;

public final class HudView extends View {
    private final GameView game;
    private final GameState gs;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float d = 2f;
    private int w = 1, h = 1;

    private float joyX, joyY, joyR, knobX, knobY;
    private float fireX, fireY, fireR;
    private float jumpX, jumpY, jumpR;
    private float crouchX, crouchY, crouchR;
    private float reloadX, reloadY, reloadR;
    private float switchX, switchY, switchR;
    private float inspectX, inspectY, inspectR;
    private float scopeX, scopeY, scopeR;
    private float plantX, plantY, plantR;
    private final RectF pauseRect = new RectF();

    private int joyId = -1, lookId = -1, fireId = -1, plantId = -1;
    private float lookLastX, lookLastY;
    private float joyDX, joyDY;

    public HudView(Context c, GameView game) {
        super(c);
        this.game = game;
        this.gs = game.getState();
        d = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        text.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        setClickable(true);
        setFocusable(true);
    }

    private float dp(float v) { return v * d; }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        w = width;
        h = height;
        joyR = dp(78);
        joyX = dp(105);
        joyY = h - dp(100);
        knobX = joyX;
        knobY = joyY;

        fireR = dp(54);
        fireX = w - dp(86);
        fireY = h - dp(92);

        jumpR = dp(31);
        jumpX = w - dp(196);
        jumpY = h - dp(58);

        crouchR = dp(31);
        crouchX = w - dp(88);
        crouchY = h - dp(208);

        reloadR = dp(31);
        reloadX = w - dp(190);
        reloadY = h - dp(160);

        switchR = dp(31);
        switchX = w - dp(292);
        switchY = h - dp(90);

        inspectR = dp(26);
        inspectX = w - dp(292);
        inspectY = h - dp(190);

        scopeR = dp(30);
        scopeX = w - dp(390);
        scopeY = h - dp(145);

        plantR = dp(42);
        plantX = w / 2f;
        plantY = h - dp(78);

        float ps = dp(40);
        pauseRect.set(w - ps - dp(12), dp(10), w - dp(12), dp(10) + ps);
    }

    // ----------------------- touch -----------------------
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        int id = e.getPointerId(idx);
        float x = e.getX(idx), y = e.getY(idx);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (pauseRect.contains(x, y)) {
                game.pausePressed();
                return true;
            }
            if (gs.alive) {
                if (hit(fireX, fireY, fireR, x, y) && fireId < 0) {
                    fireId = id;
                    gs.setFire(true);
                } else if (hit(plantX, plantY, plantR, x, y) && gs.playerHasBomb() && gs.inPlantZone() && plantId < 0) {
                    plantId = id;
                    gs.setPlantHeld(true);
                } else if (hit(jumpX, jumpY, jumpR, x, y)) {
                    gs.jump();
                } else if (hit(crouchX, crouchY, crouchR, x, y)) {
                    gs.toggleCrouch();
                } else if (hit(reloadX, reloadY, reloadR, x, y)) {
                    gs.requestReload();
                } else if (hit(switchX, switchY, switchR, x, y)) {
                    gs.nextWeapon();
                } else if (hit(inspectX, inspectY, inspectR, x, y)) {
                    gs.inspect();
                } else if (hit(scopeX, scopeY, scopeR, x, y) && gs.currentDef().scoped) {
                    gs.toggleScope();
                } else if (x < w * 0.42f && y > h * 0.45f && joyId < 0) {
                    joyId = id;
                    joyDX = x;
                    joyDY = y;
                    updateJoy(x, y);
                } else if (x > w * 0.4f && lookId < 0 && y < h - dp(30)) {
                    lookId = id;
                    lookLastX = x;
                    lookLastY = y;
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                int pid = e.getPointerId(i);
                float px = e.getX(i), py = e.getY(i);
                if (pid == joyId) {
                    updateJoy(px, py);
                } else if (pid == lookId) {
                    gs.look(px - lookLastX, py - lookLastY);
                    lookLastX = px;
                    lookLastY = py;
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
            if (id == joyId) {
                joyId = -1;
                joyDX = joyDY = 0;
                knobX = joyX;
                knobY = joyY;
                gs.setMove(0, 0);
            }
            if (id == lookId) lookId = -1;
            if (id == fireId) {
                fireId = -1;
                gs.setFire(false);
            }
            if (id == plantId) {
                plantId = -1;
                gs.setPlantHeld(false);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            joyId = lookId = fireId = plantId = -1;
            gs.setMove(0, 0);
            gs.setFire(false);
            gs.setPlantHeld(false);
            knobX = joyX;
            knobY = joyY;
        }
        return true;
    }

    private void updateJoy(float x, float y) {
        float dx = x - joyX;
        float dy = y - joyY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > joyR) {
            dx = dx / len * joyR;
            dy = dy / len * joyR;
        }
        knobX = joyX + dx;
        knobY = joyY + dy;
        float nx = dx / joyR;
        float ny = dy / joyR;
        gs.setMove(nx, -ny);
    }

    private boolean hit(float cx, float cy, float r, float x, float y) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy < r * r;
    }

    // ----------------------- draw -----------------------
    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (w < 10) return;

        if (gs.scoped && gs.currentDef().scoped) {
            drawScope(c);
        }

        drawHud(c);
        if (gs.alive) {
            drawControls(c);
            drawCrosshair(c);
        }
        drawDamage(c);
        drawBanners(c);
        if (gs.hitMarker > 0) drawHitmarker(c);
    }

    private void drawControls(Canvas c) {
        // joystick
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x44202636);
        c.drawCircle(joyX, joyY, joyR, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(0x887A879C);
        c.drawCircle(joyX, joyY, joyR, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66FF8A1E);
        c.drawCircle(knobX, knobY, dp(32), p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xAAFFB25E);
        c.drawCircle(knobX, knobY, dp(32), p);

        button(c, fireX, fireY, fireR, "ОГОНЬ", 0x33FF5A1E, 0xAAFF8A1E, fireId >= 0);
        button(c, jumpX, jumpY, jumpR, "ПРЫЖ", 0x33101A28, 0x667A879C, false);
        button(c, crouchX, crouchY, crouchR, gs.crouch ? "СТОЯ" : "ПРИС", gs.crouch ? 0x55FF8A1E : 0x33101A28, 0x667A879C, gs.crouch);
        button(c, reloadX, reloadY, reloadR, "ПЕРЕЗ", 0x33101A28, 0x667A879C, gs.reloading);
        button(c, switchX, switchY, switchR, "СМЕНА", 0x33101A28, 0x667A879C, false);
        button(c, inspectX, inspectY, inspectR, "ОСМОТР", 0x33101A28, 0x667A879C, gs.inspectTimer > 0);
        if (gs.currentDef().scoped) {
            button(c, scopeX, scopeY, scopeR, gs.scoped ? "ВЫЙТИ" : "ПРИЦЕЛ", gs.scoped ? 0x55FF8A1E : 0x33101A28, 0x667A879C, gs.scoped);
        }
        if (gs.playerHasBomb() && gs.inPlantZone()) {
            button(c, plantX, plantY, plantR, "ЗАЛОЖИТЬ", 0x44FF3B1E, 0xCCFF6A3B, plantId >= 0);
        }

        // pause
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66101620);
        c.drawRoundRect(pauseRect, dp(8), dp(8), p);
        text.setColor(0xFFEDEFF4);
        text.setTextSize(dp(17));
        text.setTextAlign(Paint.Align.CENTER);
        text.setStyle(Paint.Style.FILL);
        c.drawText("II", pauseRect.centerX(), pauseRect.centerY() + dp(6), text);
    }

    private void button(Canvas c, float x, float y, float r, String label, int fill, int border, boolean active) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawCircle(x, y, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(active ? 3 : 1.5f));
        p.setColor(border);
        c.drawCircle(x, y, r, p);
        text.setColor(0xFFEDEFF4);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(dp(label.length() > 6 ? 10 : 12));
        text.setStyle(Paint.Style.FILL);
        c.drawText(label, x, y + dp(4), text);
    }

    private void drawHud(Canvas c) {
        text.setStyle(Paint.Style.FILL);

        // top center
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFEDEFF4);
        text.setTextSize(dp(28));
        c.drawText(gs.timerText(), w / 2f, dp(34), text);
        text.setTextSize(dp(14));
        text.setColor(0xFFB9C2D4);
        String score;
        if (gs.cfg.mode == GameConfig.MODE_BOMB) score = "СЧЁТ  " + gs.scoreT + " : " + gs.scoreCT + "   РАУНД " + gs.round;
        else if (gs.cfg.mode == GameConfig.MODE_DM) score = "ФРАГИ " + gs.kills + " / 30";
        else score = "СЧЁТ  " + gs.scoreT + " : " + gs.scoreCT;
        c.drawText(score, w / 2f, dp(56), text);
        if (gs.cfg.mode == GameConfig.MODE_BOMB && gs.planted) {
            text.setColor(0xFFFF5A3C);
            text.setTextSize(dp(15));
            c.drawText("БОМБА " + gs.timerText(), w / 2f, dp(76), text);
        }

        // hp
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFFEDEFF4);
        text.setTextSize(dp(30));
        c.drawText(String.valueOf(gs.hp), dp(16), dp(40), text);
        text.setTextSize(dp(11));
        text.setColor(0xFF9AA4B8);
        c.drawText("HP", dp(16), dp(54), text);
        p.setColor(0x66101620);
        c.drawRect(dp(16), dp(60), dp(146), dp(66), p);
        p.setColor(gs.hp > 40 ? 0xFF57D97A : 0xFFE0503C);
        c.drawRect(dp(16), dp(60), dp(16) + dp(130) * Math.max(0, gs.hp) / 100f, dp(66), p);

        text.setColor(0xFFEDEFF4);
        text.setTextSize(dp(15));
        c.drawText(gs.playerName, dp(16), dp(86), text);
        text.setColor(0xFF9AA4B8);
        text.setTextSize(dp(11));
        c.drawText(Elo.rankShort(gs.save.elo()) + "  " + gs.save.elo(), dp(16), dp(101), text);
        if (gs.save.showFps()) {
            text.setColor(0xFF6B7688);
            c.drawText((int) gs.fps + " FPS", dp(16), dp(116), text);
        }

        // ammo
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFFEDEFF4);
        text.setTextSize(dp(30));
        c.drawText(gs.ammoText(), w - dp(18), h - dp(178), text);
        text.setColor(0xFF9AA4B8);
        text.setTextSize(dp(12));
        c.drawText(gs.currentDef().name, w - dp(18), h - dp(162), text);

        // bomb carrier
        if (gs.playerHasBomb()) {
            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(0xFFFFB25E);
            text.setTextSize(dp(13));
            c.drawText("БОМБА У ТЕБЯ", dp(16), dp(136), text);
        }

        // kill feed
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(dp(12));
        float y = dp(70);
        for (int i = 0; i < gs.feed.size(); i++) {
            text.setColor(i == 0 ? 0xFFFFD9A6 : 0xFFB9C2D4);
            c.drawText(gs.feed.get(i), w - dp(16), y, text);
            y += dp(17);
        }

        // hint
        if (gs.hintTimer > 0 && gs.hint.length() > 0) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFFFC46B);
            text.setTextSize(dp(15));
            c.drawText(gs.hint, w / 2f, h * 0.68f, text);
        }

        // plant progress
        if (gs.plantTimer > 0 && gs.playerHasBomb()) {
            float pw = dp(220);
            p.setColor(0x66101620);
            c.drawRect(w / 2f - pw / 2, h * 0.72f, w / 2f + pw / 2, h * 0.72f + dp(10), p);
            p.setColor(0xFFFF8A1E);
            c.drawRect(w / 2f - pw / 2, h * 0.72f,
                    w / 2f - pw / 2 + pw * Math.min(1f, gs.plantTimer / 3.2f), h * 0.72f + dp(10), p);
        }
    }

    private void drawCrosshair(Canvas c) {
        if (gs.scoped) return;
        int color = 0xFF39FF6A;
        try { color = Color.parseColor(gs.save.crossColor()); } catch (Exception ignored) {}
        float s = dp(6) * gs.save.crossSize();
        float gap = dp(4) * gs.save.crossSize();
        float cx = w / 2f, cy = h / 2f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        String style = gs.save.crossStyle();
        if (style.equals("dot")) {
            c.drawCircle(cx, cy, Math.max(dp(2), dp(2.2f) * gs.save.crossSize()), p);
            return;
        }
        if (style.equals("circle")) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            c.drawCircle(cx, cy, dp(9) * gs.save.crossSize(), p);
            return;
        }
        p.setStrokeWidth(dp(2));
        c.drawLine(cx - gap - s, cy, cx - gap, cy, p);
        c.drawLine(cx + gap, cy, cx + gap + s, cy, p);
        c.drawLine(cx, cy - gap - s, cx, cy - gap, p);
        c.drawLine(cx, cy + gap, cx, cy + gap + s, p);
    }

    private void drawScope(Canvas c) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF000000);
        Path path = new Path();
        path.setFillType(Path.FillType.EVEN_ODD);
        path.addRect(0, 0, w, h, Path.Direction.CW);
        path.addCircle(w / 2f, h / 2f, Math.min(w, h) * 0.42f, Path.Direction.CW);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(0x8819FF74);
        c.drawLine(0, h / 2f, w, h / 2f, p);
        c.drawLine(w / 2f, 0, w / 2f, h, p);
        c.drawCircle(w / 2f, h / 2f, Math.min(w, h) * 0.42f, p);
    }

    private void drawHitmarker(Canvas c) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(0xFFFFFFFF);
        float a = dp(9), b = dp(15);
        c.drawLine(w / 2f - b, h / 2f - b, w / 2f - a, h / 2f - a, p);
        c.drawLine(w / 2f + a, h / 2f - a, w / 2f + b, h / 2f - b, p);
        c.drawLine(w / 2f - b, h / 2f + b, w / 2f - a, h / 2f + a, p);
        c.drawLine(w / 2f + a, h / 2f + a, w / 2f + b, h / 2f + b, p);
    }

    private void drawDamage(Canvas c) {
        if (gs.damageFlash <= 0) return;
        int alpha = (int) (Math.min(1f, gs.damageFlash / 0.4f) * 90);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(26));
        p.setColor(Color.argb(alpha, 220, 30, 30));
        c.drawRect(dp(8), dp(8), w - dp(8), h - dp(8), p);
    }

    private void drawBanners(Canvas c) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setStyle(Paint.Style.FILL);
        if (!gs.alive && !gs.matchOver) {
            p.setColor(0x66000000);
            c.drawRect(0, 0, w, h, p);
            text.setColor(0xFFFF6A5A);
            text.setTextSize(dp(30));
            c.drawText("ВЫ УБИТЫ", w / 2f, h * 0.44f, text);
            if (gs.cfg.mode != GameConfig.MODE_BOMB && gs.respawnTimer > 0) {
                text.setColor(0xFFD5DCEA);
                text.setTextSize(dp(15));
                c.drawText("Возрождение через " + ((int) Math.ceil(gs.respawnTimer) + " c"), w / 2f, h * 0.44f + dp(34), text);
            }
        }
        if (gs.freeze > 0 && !gs.matchOver) {
            text.setColor(0xFFFFC46B);
            text.setTextSize(dp(22));
            c.drawText("ПОДГОТОВКА " + ((int) Math.ceil(gs.freeze)), w / 2f, h * 0.3f, text);
        }
        if (gs.roundOver && gs.roundEndTimer > 0) {
            text.setColor(gs.roundWinner == 0 ? 0xFF57D97A : 0xFFFF6A5A);
            text.setTextSize(dp(26));
            c.drawText(gs.roundWinner == 0 ? "РАУНД ВЫИГРАН" : "РАУНД ПРОИГРАН", w / 2f, h * 0.3f, text);
        }
    }
}
