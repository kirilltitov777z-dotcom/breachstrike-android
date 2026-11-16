package com.breachstrike.game.render;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.breachstrike.game.core.Assets;
import com.breachstrike.game.core.Gl;
import com.breachstrike.game.core.Mat4;
import com.breachstrike.game.game.Bot;
import com.breachstrike.game.game.GameState;
import com.breachstrike.game.game.MapData;
import com.breachstrike.game.game.Remote;
import com.breachstrike.game.game.Skins;
import com.breachstrike.game.game.Weapons;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;

public final class GameRenderer {
    private static final String VS =
            "uniform mat4 uProj; uniform mat4 uView; uniform mat4 uModel;\n" +
            "attribute vec3 aPos; attribute vec3 aNormal; attribute vec2 aUv;\n" +
            "varying vec2 vUv; varying vec3 vN; varying vec3 vW;\n" +
            "void main(){ vec4 w = uModel * vec4(aPos,1.0); vW = w.xyz; vN = mat3(uModel) * aNormal; vUv = aUv;\n" +
            "gl_Position = uProj * uView * w; }\n";

    private static final String FS =
            "precision mediump float;\n" +
            "uniform sampler2D uTex; uniform vec3 uTint; uniform vec3 uFog;\n" +
            "uniform float uFogNear; uniform float uFogFar; uniform vec3 uCam; uniform float uAlpha;\n" +
            "varying vec2 vUv; varying vec3 vN; varying vec3 vW;\n" +
            "void main(){ vec4 c = texture2D(uTex, vUv); c.rgb *= uTint;\n" +
            " vec3 n = normalize(vN); float l = max(dot(n, normalize(vec3(0.45, 0.9, 0.35))), 0.0);\n" +
            " c.rgb *= (0.55 + 0.45 * l);\n" +
            " float d = distance(vW, uCam); float f = clamp((d - uFogNear) / (uFogFar - uFogNear), 0.0, 0.75);\n" +
            " c.rgb = mix(c.rgb, uFog, f);\n" +
            " gl_FragColor = vec4(c.rgb, c.a * uAlpha); }\n";

    private static final class Mesh {
        int vbo;
        int count;
    }

    private final Assets assets;
    private final GameState gs;
    private final MapData map;
    private final HashMap<String, Mesh> world = new HashMap<>();
    private final HashMap<String, Mesh> dynamic = new HashMap<>();
    private int prog;
    private int uProj, uView, uModel, uTex, uTint, uFog, uFogNear, uFogFar, uCam, uAlpha;
    private float aspect = 1.8f;
    private final float[] proj = new float[16];
    private final float[] view = new float[16];
    private final float[] identity = Mat4.identity();
    private float time;
    private final float[] camPos = new float[3];
    private String vmWeapon = "";
    private String vmSkin = "";

    public GameRenderer(Assets assets, GameState gs) {
        this.assets = assets;
        this.gs = gs;
        this.map = gs.map;
    }

    public void init() {
        prog = Gl.program(VS, FS);
        uProj = GLES20.glGetUniformLocation(prog, "uProj");
        uView = GLES20.glGetUniformLocation(prog, "uView");
        uModel = GLES20.glGetUniformLocation(prog, "uModel");
        uTex = GLES20.glGetUniformLocation(prog, "uTex");
        uTint = GLES20.glGetUniformLocation(prog, "uTint");
        uFog = GLES20.glGetUniformLocation(prog, "uFog");
        uFogNear = GLES20.glGetUniformLocation(prog, "uFogNear");
        uFogFar = GLES20.glGetUniformLocation(prog, "uFogFar");
        uCam = GLES20.glGetUniformLocation(prog, "uCam");
        uAlpha = GLES20.glGetUniformLocation(prog, "uAlpha");

        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        buildWorld();
        preloadTextures();
    }

