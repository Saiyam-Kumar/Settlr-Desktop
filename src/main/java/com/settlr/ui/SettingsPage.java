package com.settlr.ui;

import java.io.File;
import java.time.LocalDate;

import com.settlr.db.Extras;
import static com.settlr.ui.Ui.card;
import static com.settlr.ui.Ui.heading;
import static com.settlr.ui.Ui.isDark;
import static com.settlr.ui.Ui.message;
import static com.settlr.ui.Ui.pageHeader;
import static com.settlr.ui.Ui.primaryButton;
import static com.settlr.ui.Ui.secondaryButton;
import static com.settlr.ui.Ui.sectionTitle;
import static com.settlr.ui.Ui.smallText;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public final class SettingsPage {

    private final Nav nav;

    public SettingsPage(Nav nav) {
        this.nav = nav;
    }

    public void render(VBox content) {
        pageHeader(content, "Settings", "Make Settlr yours and keep your data safe.");

        // ---- profile
        TextField name = new TextField(Extras.getSetting("name", ""));
        name.setPromptText("Your name");
        name.setPrefWidth(260);
        Button saveName = primaryButton("Save");
        saveName.setOnAction(e -> {
            Extras.setSetting("name", name.getText().trim());
            nav.toast("Name saved");
        });
        HBox nameRow = new HBox(10, name, saveName);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(sectionTitle("Profile"),
                card(new VBox(10, smallText("Shown in the greeting on your dashboard."), nameRow)));

        // ---- appearance
        ComboBox<String> theme = new ComboBox<>(FXCollections.observableArrayList("Light", "Dark"));
        theme.setValue(isDark() ? "Dark" : "Light");
        theme.setOnAction(e -> nav.applyTheme("Dark".equals(theme.getValue())));
        content.getChildren().addAll(sectionTitle("Appearance"),
                card(new VBox(10, smallText("Choose how Settlr looks."), theme)));

        // ---- data
        Button backup = secondaryButton("⇩  Back up my data");
        backup.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save backup");
            chooser.setInitialFileName("settlr-backup-" + LocalDate.now() + ".db");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Settlr backup", "*.db"));
            File file = chooser.showSaveDialog(nav.stage());
            if (file == null) {
                return;
            }
            if (Extras.backupTo(file.toPath())) {
                nav.toast("Backup saved");
            } else {
                message("Backup failed", "Could not write the backup file.");
            }
        });
        content.getChildren().addAll(sectionTitle("Your data"),
                card(new VBox(10,
                        smallText("Everything is stored locally in settlr.db. A backup is a full copy you can restore by "
                                + "replacing that file while Settlr is closed."),
                        backup)));

        content.getChildren().addAll(sectionTitle("About"),
                card(new VBox(6, heading("Settlr Desktop 1.0"),
                        smallText("Java 21 · JavaFX · SQLite"),
                        smallText("Manage money, together."))));
    }
}