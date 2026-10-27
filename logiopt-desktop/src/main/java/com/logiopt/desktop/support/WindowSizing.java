package com.logiopt.desktop.support;

/**
 * Chooses the main window's size and position from the usable area of the screen (the screen minus
 * the taskbar, in JavaFX logical pixels, so Windows display scaling is already applied): about 90%
 * of the usable width and height, never larger than the usable area, centred, and at least
 * {@link #MIN_WIDTH}×{@link #MIN_HEIGHT} where the screen allows. Pure function; no JavaFX types.
 */
public final class WindowSizing {

    /** Share of the usable area the window takes by default. */
    public static final double FRACTION = 0.9;
    /** Smallest window the layout is designed for (fits 1920×1080 at 150% scale: 1280×672 usable). */
    public static final double MIN_WIDTH = 960;
    /** Smallest window height the layout is designed for. */
    public static final double MIN_HEIGHT = 600;

    /**
     * A rectangle in logical pixels.
     *
     * @param x      left
     * @param y      top
     * @param width  width
     * @param height height
     */
    public record Bounds(double x, double y, double width, double height) {
    }

    /**
     * Where the window goes and how small it may get.
     *
     * @param x         left of the window
     * @param y         top of the window
     * @param width     window width
     * @param height    window height
     * @param minWidth  minimum width (never more than the usable width)
     * @param minHeight minimum height (never more than the usable height)
     */
    public record Placement(double x, double y, double width, double height, double minWidth, double minHeight) {
    }

    private WindowSizing() {
    }

    /**
     * @param usable usable area of the primary screen (JavaFX Screen.getVisualBounds())
     * @return window size, position and minimum size
     */
    public static Placement place(Bounds usable) {
        if (usable == null || usable.width() <= 0 || usable.height() <= 0) {
            throw new IllegalArgumentException("Usable screen bounds are required");
        }
        double minWidth = Math.min(MIN_WIDTH, usable.width());
        double minHeight = Math.min(MIN_HEIGHT, usable.height());
        double width = clamp(Math.floor(usable.width() * FRACTION), minWidth, usable.width());
        double height = clamp(Math.floor(usable.height() * FRACTION), minHeight, usable.height());
        double x = usable.x() + Math.floor((usable.width() - width) / 2);
        double y = usable.y() + Math.floor((usable.height() - height) / 2);
        return new Placement(x, y, width, height, minWidth, minHeight);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
}
