package com.logiopt.desktop;

import javafx.application.Application;

/**
 * Entry point of the desktop client. A separate class that does not extend Application, so the
 * client also starts when JavaFX is on the classpath rather than the module path.
 */
public final class Launcher {

    private Launcher() {
    }

    /**
     * @param args passed to JavaFX
     */
    public static void main(String[] args) {
        Application.launch(DesktopApp.class, args);
    }
}
