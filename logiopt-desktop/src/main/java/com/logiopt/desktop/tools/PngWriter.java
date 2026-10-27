package com.logiopt.desktop.tools;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritablePixelFormat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Writes a JavaFX image as PNG with the JDK's image writer (no javafx-swing needed). */
final class PngWriter {

    private PngWriter() {
    }

    /**
     * @param image snapshot
     * @param file  target .png file
     * @throws IOException if it cannot be written
     */
    static void write(Image image, Path file) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width * height];
        PixelReader reader = image.getPixelReader();
        reader.getPixels(0, 0, width, height, WritablePixelFormat.getIntArgbInstance(), argb, 0, width);
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, argb, 0, width);
        if (!ImageIO.write(buffered, "png", file.toFile())) {
            throw new IOException("No PNG writer is available");
        }
    }
}
