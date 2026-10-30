package com.settlr.ui;

import com.settlr.Group;

import javafx.stage.Stage;

/** What a page can ask the app shell to do. */
public interface Nav {

    void show(String page);

    void openTrip(Group group);

    void settleUp(int groupId);

    void toast(String message);

    /** A toast with an action button (e.g. "Undo"). */
    void toast(String message, String actionLabel, Runnable action);

    void applyTheme(boolean dark);

    Stage stage();
}