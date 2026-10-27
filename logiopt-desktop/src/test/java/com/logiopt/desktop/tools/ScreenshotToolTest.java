package com.logiopt.desktop.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The screenshot tool's exit codes when there is no display or the arguments are wrong; needs no display. */
class ScreenshotToolTest {

    @TempDir
    Path temp;

    @Test
    void withoutADisplayItExitsWithCode2AndWritesNothing() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path folder = temp.resolve("shots");
        int code = ScreenshotTool.run(new String[] {folder.toString()}, () -> false, new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(ScreenshotTool.NO_DISPLAY, code);
        assertEquals(2, code);
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("needs a display"), err.toString(StandardCharsets.UTF_8));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("No screenshots were written"));
        assertFalse(Files.exists(folder), "the folder is not even created");
    }

    @Test
    void wrongArgumentsExitWithCode1() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(new ByteArrayOutputStream());
        assertEquals(ScreenshotTool.FAILED, ScreenshotTool.run(new String[0], () -> true, out,
                new PrintStream(err, true, StandardCharsets.UTF_8)));
        assertEquals(ScreenshotTool.FAILED, ScreenshotTool.run(new String[] {"x", "--scale=9"}, () -> true, out,
                new PrintStream(err, true, StandardCharsets.UTF_8)));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Usage: ScreenshotTool"));
    }
}
