package dev.lilt.player.ui;

import java.util.Arrays;

/**
 * Parses SVG / VectorDrawable path data into absolute move, line, quadratic, cubic and close
 * segments. Arcs are converted to cubic Béziers, H/V to lines and S/T to explicit curves, so a
 * consumer only needs five drawing calls (see {@code GlyphDrawable}). Pure Java.
 *
 * <p>Coordinates are stored flat in {@link #coords}; each op consumes 2 (MOVE, LINE),
 * 4 (QUAD), 6 (CUBIC) or 0 (CLOSE) values in order.
 */
public final class SvgPath {
    public static final int MOVE = 0, LINE = 1, QUAD = 2, CUBIC = 3, CLOSE = 4;

    private final int[] ops;
    private final float[] coords;
    private final int opCount, coordCount;

    private SvgPath(int[] ops, int opCount, float[] coords, int coordCount) {
        this.ops = ops;
        this.opCount = opCount;
        this.coords = coords;
        this.coordCount = coordCount;
    }

    public int opCount() { return opCount; }
    public int op(int i) { return ops[i]; }
    /** A copy of the flat coordinate list. */
    public float[] coords() { return Arrays.copyOf(coords, coordCount); }
    public float coord(int i) { return coords[i]; }

    /** Number of coordinates consumed by {@code op}. */
    public static int arity(int op) {
        switch (op) {
            case MOVE: case LINE: return 2;
            case QUAD: return 4;
            case CUBIC: return 6;
            default: return 0;
        }
    }

