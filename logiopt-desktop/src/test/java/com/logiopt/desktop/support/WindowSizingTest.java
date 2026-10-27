package com.logiopt.desktop.support;

import com.logiopt.desktop.support.WindowSizing.Bounds;
import com.logiopt.desktop.support.WindowSizing.Placement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure window-sizing function: 90% of the usable area, centred, within the usable area, minimum 960×600. */
class WindowSizingTest {

    @ParameterizedTest(name = "usable {0}x{1} -> {2}x{3}")
    @CsvSource({
        // this machine: 1920x1080 at 150% = 1280x720 logical, 1280x672 above the taskbar
        "1280, 672, 1152, 604",
        // 1366x768 at 100%, 1536x864 (1920x1080 at 125%), 1920x1080 at 100%, each minus a 48 px taskbar
        "1366, 720, 1229, 648",
        "1536, 816, 1382, 734",
        "1920, 1032, 1728, 928",
        // small screens: the minimum wins over 90%, but never beyond the usable area
        "1024, 640, 960, 600",
        "960, 600, 960, 600",
        "910, 464, 910, 464"
    })
    void ninetyPercentWithinTheUsableAreaAndAtLeastTheMinimum(double usableWidth, double usableHeight, double width,
                                                               double height) {
        Placement p = WindowSizing.place(new Bounds(0, 0, usableWidth, usableHeight));
        assertEquals(width, p.width());
        assertEquals(height, p.height());
        assertTrue(p.width() <= usableWidth && p.height() <= usableHeight, "never larger than the usable area");
        assertEquals(Math.min(960, usableWidth), p.minWidth());
        assertEquals(Math.min(600, usableHeight), p.minHeight());
        assertTrue(p.minWidth() <= p.width() && p.minHeight() <= p.height());
    }

    @Test
    void theWindowIsCentredInTheUsableAreaIncludingItsOffset() {
        Placement p = WindowSizing.place(new Bounds(1920, 40, 1280, 672));
        assertEquals(1920 + 64, p.x());
        assertEquals(40 + 34, p.y());
        assertEquals(p.x() - 1920, 1920 + 1280 - (p.x() + p.width()), 1.0, "equal margins left and right");
    }

    @Test
    void missingOrEmptyBoundsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> WindowSizing.place(null));
        assertThrows(IllegalArgumentException.class, () -> WindowSizing.place(new Bounds(0, 0, 0, 600)));
    }
}
