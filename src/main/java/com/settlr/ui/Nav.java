package com.settlr.ui;

import com.settlr.Group;
import com.settlr.db.GroupExpenses;

import javafx.stage.Stage;

/** What a page can ask the app shell to do. */
public interface Nav {

    void show(String page);

    void openTrip(Group group);

    void settleUp(int groupId);

    /** Opens the add / edit expense form in the main window (existing == null means a new expense). */
    void expenseForm(Group group, GroupExpenses.Expense existing);

    void toast(String message);

    /** A toast with an action button (e.g. "Undo"). */
    void toast(String message, String actionLabel, Runnable action);

    void applyTheme(boolean dark);

    Stage stage();
}