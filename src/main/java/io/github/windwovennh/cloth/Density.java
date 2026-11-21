package io.github.windwovennh.cloth;

/** Grid density, not just the number of rendered faces. */
public enum Density {
    DETAIL(5, 13), ECONOMY(3, 7);

    public final int columns;
    public final int rows;
    public final int sites;

    Density(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        sites = columns * rows;
    }

    public static Density forDistance(double squared, boolean alwaysDetail) {
        if (!Double.isFinite(squared) || squared < 0) return null;
        if (alwaysDetail || squared <= 576) return DETAIL;
        return squared <= 2304 ? ECONOMY : null;
    }
}
