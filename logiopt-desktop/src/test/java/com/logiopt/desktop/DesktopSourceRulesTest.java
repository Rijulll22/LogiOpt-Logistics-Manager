package com.logiopt.desktop;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Static checks of the desktop module's rules: all UI in Java code (no FXML, CSS or JavaScript files,
 * no FXMLLoader), inline setStyle only inside Theme, and no JavaFX types in the presenter and support
 * packages, so presenters stay plain Java.
 */
class DesktopSourceRulesTest {

    private static final Path MODULE = Paths.get(System.getProperty("logiopt.moduleDir", "."));
    private static final Path MAIN = MODULE.resolve(Paths.get("src", "main", "java", "com", "logiopt", "desktop"));

    private static List<Path> files(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    @Test
    void noFxmlCssOrJavaScriptFiles() throws IOException {
        List<Path> forbidden = files(MODULE.resolve("src")).stream().filter(p -> {
            String name = p.getFileName().toString().toLowerCase();
            return name.endsWith(".fxml") || name.endsWith(".css") || name.endsWith(".js");
        }).collect(Collectors.toList());
        assertEquals(List.of(), forbidden);
        assertTrue(files(MAIN).size() > 20, "the scan sees the sources: " + MAIN.toAbsolutePath());
    }

    @Test
    void setStyleOnlyInThemeAndNoFxmlLoader() throws IOException {
        for (Path file : files(MAIN)) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!file.getFileName().toString().equals("Theme.java")) {
                assertTrue(!text.contains("setStyle(") && !text.contains("getStylesheets()"), "inline style outside Theme: " + file);
            }
            assertTrue(!text.contains("FXMLLoader") && !text.contains("javafx.fxml"), "FXML used in " + file);
            assertTrue(!text.contains("WebView") && !text.contains("javafx.scene.web"), "web content in " + file);
        }
    }

    @Test
    void viewsAndPresentersCalculateNoFigures() throws IOException {
        for (String pkg : List.of("presenter", "view", "tools")) {
            for (Path file : files(MAIN.resolve(pkg))) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String op : List.of(".multiply(", ".divide(", ".subtract(", "Math.round(", "Math.pow(")) {
                    assertTrue(!text.contains(op), op + " in " + file + ": figures come from the services");
                }
            }
        }
    }

    @Test
    void chartsAreStyledOnlyThroughTheme() throws IOException {
        for (Path file : files(MAIN.resolve("view"))) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (!file.getFileName().toString().equals("Theme.java") && text.contains("BarChart")) {
                assertTrue(text.contains("Theme.styleChart("), "chart not styled through Theme: " + file);
            }
        }
    }

    @Test
    void threadsAndTimersAreCreatedOnlyInSupport() throws IOException {
        for (String pkg : List.of("presenter", "view", "tools")) {
            for (Path file : files(MAIN.resolve(pkg))) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String forbidden : List.of("new Thread(", "Thread.sleep(", "ScheduledExecutorService", "new Timer(",
                        "Timeline(", "Executors.new")) {
                    assertTrue(!text.contains(forbidden), forbidden + " outside support: " + file);
                }
            }
        }
    }

    @Test
    void everyPageViewImplementsItsPresentersViewAndIsInTheGallery() throws IOException {
        String gallery = Files.readString(MAIN.resolve(Paths.get("tools", "ScreenGallery.java")), StandardCharsets.UTF_8);
        for (String view : List.of("NotificationsView", "ReportsView", "AdminUsersView", "AdminTrackingView",
                "BookingDetailView", "BookingListView")) {
            String text = Files.readString(MAIN.resolve(Paths.get("view", view + ".java")), StandardCharsets.UTF_8);
            assertTrue(text.contains("implements") && text.contains("Presenter.View"), view);
            assertTrue(gallery.contains("new " + view + "("), view + " is rendered by the ScreenshotTool");
        }
    }

    @Test
    void presentersAndSupportHaveNoJavaFxTypes() throws IOException {
        for (String pkg : List.of("presenter", "support")) {
            for (Path file : files(MAIN.resolve(pkg))) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertTrue(!text.contains("import javafx."), "JavaFX type in plain-Java package: " + file);
            }
        }
    }
}
