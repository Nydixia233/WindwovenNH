package io.github.windwovennh.cloth;

/** Rear-facing proxies in cape coordinates (Y down, Z back from the cloak call's origin). */
final class BodyContact {
    // The vanilla cloak call translates two model pixels back from the biped's yaw axis.
    static final float CLOAK_ORIGIN_REAR = .125F;
    // Standard biped helmet volume, including clearance for head yaw and a small seam margin.
    static final float HEAD_HALF_WIDTH = .455F;
    static final float HEAD_REAR = .333F;
    static final float HEAD_TOP = -.585F;
    // The collar owns the narrow band at the head/neck attachment. Its pins are never projected.
    static final float HEAD_BOTTOM = 0;
    static final float HEAD_CROUCH = .023F;
    // Round the clearance volume from the underside to a tangent on the rear face.
    // This tapers only the proxy margin, not the volume of the standard helmet.
    private static final float LIP_START = .075F, LIP_DEPTH = .135F;
    private static final float LIP_WIDTH = HEAD_REAR - LIP_START;
    private static final float LIP_SCALE = 1 / LIP_WIDTH;

    private BodyContact() {}

    static void projectHead(float[] v, int i, float top, float bottom, int parent, float span) {
        float down = v[i + 1], rear = v[i + 2];
        if (down <= top || down >= bottom || rear >= HEAD_REAR || Math.abs(v[i]) >= HEAD_HALF_WIDTH) return;
        float floor = lowerLip(rear, bottom);
        if (down >= floor) return;
        // Keep the contact face reachable from the warp parent, especially just below the pins.
        // Otherwise alternating contact and extension can keep throwing a short collar link over the rim.
        float py = v[parent + 1], pz = v[parent + 2], px = v[parent];
        if (Math.abs(px) < HEAD_HALF_WIDTH && rear > LIP_START && down > bottom - LIP_DEPTH) {
            // In the rounded quadrant, follow one continuous contact direction instead of
            // choosing between a vertical shove and a horizontal one. Keep the warp budget.
            float y = (down - bottom + LIP_DEPTH) / LIP_DEPTH, z = (rear - LIP_START) * LIP_SCALE;
            float scale = 1 / (float) Math.sqrt(y * y + z * z);
            float roundedRear = LIP_START + z * scale * LIP_WIDTH;
            float roundedDown = lowerLip(roundedRear, bottom);
            float dx = v[i] - px, dy = roundedDown - py, dz = roundedRear - pz;
            if (dx * dx + dy * dy + dz * dz <= span * span) {
                v[i + 1] = roundedDown;
                v[i + 2] = roundedRear;
                return;
            }
        }
        float below = floor - py <= span ? floor - down : Float.POSITIVE_INFINITY;
        float above = py - top <= span ? down - top : Float.POSITIVE_INFINITY;
        float behind = HEAD_REAR - pz <= span ? HEAD_REAR - rear : Float.POSITIVE_INFINITY;
        int axis;
        float plane;
        if (Math.abs(px) >= HEAD_HALF_WIDTH) {
            // A panel already outside the side of the head can keep passing around that side.
            axis = 0;
            plane = Math.copySign(HEAD_HALF_WIDTH, px);
        } else if (below <= behind && below <= above) {
            // Fit the short warp link while following the lip, rather than flattening successive
            // rows onto the same horizontal plane. A correction toward the parent may move Z inward.
            projectLowerLip(v, i, px, py, pz, bottom, floor, span);
            return;
        } else if (above <= behind) {
            axis = 1; plane = top;
        } else {
            axis = 2; plane = HEAD_REAR;
        }
        float anchor = v[parent + axis];
        float along = Math.max(-span, Math.min(span, plane - anchor));
        v[i + axis] = along == plane - anchor ? plane : anchor + along;
        int a = (axis + 1) % 3, b = (axis + 2) % 3;
        float da = v[i + a] - v[parent + a], db = v[i + b] - v[parent + b];
        float tangent = da * da + db * db;
        float limit = Math.max(0, span * span - along * along);
        if (tangent > limit) {
            float ratio = (float) Math.sqrt(limit / tangent);
            v[i + a] = v[parent + a] + ratio * da;
            v[i + b] = v[parent + b] + ratio * db;
        }
    }

    static float lowerLip(float rear, float bottom) {
        float t = Math.max(0, Math.min(1, (rear - LIP_START) * LIP_SCALE));
        return bottom - LIP_DEPTH * (1 - (float) Math.sqrt(1 - t * t));
    }

    private static void projectLowerLip(float[] v, int i, float px, float py, float pz, float bottom, float floor, float span) {
        float dx = v[i] - px, dz = v[i + 2] - pz;
        float dy = floor - py;
        float limit = span * span, tangent = dx * dx + dz * dz;
        if (dy * dy + tangent <= limit) {
            v[i + 1] = floor;
            return;
        }
        // Follow the same XZ direction. A few bounded iterations suffice only on the saturated contact path.
        float best = 0, far = 1;
        for (int step = 0; step < 9; step++) {
            float f = (best + far) * .5F;
            float height = lowerLip(pz + f * dz, bottom) - py;
            if (height * height + f * f * tangent <= limit) best = f;
            else far = f;
        }
        v[i] = px + best * dx;
        v[i + 2] = pz + best * dz;
        v[i + 1] = py + Math.max(-span, Math.min(span, lowerLip(v[i + 2], bottom) - py));
    }

    /** Vertical free stream recovers gradually outside the near-body wake. */
    static float wakeOpening(float gap) {
        float t = Math.max(0, Math.min(1, (gap - .18F) * (1 / .49F)));
        return t * t * (3 - 2 * t);
    }
}
