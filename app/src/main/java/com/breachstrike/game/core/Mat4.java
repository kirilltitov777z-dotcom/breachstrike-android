package com.breachstrike.game.core;

/** Minimal column-major matrix math for OpenGL ES 2.0. */
public final class Mat4 {
    private Mat4() {}

    public static float[] identity() {
        return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }

    public static float[] mul(float[] a, float[] b) {
        float[] o = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                o[c * 4 + r] =
                        a[r] * b[c * 4] +
                        a[4 + r] * b[c * 4 + 1] +
                        a[8 + r] * b[c * 4 + 2] +
                        a[12 + r] * b[c * 4 + 3];
            }
        }
        return o;
    }

    public static float[] translate(float x, float y, float z) {
        float[] m = identity();
        m[12] = x; m[13] = y; m[14] = z;
        return m;
    }

    public static float[] scale(float x, float y, float z) {
        float[] m = identity();
        m[0] = x; m[5] = y; m[10] = z;
        return m;
    }

    public static float[] rotateX(float deg) {
        float a = (float) Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] m = identity();
        m[5] = c; m[6] = s; m[9] = -s; m[10] = c;
        return m;
    }

    public static float[] rotateY(float deg) {
        float a = (float) Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] m = identity();
        m[0] = c; m[2] = -s; m[8] = s; m[10] = c;
        return m;
    }

    public static float[] rotateZ(float deg) {
        float a = (float) Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float[] m = identity();
        m[0] = c; m[1] = s; m[4] = -s; m[5] = c;
        return m;
    }

    /** T * Rz * Ry * Rx * S. */
    public static float[] model(float x, float y, float z,
                                float rx, float ry, float rz,
                                float sx, float sy, float sz) {
        float[] m = translate(x, y, z);
        if (rz != 0) m = mul(m, rotateZ(rz));
        if (ry != 0) m = mul(m, rotateY(ry));
        if (rx != 0) m = mul(m, rotateX(rx));
        m = mul(m, scale(sx, sy, sz));
        return m;
    }

    public static float[] perspective(float fovYDeg, float aspect, float near, float far) {
        float f = 1.0f / (float) Math.tan(Math.toRadians(fovYDeg) / 2.0);
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = (2f * far * near) / (near - far);
        return m;
    }

    public static float[] lookAt(float ex, float ey, float ez,
                                 float cx, float cy, float cz,
                                 float ux, float uy, float uz) {
        float fx = cx - ex, fy = cy - ey, fz = cz - ez;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 0.00001f) { fl = 1f; fz = -1f; }
        fx /= fl; fy /= fl; fz /= fl;

        float sx = fy * uz - fz * uy;
        float sy = fz * ux - fx * uz;
        float sz = fx * uy - fy * ux;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 0.00001f) { sx = 1f; sy = 0f; sz = 0f; sl = 1f; }
        sx /= sl; sy /= sl; sz /= sl;

        float ux2 = sy * fz - sz * fy;
        float uy2 = sz * fx - sx * fz;
        float uz2 = sx * fy - sy * fx;

        float[] m = new float[16];
        m[0] = sx; m[4] = sy; m[8] = sz;
        m[1] = ux2; m[5] = uy2; m[9] = uz2;
        m[2] = -fx; m[6] = -fy; m[10] = -fz;
        m[12] = -(sx * ex + sy * ey + sz * ez);
        m[13] = -(ux2 * ex + uy2 * ey + uz2 * ez);
        m[14] = (fx * ex + fy * ey + fz * ez);
        m[15] = 1f;
        return m;
    }
}