    /** Bounding box of all points (end points and control points): {left, top, right, bottom}. */
    public float[] controlBounds() {
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < coordCount; i += 2) {
            l = Math.min(l, coords[i]); r = Math.max(r, coords[i]);
            t = Math.min(t, coords[i + 1]); b = Math.max(b, coords[i + 1]);
        }
        return new float[] {l, t, r, b};
    }

    /** Parses {@code d}. Throws IllegalArgumentException on malformed data. */
    public static SvgPath parse(String d) {
        return new Parser(d).run();
    }

    private static final class Parser {
        private final String s;
        private int pos;
        private int[] ops = new int[16];
        private float[] co = new float[64];
        private int nOps, nCo;
        private float cx, cy, startX, startY, lastCtrlX, lastCtrlY;
        private char lastCmd;

        Parser(String s) { this.s = s == null ? "" : s; }

        SvgPath run() {
            char cmd = 0;
            while (true) {
                skipSeparators();
                if (pos >= s.length()) break;
                char c = s.charAt(pos);
                if (Character.isLetter(c) && c != 'e' && c != 'E') { cmd = c; pos++; }
                else if (cmd == 0) throw new IllegalArgumentException("path must start with a command at " + pos);
                else if (cmd == 'M') cmd = 'L'; // implicit lineto after moveto
                else if (cmd == 'm') cmd = 'l';
                else if (cmd == 'Z' || cmd == 'z') throw new IllegalArgumentException("number after close at " + pos);
                command(cmd);
            }
            return new SvgPath(ops, nOps, co, nCo);
        }

        private void command(char cmd) {
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? cx : 0, oy = rel ? cy : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M': {
                    float x = num() + ox, y = num() + oy;
                    add(MOVE, x, y);
                    cx = startX = x; cy = startY = y;
                    break;
                }
                case 'L': { float x = num() + ox, y = num() + oy; add(LINE, x, y); cx = x; cy = y; break; }
                case 'H': { float x = num() + ox; add(LINE, x, cy); cx = x; break; }
                case 'V': { float y = num() + oy; add(LINE, cx, y); cy = y; break; }
                case 'C': {
                    float x1 = num() + ox, y1 = num() + oy, x2 = num() + ox, y2 = num() + oy, x = num() + ox, y = num() + oy;
                    add(CUBIC, x1, y1, x2, y2, x, y); lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y;
                    break;
                }
                case 'S': {
                    boolean prevCubic = lastCmd == 'C' || lastCmd == 'S';
                    float x1 = prevCubic ? 2 * cx - lastCtrlX : cx, y1 = prevCubic ? 2 * cy - lastCtrlY : cy;
                    float x2 = num() + ox, y2 = num() + oy, x = num() + ox, y = num() + oy;
                    add(CUBIC, x1, y1, x2, y2, x, y); lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y;
                    break;
                }
                case 'Q': {
                    float x1 = num() + ox, y1 = num() + oy, x = num() + ox, y = num() + oy;
                    add(QUAD, x1, y1, x, y); lastCtrlX = x1; lastCtrlY = y1; cx = x; cy = y;
                    break;
                }
                case 'T': {
                    boolean prevQuad = lastCmd == 'Q' || lastCmd == 'T';
                    float x1 = prevQuad ? 2 * cx - lastCtrlX : cx, y1 = prevQuad ? 2 * cy - lastCtrlY : cy;
                    float x = num() + ox, y = num() + oy;
                    add(QUAD, x1, y1, x, y); lastCtrlX = x1; lastCtrlY = y1; cx = x; cy = y;
                    break;
                }
                case 'A': {
                    float rx = num(), ry = num(), rot = num();
                    boolean large = flag(), sweep = flag();
                    float x = num() + ox, y = num() + oy;
                    arc(cx, cy, rx, ry, rot, large, sweep, x, y);
                    cx = x; cy = y;
                    break;
                }
                case 'Z': add(CLOSE); cx = startX; cy = startY; break;
                default: throw new IllegalArgumentException("unsupported path command '" + cmd + "'");
            }
            lastCmd = Character.toUpperCase(cmd);
        }

        // SVG implementation notes F.6.5: endpoint to centre parameterisation, then <= 90° cubic pieces.
        private void arc(float x0, float y0, float rx, float ry, float angleDeg, boolean large, boolean sweep, float x, float y) {
            if (x0 == x && y0 == y) return;
            rx = Math.abs(rx); ry = Math.abs(ry);
            if (rx == 0 || ry == 0) { add(LINE, x, y); return; }
            double phi = Math.toRadians(angleDeg), cos = Math.cos(phi), sin = Math.sin(phi);
            double dx = (x0 - x) / 2.0, dy = (y0 - y) / 2.0;
            double x1p = cos * dx + sin * dy, y1p = -sin * dx + cos * dy;
            double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
            if (lambda > 1) { double k = Math.sqrt(lambda); rx *= k; ry *= k; }
            double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
            double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
            double coef = Math.sqrt(Math.max(0, num / den)) * (large == sweep ? -1 : 1);
            double cxp = coef * rx * y1p / ry, cyp = -coef * ry * x1p / rx;
            double ccx = cos * cxp - sin * cyp + (x0 + x) / 2.0, ccy = sin * cxp + cos * cyp + (y0 + y) / 2.0;
            double t1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
            double dt = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
            if (!sweep && dt > 0) dt -= 2 * Math.PI;
            else if (sweep && dt < 0) dt += 2 * Math.PI;
            int segments = Math.max(1, (int) Math.ceil(Math.abs(dt) / (Math.PI / 2) - 1e-7));
            double step = dt / segments, k = 4.0 / 3.0 * Math.tan(step / 4);
            double a = t1;
            for (int i = 0; i < segments; i++) {
                double b = a + step;
                double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
                double p1x = ca - k * sa, p1y = sa + k * ca, p2x = cb + k * sb, p2y = sb - k * cb;
                float[] c1 = map(p1x, p1y, rx, ry, cos, sin, ccx, ccy), c2 = map(p2x, p2y, rx, ry, cos, sin, ccx, ccy);
                float[] e = i == segments - 1 ? new float[] {x, y} : map(cb, sb, rx, ry, cos, sin, ccx, ccy);
                add(CUBIC, c1[0], c1[1], c2[0], c2[1], e[0], e[1]);
                a = b;
            }
        }

        private static float[] map(double ux, double uy, double rx, double ry, double cos, double sin, double cx, double cy) {
            double px = ux * rx, py = uy * ry;
            return new float[] {(float) (cos * px - sin * py + cx), (float) (sin * px + cos * py + cy)};
        }

        private static double angle(double ux, double uy, double vx, double vy) {
            double a = Math.atan2(uy, ux), b = Math.atan2(vy, vx), d = b - a;
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d <= -Math.PI) d += 2 * Math.PI;
            return d;
        }

        private void add(int op, float... values) {
            if (nOps == ops.length) ops = Arrays.copyOf(ops, nOps * 2);
            ops[nOps++] = op;
            if (nCo + values.length > co.length) co = Arrays.copyOf(co, Math.max(co.length * 2, nCo + values.length));
            for (float v : values) co[nCo++] = v;
        }

        private void skipSeparators() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r') pos++; else break;
            }
        }

        private boolean flag() {
            skipSeparators();
            if (pos >= s.length()) throw new IllegalArgumentException("missing arc flag");
            char c = s.charAt(pos++);
            if (c == '0') return false;
            if (c == '1') return true;
            throw new IllegalArgumentException("bad arc flag '" + c + "' at " + (pos - 1));
        }

        private float num() {
            skipSeparators();
            int start = pos;
            if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
            boolean digits = false, dot = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') { digits = true; pos++; }
                else if (c == '.' && !dot) { dot = true; pos++; }
                else break;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                int save = pos;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '-' || s.charAt(pos) == '+')) pos++;
                int expStart = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                if (pos == expStart) pos = save;
            }
            if (!digits) throw new IllegalArgumentException("expected a number at " + start + " in \"" + s + "\"");
            return Float.parseFloat(s.substring(start, pos));
        }
    }
}
