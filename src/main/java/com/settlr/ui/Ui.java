package com.settlr.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.settlr.Money;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Shared look-and-feel helpers used by every page. */
public final class Ui {

    private static final String CSS_LIGHT = "/com/settlr/ui/settlr.css";
    private static final String CSS_DARK = "/com/settlr/ui/settlr-dark.css";

    public static final List<String> CATEGORIES = List.of(
            "Food", "Travel", "Shopping", "Bills", "Entertainment", "Education", "Other");

    private static final Map<String, String> CATEGORY_COLORS = Map.of(
            "Food", "#F59E0B", "Travel", "#0EA5E9", "Shopping", "#EC4899",
            "Bills", "#EF4444", "Entertainment", "#8B5CF6",
            "Education", "#10B981", "Other", "#64748B");

    private static boolean dark = false;

    private Ui() {
    }

    // ---------- theme ----------

    public static void setDark(boolean value) {
        dark = value;
    }

    public static boolean isDark() {
        return dark;
    }

    public static List<String> sheets() {
        List<String> sheets = new ArrayList<>();
        sheets.add(Ui.class.getResource(CSS_LIGHT).toExternalForm());
        if (dark) {
            sheets.add(Ui.class.getResource(CSS_DARK).toExternalForm());
        }
        return sheets;
    }

    public static void style(DialogPane pane) {
        pane.getStylesheets().setAll(sheets());
    }

    public static String categoryColor(String category) {
        return CATEGORY_COLORS.getOrDefault(category, "#64748B");
    }

    public static String money(double amount) {
        return Money.format(amount);
    }

    // ---------- layout ----------

    public static void pageHeader(VBox content, String title, String subtitle, Node... actions) {
        Label heading = new Label(title);
        heading.getStyleClass().add("page-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("page-subtitle");

        VBox titles = new VBox(4, heading, sub);
        HBox.setHgrow(titles, Priority.ALWAYS);

        HBox row = new HBox(10, titles);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(actions);
        content.getChildren().add(row);
    }

    public static VBox card(Node node) {
        VBox box = new VBox(node);
        box.getStyleClass().add("card");
        return box;
    }

    public static VBox statCard(String title, String value, String note) {
        Label t = new Label(title);
        t.getStyleClass().add("stat-label");
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        Label n = new Label(note);
        n.getStyleClass().add("stat-note");
        VBox box = new VBox(8, t, v, n);
        box.getStyleClass().add("stat-card");
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    public static HBox infoRow(String title, String subtitle, String value) {
        VBox labels = new VBox(4, heading(title), smallText(subtitle));
        HBox.setHgrow(labels, Priority.ALWAYS);
        Label amount = new Label(value);
        amount.getStyleClass().add("amount");
        HBox row = new HBox(12, labels, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    public static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.setPrefWidth(520);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(100);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        return grid;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ---------- text ----------

    public static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    public static Label heading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("row-title");
        return l;
    }

    public static Label smallText(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("text-muted");
        return l;
    }

    public static Label smallLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("stat-label");
        return l;
    }

    public static Label chip(String text, String color) {
        Label l = new Label(text);
        l.getStyleClass().add("chip");
        if (color != null) {
            l.setStyle("-fx-background-color: " + color + "22; -fx-text-fill: " + color + ";");
        }
        return l;
    }

    public static Label emptyMessage(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add("empty-state");
        return l;
    }

    // ---------- buttons ----------

    public static Button primaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-primary");
        return b;
    }

    public static Button secondaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-secondary");
        return b;
    }

    public static Button dangerButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-danger");
        return b;
    }

    public static Button smallButton(String text) {
        Button b = secondaryButton(text);
        b.getStyleClass().add("btn-small");
        return b;
    }

    public static Button dangerSmallButton(String text) {
        Button b = dangerButton(text);
        b.getStyleClass().add("btn-small");
        return b;
    }

    // ---------- budgets ----------

    /** Category name, "spent / limit · pct", and a coloured progress bar (green, amber, red). */
    public static VBox budgetRow(String category, double spent, double limit) {
        double ratio = limit <= 0 ? 0 : spent / limit;
        String color = ratio >= 1 ? "#F04438" : ratio >= 0.8 ? "#F79009" : "#12B76A";

        Label name = heading(category);
        Label amounts = smallText(money(spent) + " of " + money(limit) + "  ·  " + Math.round(ratio * 100) + "%");
        HBox top = new HBox(name, spacer(), amounts);
        top.setAlignment(Pos.CENTER_LEFT);

        ProgressBar bar = new ProgressBar(Math.min(1, ratio));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(8);
        bar.setStyle("-fx-accent: " + color + ";");
        return new VBox(6, top, bar);
    }

    // ---------- dialogs ----------

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        style(alert.getDialogPane());
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    public static void message(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        style(alert.getDialogPane());
        alert.showAndWait();
    }
}