    private void preloadTextures() {
        for (String mat : world.keySet()) assets.tex("mat_" + mat);
        assets.tex("bot_t");
        assets.tex("bot_ct");
        assets.tex("mat_bomb");
        assets.tex("mat_gunmetal");
        // Weapon skins are decoded on first use: only one or two are ever worn
        // in a match, and each source is a 1536x1536 PNG.
    }

    public void resize(int w, int h) {
        if (h <= 0) h = 1;
        aspect = (float) w / (float) h;
    }

    private void buildWorld() {
        HashMap<String, ArrayList<Float>> groups = new HashMap<>();
        for (MapData.Box b : map.boxes) {
            ArrayList<Float> out = groups.get(b.mat);
            if (out == null) {
                out = new ArrayList<>();
                groups.put(b.mat, out);
            }
            box(out, b.x, b.y, b.z, b.hx * 2, b.hy * 2, b.hz * 2, 0.35f);
        }
        for (String key : groups.keySet()) {
            float[] data = toArray(groups.get(key));
            Mesh m = new Mesh();
            m.count = data.length / 8;
            int[] vbo = new int[1];
            GLES20.glGenBuffers(1, vbo, 0);
            m.vbo = vbo[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, m.vbo);
            FloatBuffer fb = buffer(data);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            world.put(key, m);
        }
    }

    private static float[] toArray(ArrayList<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }

