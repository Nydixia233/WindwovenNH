package io.github.windwovennh.client;

import io.github.windwovennh.cloth.ClothSurface;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;

/** Arithmetic adapter between the cloak-call matrix and the solver's vanilla rest frame. */
final class ModelSpace implements ClothSurface.VertexOut {
    private Tessellator batch;
    private float sp, cp, sr, cr, sy, cy, drop;

    boolean fromLegacyCall(AbstractClientPlayer p, float fraction) {
        // Derive the inverse of the fixed function cloak call from its three angles, not from GL.
        double trailX = gap(p.field_71091_bM, p.field_71094_bP, p.prevPosX, p.posX, fraction);
        double trailY = gap(p.field_71096_bN, p.field_71095_bQ, p.prevPosY, p.posY, fraction);
        double trailZ = gap(p.field_71097_bO, p.field_71085_bR, p.prevPosZ, p.posZ, fraction);
        float bodyRadians = blend(p.prevRenderYawOffset, p.renderYawOffset, fraction) * (float) Math.PI / 180.0F;
        double alongX = MathHelper.sin(bodyRadians), alongZ = -MathHelper.cos(bodyRadians);
        float walk = blend(p.prevDistanceWalkedModified, p.distanceWalkedModified, fraction);
        float sway = blend(p.prevCameraYaw, p.cameraYaw, fraction);
        float kick = MathHelper.clamp_float((float) trailY * 10, -6, 32) + MathHelper.sin(walk * 6) * 32 * sway;
        boolean sneak = p.isSneaking();
        if (sneak) kick += 25;
        float lift = 6 + Math.max(0, (float) (trailX * alongX + trailZ * alongZ) * 100) * 0.5F + kick;
        float roll = (float) (trailX * alongZ - trailZ * alongX) * 50;
        if (!Float.isFinite(lift) || !Float.isFinite(roll)) return false;
        derive(lift, roll, sneak);
        return true;
    }

    void derive(float lift, float roll, boolean sneak) {
        float pitchRadians = lift * ((float) Math.PI / 180.0F);
        float rollRadians = roll * ((float) Math.PI / 180.0F);
        sp = (float) Math.sin(pitchRadians);
        cp = (float) Math.cos(pitchRadians);
        sr = (float) Math.sin(rollRadians);
        cr = (float) Math.cos(rollRadians);
        sy = sr;
        cy = cr;
        drop = sneak ? 0.018F : 0.0F;
    }

    void outputTo(Tessellator batch) {
        this.batch = batch;
    }

    @Override
    public void point(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        float b = cp * (y + drop) - sp * z;
        float c = sp * (y + drop) + cp * z;
        float a = x * cr - b * sr;
        float yy = x * sr + b * cr;
        float nb = cp * ny - sp * nz;
        float nc = sp * ny + cp * nz;
        float na = nx * cr - nb * sr;
        float nny = nx * sr + nb * cr;
        batch.setNormal(cy * na + sy * nc, nny, -sy * na + cy * nc);
        batch.addVertexWithUV(cy * a + sy * c, yy, -sy * a + cy * c, u, v);
    }

    private static double gap(double from, double to, double before, double now, float alpha) {
        return (from + alpha * (to - from)) - (before + alpha * (now - before));
    }

    private static float blend(float from, float to, float t) {
        return from + t * (to - from);
    }
}
