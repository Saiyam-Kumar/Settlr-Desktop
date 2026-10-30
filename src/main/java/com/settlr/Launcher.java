package com.settlr;

/**
 * Entry point for the packaged app (jpackage). A main class that does not extend
 * javafx.application.Application lets JavaFX start from the classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        MainApp.main(args);
    }
}