    private static FloatBuffer buffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(data);
        fb.position(0);
        return fb;
    }

    public void draw(float dt) {
        time += dt;
        float[] eye = gs.eye();
        camPos[0] = eye[0]; camPos[1] = eye[1]; camPos[2] = eye[2];
        float[] dir = gs.dir();
        float fov = gs.scoped ? gs.currentDef().scopeFov : gs.save.fov();
        if (fov < 30) fov = 30;
        if (fov > 110) fov = 110;
        int q = gs.save.quality();
        float far = q <= 0 ? 130f : q == 1 ? 180f : 220f;
        float[] p = Mat4.perspective(gs.scoped ? fov : Math.max(55f, fov), aspect, 0.05f, far);
        System.arraycopy(p, 0, proj, 0, 16);
        float[] v = Mat4.lookAt(eye[0], eye[1], eye[2],
                eye[0] + dir[0], eye[1] + dir[1], eye[2] + dir[2],
                0, 1, 0);
        System.arraycopy(v, 0, view, 0, 16);

        GLES20.glClearColor(hexR(map.skyColor), hexG(map.skyColor), hexB(map.skyColor), 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(prog);
        GLES20.glUniformMatrix4fv(uProj, 1, false, proj, 0);
        GLES20.glUniformMatrix4fv(uView, 1, false, view, 0);
        GLES20.glUniform3f(uFog, hexR(map.skyColor), hexG(map.skyColor), hexB(map.skyColor));
        GLES20.glUniform1f(uFogNear, q <= 0 ? 22f : 35f);
        GLES20.glUniform1f(uFogFar, q <= 0 ? 90f : q == 1 ? 110f : 130f);
        GLES20.glUniform3f(uCam, eye[0], eye[1], eye[2]);
        GLES20.glUniform1f(uAlpha, 1f);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        drawWorld();
        drawBots();
        drawRemotes();
        drawBomb();
        drawViewmodel();
    }

    private void drawWorld() {
        for (HashMap.Entry<String, Mesh> e : world.entrySet()) {
            Mesh m = e.getValue();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, assets.tex("mat_" + e.getKey()));
            GLES20.glUniformMatrix4fv(uModel, 1, false, identity, 0);
            GLES20.glUniform3f(uTint, 1, 1, 1);
            GLES20.glUniform1f(uAlpha, 1f);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, m.vbo);
            bindAttribsVbo();
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, m.count);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    private void drawBots() {
        if (gs.bots.isEmpty()) return;
        drawTeamBots(0);
        drawTeamBots(1);
    }

    private void drawTeamBots(int team) {
        ArrayList<Float> out = new ArrayList<>();
        for (Bot b : gs.bots) {
            if (!b.alive || b.team != team) continue;
            float x = b.body.x, y = b.body.y, z = b.body.z;
            box(out, x, y + 1.16f, z, 0.58f, 0.64f, 0.34f, 0.7f);
            box(out, x, y + 1.64f, z, 0.3f, 0.3f, 0.3f, 0.7f);
            box(out, x - 0.17f, y + 0.4f, z, 0.18f, 0.8f, 0.24f, 0.7f);
            box(out, x + 0.17f, y + 0.4f, z, 0.18f, 0.8f, 0.24f, 0.7f);
            box(out, x - 0.35f, y + 1.14f, z, 0.14f, 0.56f, 0.18f, 0.7f);
            box(out, x + 0.35f, y + 1.14f, z, 0.14f, 0.56f, 0.18f, 0.7f);
        }
        if (out.isEmpty()) return;
        drawBatch(out, team == 0 ? "bot_t" : "bot_ct", new float[]{1, 1, 1}, identity);
    }

    private void drawRemotes() {
        if (gs.remotes.isEmpty()) return;
        ArrayList<Float> out = new ArrayList<>();
        for (Remote r : gs.remotes) {
            if (!r.alive) continue;
            float x = r.x, y = r.y, z = r.z;
            box(out, x, y + 1.16f, z, 0.58f, 0.64f, 0.34f, 0.7f);
            box(out, x, y + 1.64f, z, 0.3f, 0.3f, 0.3f, 0.7f);
            box(out, x - 0.17f, y + 0.4f, z, 0.18f, 0.8f, 0.24f, 0.7f);
            box(out, x + 0.17f, y + 0.4f, z, 0.18f, 0.8f, 0.24f, 0.7f);
            box(out, x - 0.35f, y + 1.14f, z, 0.14f, 0.56f, 0.18f, 0.7f);
            box(out, x + 0.35f, y + 1.14f, z, 0.14f, 0.56f, 0.18f, 0.7f);
        }
        if (!out.isEmpty()) drawBatch(out, "bot_ct", new float[]{0.85f, 0.95f, 1f}, identity);
    }

    private void drawBomb() {        if (!gs.planted || gs.bombPos == null) return;
        ArrayList<Float> out = new ArrayList<>();
        box(out, gs.bombPos[0], gs.bombPos[1] + 0.16f, gs.bombPos[2], 0.34f, 0.3f, 0.24f, 1f);
        float blink = (Math.sin(time * 9f) > 0) ? 1f : 0.55f;
        drawBatch(out, "mat_bomb", new float[]{blink, blink * 0.5f, blink * 0.5f}, identity);
    }

    private void drawBatch(ArrayList<Float> list, String tex, float[] tint, float[] model) {
        if (list.isEmpty()) return;
        float[] data = toArray(list);
        FloatBuffer fb = buffer(data);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, assets.tex(tex));
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES20.glUniform3f(uTint, tint[0], tint[1], tint[2]);
        bindAttribsClient(fb);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, data.length / 8);
    }

    private void drawViewmodel() {
        if (!gs.alive || gs.scoped) return;
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
        Weapons.Def d = gs.currentDef();
        float[] p = Mat4.perspective(58f, aspect, 0.01f, 6f);
        GLES20.glUniformMatrix4fv(uProj, 1, false, p, 0);
        GLES20.glUniformMatrix4fv(uView, 1, false, identity, 0);
        GLES20.glUniform1f(uFogNear, 100f);
        GLES20.glUniform1f(uFogFar, 200f);

        if (!d.id.equals(vmWeapon)) {
            vmWeapon = d.id;
            vmSkin = gs.save.equippedSkin(d.id);
        }
        String pattern = vmSkin;
        String tex = pattern.length() > 0 ? Skins.skinTexture(Skins.patternOf(pattern)) : "mat_gunmetal";
        float[] tint;
        if (pattern.length() > 0) {
            float[] t = Skins.tint(d.id);
            tint = new float[]{0.55f + 0.45f * t[0], 0.55f + 0.45f * t[1], 0.55f + 0.45f * t[2]};
        } else {
            tint = new float[]{1f, 1f, 1f};
        }

        float bob = (float) Math.sin(gs.bobTime) * 0.012f;
        float bob2 = (float) Math.abs(Math.cos(gs.bobTime)) * 0.008f;
        float draw = gs.drawTimer > 0 ? gs.drawTimer / 0.45f : 0f;
        float reloadT = gs.reloading ? 1f - gs.reloadTimer / Math.max(0.1f, d.reload) : 0f;
        float inspectT = gs.inspectTimer > 0 ? 1f - gs.inspectTimer / 2.2f : 0f;
        float knifeT = gs.knifeTimer > 0 ? 1f - gs.knifeTimer / 0.55f : 0f;

        float gx = d.cat.equals("pistol") || d.isKnife() ? 0.22f : 0.25f;
        float gy = -0.24f + bob2;
        float gz = -0.52f;
        float rx = 0f, ry = -7f, rz = 0f;

        if (draw > 0) {
            gy -= 0.32f * draw;
            rx -= 35f * draw;
            rz += 18f * draw;
        }
        if (reloadT > 0) {
            float s = (float) Math.sin(reloadT * Math.PI);
            gy -= 0.24f * s;
            rx -= 28f * s;
            rz += 16f * s;
        }
        if (inspectT > 0) {
            float s = (float) Math.sin(inspectT * Math.PI);
            ry += 55f * s;
            rz -= 22f * s;
            gx -= 0.04f * s;
        }
        if (knifeT > 0) {
            float s = (float) Math.sin(knifeT * Math.PI);
            gx -= 0.24f * s;
            rz -= 75f * s;
            rx -= 15f * s;
        }
        if (gs.viewKick > 0) {
            rz += gs.viewKick * 3f;
            rx += gs.viewKick * 4f;
            gz += gs.viewKick * 0.05f;
        }

        float gxb = gx + bob;
        vmX = gxb; vmY = gy; vmZ = gz;
        vmBase = Mat4.translate(gxb, gy, gz);
        vmBase = Mat4.mul(vmBase, Mat4.rotateY(ry));
        vmBase = Mat4.mul(vmBase, Mat4.rotateX(rx));
        vmBase = Mat4.mul(vmBase, Mat4.rotateZ(rz));

        if (d.isKnife()) {
            drawKnife(d, tex, tint, gxb, gy, gz, rx, ry, rz, inspectT);
            return;
        }
        boolean rifle = d.cat.equals("rifle");
        boolean smg = d.cat.equals("smg");
        boolean shot = d.cat.equals("shotgun");
        boolean lmg = d.cat.equals("lmg");
        boolean sniper = d.cat.equals("sniper");
        float len = rifle ? 0.5f : smg ? 0.34f : shot ? 0.46f : lmg ? 0.55f : sniper ? 0.6f : 0.22f;
        float bodyH = rifle || sniper || lmg ? 0.115f : 0.095f;

        // receiver
        part(tex, tint, gx + bob, gy, gz, 0.085f, bodyH, len, 0, 0, 0, rx, ry, rz);
        // barrel
        part(tex, tint, gx + bob, gy + 0.012f, gz - len / 2 - 0.08f, 0.038f, 0.038f,
                rifle || sniper || lmg ? 0.24f : 0.1f, 0, 0, 0, rx, ry, rz);
        // grip
        part(tex, tint, gx + bob, gy - 0.11f, gz + len * 0.28f, 0.052f, 0.14f, 0.07f, 12, 0, 0, rx, ry, rz);
        // mag
        if (!d.cat.equals("pistol") && !shot) {
            float magDrop = reloadT > 0 ? 0.14f * (float) Math.sin(reloadT * Math.PI) : 0f;
            part(tex, tint, gx + bob, gy - 0.14f - magDrop, gz + (rifle ? 0.02f : 0.01f),
                    0.06f, 0.2f, 0.1f, 6, 0, 0, rx, ry, rz);
        }
        if (rifle || sniper || lmg) {
            part(tex, tint, gx + bob, gy - 0.012f, gz + len / 2 + 0.06f, 0.065f, 0.1f, 0.13f, 0, 0, 0, rx, ry, rz);
        }
        if (sniper || d.id.equals("aug") || d.id.equals("sg553")) {
            part("mat_gunmetal", new float[]{1, 1, 1}, gx + bob, gy + 0.1f, gz - 0.02f,
                    0.05f, 0.05f, 0.16f, 0, 0, 0, rx, ry, rz);
        }
        if (lmg) {
            part(tex, tint, gx + bob, gy - 0.17f, gz + 0.02f, 0.16f, 0.16f, 0.12f, 0, 0, 0, rx, ry, rz);
        }
        if (shot) {
            part("mat_gunmetal", new float[]{1, 1, 1}, gx + bob, gy - 0.05f, gz - 0.14f,
                    0.07f, 0.06f, 0.16f, 0, 0, 0, rx, ry, rz);
        }

        if (gs.recoilKick > 0.75f) {
            float[] flashTint = {1f, 0.85f, 0.4f};
            part("mat_flash", flashTint, gx + bob, gy + 0.012f, gz - len / 2 - 0.2f,
                    0.09f, 0.09f, 0.09f, 0, 0, 0, rx, ry, rz);
        }
    }

    private void drawKnife(Weapons.Def d, String tex, float[] tint, float gx, float gy, float gz,
                           float rx, float ry, float rz, float inspectT) {
        float spin = 0, handleSpread = 0;
        if (inspectT > 0) {
            if (d.id.equals("karambit")) spin = 720f * inspectT;
            else if (d.id.equals("flip")) spin = 540f * inspectT;
            else if (d.id.equals("butterfly")) {
                float s = (float) Math.sin(inspectT * Math.PI);
                handleSpread = 45f * s;
                spin = 30f * s;
            } else if (d.id.equals("talon")) {
                spin = 25f * (float) Math.sin(inspectT * Math.PI);
            }
        }
        if (spin != 0) vmBase = Mat4.mul(vmBase, Mat4.rotateY(spin));

        // local parts, vmBase already holds position + view rotation
        part(tex, tint, gx, gy - 0.02f, gz + 0.06f, 0.035f, 0.09f, 0.16f, 0, 0, handleSpread, 0, 0, 0);
        part("mat_gunmetal", new float[]{1, 1, 1}, gx, gy + 0.01f, gz - 0.02f, 0.11f, 0.03f, 0.03f, 0, 0, 0, 0, 0, 0);
        float bladeLen = d.id.equals("karambit") ? 0.22f : 0.3f;
        part("mat_blade", new float[]{1, 1, 1},
                gx + (d.id.equals("karambit") ? 0.05f : 0f), gy + 0.04f, gz - 0.02f - bladeLen / 2,
                0.045f, 0.012f, bladeLen, 0, 0, 0, 0, 0, 0);
        if (d.id.equals("butterfly")) {
            part(tex, tint, gx - 0.06f, gy - 0.05f, gz + 0.05f, 0.03f, 0.06f, 0.14f, 0, 0, -handleSpread, 0, 0, 0);
        }
        if (gs.recoilKick > 0.75f) {
            part("mat_flash", new float[]{1f, 0.9f, 0.5f}, gx, gy + 0.04f, gz - 0.32f, 0.06f, 0.06f, 0.06f, 0, 0, 0, 0, 0, 0);
        }
    }

    private final ArrayList<Float> one = new ArrayList<>();
    private float[] vmBase = Mat4.identity();
    private float vmX, vmY, vmZ;

    private void part(String tex, float[] tint, float x, float y, float z,
                      float sx, float sy, float sz,
                      float prx, float pry, float prz,
                      float rx, float ry, float rz) {
        float[] m = Mat4.mul(vmBase, Mat4.translate(x - vmX, y - vmY, z - vmZ));
        m = Mat4.mul(m, Mat4.rotateZ(prz));
        m = Mat4.mul(m, Mat4.rotateY(pry));
        m = Mat4.mul(m, Mat4.rotateX(prx));
        one.clear();
        boxLocal(one, sx, sy, sz, 1f);
        drawBatch(one, tex, tint, m);
    }

    private static void boxLocal(ArrayList<Float> out, float sx, float sy, float sz, float uScale) {
        box(out, 0, 0, 0, sx, sy, sz, uScale);
    }

    // ---------- geometry ----------
    public static void box(ArrayList<Float> out, float cx, float cy, float cz,
                           float sx, float sy, float sz, float uScale) {
        float x = sx / 2, y = sy / 2, z = sz / 2;
        // +Z
        quad(out, cx - x, cy - y, cz + z, cx + x, cy - y, cz + z, cx + x, cy + y, cz + z, cx - x, cy + y, cz + z,
                0, 0, 1, sx * uScale, sy * uScale);
        // -Z
        quad(out, cx + x, cy - y, cz - z, cx - x, cy - y, cz - z, cx - x, cy + y, cz - z, cx + x, cy + y, cz - z,
                0, 0, -1, sx * uScale, sy * uScale);
        // -X
        quad(out, cx - x, cy - y, cz - z, cx - x, cy - y, cz + z, cx - x, cy + y, cz + z, cx - x, cy + y, cz - z,
                -1, 0, 0, sz * uScale, sy * uScale);
        // +X
        quad(out, cx + x, cy - y, cz + z, cx + x, cy - y, cz - z, cx + x, cy + y, cz - z, cx + x, cy + y, cz + z,
                1, 0, 0, sz * uScale, sy * uScale);
        // +Y
        quad(out, cx - x, cy + y, cz + z, cx + x, cy + y, cz + z, cx + x, cy + y, cz - z, cx - x, cy + y, cz - z,
                0, 1, 0, sx * uScale, sz * uScale);
        // -Y
        quad(out, cx - x, cy - y, cz - z, cx + x, cy - y, cz - z, cx + x, cy - y, cz + z, cx - x, cy - y, cz + z,
                0, -1, 0, sx * uScale, sz * uScale);
    }

    private static void quad(ArrayList<Float> out,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz, float u, float v) {
        vert(out, x0, y0, z0, nx, ny, nz, 0, 0);
        vert(out, x1, y1, z1, nx, ny, nz, u, 0);
        vert(out, x2, y2, z2, nx, ny, nz, u, v);
        vert(out, x0, y0, z0, nx, ny, nz, 0, 0);
        vert(out, x2, y2, z2, nx, ny, nz, u, v);
        vert(out, x3, y3, z3, nx, ny, nz, 0, v);
    }

    private static void vert(ArrayList<Float> out, float x, float y, float z,
                             float nx, float ny, float nz, float u, float v) {
        out.add(x); out.add(y); out.add(z);
        out.add(nx); out.add(ny); out.add(nz);
        out.add(u); out.add(v);
    }

    private void bindAttribsVbo() {
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glEnableVertexAttribArray(1);
        GLES20.glEnableVertexAttribArray(2);
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 32, 0);
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 32, 12);
        GLES20.glVertexAttribPointer(2, 2, GLES20.GL_FLOAT, false, 32, 24);
    }

    private void bindAttribsClient(FloatBuffer fb) {
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glEnableVertexAttribArray(1);
        GLES20.glEnableVertexAttribArray(2);
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 32, fb);
        fb.position(3);
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 32, fb);
        fb.position(6);
        GLES20.glVertexAttribPointer(2, 2, GLES20.GL_FLOAT, false, 32, fb);
        fb.position(0);
    }

    private static float hexR(int c) { return ((c >> 16) & 0xFF) / 255f; }
    private static float hexG(int c) { return ((c >> 8) & 0xFF) / 255f; }
    private static float hexB(int c) { return (c & 0xFF) / 255f; }
}
