package io.github.windwovennh.cloth;

/** Air velocity relative to the player, in blocks/second in the cloth frame (X lateral, Y down, Z rear). */
public final class Flow {
    // Measured motion is unscaled so turn transport and attachment inertia remain independent of wind strength.
    float airX, airY, airZ;
    float yawStep;
    boolean submerged;
    float windStrength = 1.0F;
    float flightWake;
    CapeFrame frame;
    public float shoulder;
    public float squat;

    public void update(double dx, double dy, double dz, float headingDegrees, float turnDegrees,
        boolean submerged, boolean crouching, boolean armored) {
        update(dx, dy, dz, headingDegrees, turnDegrees, submerged, false, 1.0F, crouching, armored);
    }

    /**
     * Updates the measured player-relative air flow. The legacy overload above keeps the
     * offline cloth tests and callers that do not know about flight at the old baseline.
     */
    public void update(double dx, double dy, double dz, float headingDegrees, float turnDegrees,
        boolean submerged, boolean flying, float windStrength, boolean crouching, boolean armored) {
        double angle = Math.toRadians(headingDegrees);
        double sin = Math.sin(angle), cos = Math.cos(angle);
        airX = limit(-20 * (dx * cos + dz * sin), 26);
        airY = limit(20 * dy, 80);
        airZ = limit(20 * (dz * cos - dx * sin), 26);
        yawStep = limit(Math.toRadians(turnDegrees), (float) Math.PI);
        frame = null;
        this.submerged = submerged;
        this.windStrength = Float.isNaN(windStrength) ? 1.0F : Math.max(0.0F, Math.min(3.0F, windStrength));
        flightWake = flying && this.windStrength != 0.0F
            ? Math.min(8.0F, 2.4F + (float) Math.sqrt(airX * airX + airZ * airZ) * .32F + Math.abs(airY) * .10F)
                * this.windStrength
            : 0.0F;
        shoulder = (armored ? 0.096F : 0.021F) + (crouching ? 0.025F : 0);
        squat = crouching ? 1.0F : 0;
    }

    public void pose(CapeFrame frame) {
        this.frame = frame;
        // The actual torso pose already includes the crouch; don't tilt its contact ramp twice.
        squat = 0;
    }

    private static float limit(double value, float max) {
        return Double.isFinite(value) ? (float) Math.max(-max, Math.min(max, value)) : 0.0F;
    }
}
