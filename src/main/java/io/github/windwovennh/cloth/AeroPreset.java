package io.github.windwovennh.cloth;

/** Compact surrogate coefficients. Presets are fit offline; only these scalars ship. */
final class AeroPreset {
    static final AeroPreset DETAIL = new AeroPreset(
        1.8110312F, 0.043184005F, 1.01F, 0.54F, 0.10073088F, 0.2788F, 0.20146097F, 3.7F, 2.1F, 0.40580326F);
    static final AeroPreset ECONOMY = new AeroPreset(
        1.7579577F, 0.070859835F, 1.01F, 0.55F, 0.075F, 0.32F, 0.211F, 4.2F, 2.1F, 0.85F);
    final float normal, tangent, inertia, follow, verticalAir, openAir, wake, damping, bend, yaw;

    AeroPreset(float normal, float tangent, float inertia, float follow, float verticalAir, float openAir, float wake,
        float damping, float bend, float yaw) {
        this.normal = normal;
        this.tangent = tangent;
        this.inertia = inertia;
        this.follow = follow;
        this.verticalAir = verticalAir;
        this.openAir = openAir;
        this.wake = wake;
        this.damping = damping;
        this.bend = bend;
        this.yaw = yaw;
    }
}
