package com.logiopt.desktop.tools;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Renders every desktop screen with {@link SampleData} off-screen and writes one PNG per screen into
 * sub-folders (one per window size, see {@link ScreenGallery#sizes()}) of the folder given as the first
 * argument. The optional second argument {@code --scale=1.5} renders at a display scale such as
 * Windows 150% (image pixels = logical pixels × scale). No window is shown and no database is used.
 * It needs a display for the JavaFX toolkit: without one it prints a clear message, writes nothing
 * and exits with code 2.
 * {@code --d4} renders only the phase D4 screens, at this laptop's size and the minimum window.
 * <p>Usage: {@code java ... com.logiopt.desktop.tools.ScreenshotTool <folder> [--scale=1.5] [--d4]}</p>
 */
public final class ScreenshotTool {

    /** Exit code: done. */
    public static final int OK = 0;
    /** Exit code: wrong arguments or a screen could not be written. */
    public static final int FAILED = 1;
    /** Exit code: no display, so the JavaFX toolkit could not start. */
    public static final int NO_DISPLAY = 2;

    private static final Duration TOOLKIT_TIMEOUT = Duration.ofSeconds(15);
    /** {@code --scale=1}, {@code --scale=1.5}, {@code --scale=2.25} ... up to 4. */
    private static final String SCALE_ARGUMENT = "--scale=[1-4](\\.[0-9]{1,2})?";
    /** Renders only the phase D4 screens (timeline, notifications, reports, administrator) at their two sizes. */
    static final String D4_ARGUMENT = "--d4";

    private ScreenshotTool() {
    }

    /**
     * @param args output folder and optional --scale=N
     */
    public static void main(String[] args) {
        int code = run(args, () -> FxToolkit.start(TOOLKIT_TIMEOUT), System.out, System.err);
        if (code != NO_DISPLAY) {
            Platform.exit();
        }
        System.exit(code);
    }

    /**
     * @param args    output folder and optional --scale=N
     * @param toolkit starts the JavaFX toolkit, false if there is no display
     * @param out     progress output
     * @param err     error output
     * @return exit code
     */
    static int run(String[] args, BooleanSupplier toolkit, PrintStream out, PrintStream err) {
        double scale = 1;
        boolean d4 = false;
        boolean valid = args.length >= 1 && args.length <= 3;
        for (int i = 1; valid && i < args.length; i++) {
            if (args[i].matches(SCALE_ARGUMENT) && scale == 1) {
                scale = Double.parseDouble(args[i].substring("--scale=".length()));
            } else if (D4_ARGUMENT.equals(args[i]) && !d4) {
                d4 = true;
            } else {
                valid = false;
            }
        }
        if (!valid) {
            err.println("Usage: ScreenshotTool <output folder> [--scale=1.5] [--d4]");
            return FAILED;
        }
        if (!toolkit.getAsBoolean()) {
            err.println("ScreenshotTool needs a display: the JavaFX toolkit could not start ("
                    + FxToolkit.failure() + "). No screenshots were written.");
            return NO_DISPLAY;
        }
        Path folder = Paths.get(args[0]).toAbsolutePath().normalize();
        try {
            Files.createDirectories(folder);
            double factor = scale;
            for (ScreenGallery.Shot shot : FxToolkit.call(d4 ? ScreenGallery::d4Shots : ScreenGallery::shots)) {
                WritableImage image = FxToolkit.call(() -> render(shot, factor));
                Path file = folder.resolve(shot.name() + ".png");
                Files.createDirectories(file.getParent());
                PngWriter.write(image, file);
                out.println("Wrote " + file + " (" + (int) image.getWidth() + "x" + (int) image.getHeight() + ")");
            }
        } catch (Exception e) {
            err.println("ScreenshotTool failed: " + e);
            return FAILED;
        }
        out.println("Screenshots written to " + folder);
        return OK;
    }

    /** Lays out the screen in an off-screen scene and takes its snapshot (JavaFX thread). */
    static WritableImage render(ScreenGallery.Shot shot, double scale) {
        Parent root = shot.build().get();
        Scene scene = new Scene(root, shot.width(), shot.height());
        root.applyCss();
        root.layout();
        root.applyCss();
        root.layout();
        if (scale == 1) {
            return scene.snapshot(null);
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(scale, scale));
        return root.snapshot(params, null);
    }
}
