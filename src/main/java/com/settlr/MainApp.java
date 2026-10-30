package com.settlr;

import java.util.ArrayList;
import java.util.List;

import com.settlr.db.Database;
import com.settlr.db.Extras;
import com.settlr.db.GroupExpenses;
import com.settlr.ui.AnalyticsPage;
import com.settlr.ui.BudgetsPage;
import com.settlr.ui.DashboardPage;
import com.settlr.ui.Nav;
import com.settlr.ui.PersonalPage;
import com.settlr.ui.SettingsPage;
import com.settlr.ui.SettlementsPage;
import com.settlr.ui.TripsPage;
import com.settlr.ui.Ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

/** App shell: sidebar, page switching, toasts and theme. Each page lives in com.settlr.ui. */
public class MainApp extends Application implements Nav {

    private static final String[][] PAGES = {
            {"Dashboard", "▦"}, {"Personal Expenses", "₹"}, {"My Trips", "✈"},
            {"Settlements", "⇄"}, {"Budgets", "◎"}, {"Analytics", "◔"}, {"Settings", "⚙"}
    };

    private final BorderPane root = new BorderPane();
    private final VBox sidebar = new VBox(6);
    private final VBox content = new VBox(20);
    private final VBox toastHost = new VBox(8);
    private final List<Button> navButtons = new ArrayList<>();

    private final DashboardPage dashboard = new DashboardPage(this);
    private final PersonalPage personal = new PersonalPage(this);
    private final TripsPage trips = new TripsPage(this);
    private final SettlementsPage settlements = new SettlementsPage(this);
    private final BudgetsPage budgets = new BudgetsPage(this);
    private final AnalyticsPage analytics = new AnalyticsPage();
    private final SettingsPage settings = new SettingsPage(this);

    private ScrollPane pageScroll;
    private Scene scene;
    private Stage stage;
    private Integer settleTarget = null;

    // ================= START =================

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        for (String weight : new String[] {"Regular", "Medium", "SemiBold", "Bold"}) {
            Font.loadFont(getClass().getResourceAsStream("/com/settlr/fonts/Poppins-" + weight + ".ttf"), 13);
        }

        Database.createTable();
        Extras.init();
        Ui.setDark("dark".equals(Extras.getSetting("theme", "light")));
        int posted = Extras.postDueRecurring();

        root.getStyleClass().add("app-root");
        root.setLeft(buildSidebar());

        content.getStyleClass().add("page-content");
        pageScroll = new ScrollPane(content);
        pageScroll.setFitToWidth(true);
        pageScroll.getStyleClass().add("page-scroll");
        root.setCenter(pageScroll);

        toastHost.setPadding(new Insets(24));
        toastHost.setAlignment(Pos.BOTTOM_RIGHT);
        toastHost.setPickOnBounds(false);
        toastHost.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane shell = new StackPane(root, toastHost);
        StackPane.setAlignment(toastHost, Pos.BOTTOM_RIGHT);

        scene = new Scene(shell, 1280, 820);
        scene.getStylesheets().setAll(Ui.sheets());

        show("Dashboard");

        stage.setTitle("Settlr — Expense Manager");
        stage.setMinWidth(980);
        stage.setMinHeight(660);
        stage.setScene(scene);
        stage.show();

        if (posted > 0) {
            toast(posted + " recurring expense(s) added for this month");
        }
    }

    // ================= NAV =================

    @Override
    public void show(String page) {
        resetContent(page);
        switch (page) {
            case "Personal Expenses" -> personal.render(content);
            case "My Trips" -> trips.renderList(content);
            case "Settlements" -> {
                Integer target = settleTarget;
                settleTarget = null;
                settlements.render(content, target);
            }
            case "Budgets" -> budgets.render(content);
            case "Analytics" -> analytics.render(content);
            case "Settings" -> settings.render(content);
            default -> dashboard.render(content);
        }
    }

    @Override
    public void openTrip(Group group) {
        resetContent("My Trips");
        trips.renderDetail(content, group);
    }

    @Override
    public void expenseForm(Group group, GroupExpenses.Expense existing) {
        resetContent("My Trips");
        trips.renderExpenseForm(content, group, existing);
    }

    @Override
    public void settleUp(int groupId) {
        settleTarget = groupId;
        show("Settlements");
    }

    @Override
    public Stage stage() {
        return stage;
    }

    private void resetContent(String activeNav) {
        for (Button b : navButtons) {
            b.getStyleClass().remove("nav-selected");
            if (activeNav.equals(b.getUserData())) {
                b.getStyleClass().add("nav-selected");
            }
        }
        content.getChildren().clear();
        pageScroll.setVvalue(0);
    }

    // ================= THEME =================

    @Override
    public void applyTheme(boolean dark) {
        Ui.setDark(dark);
        Extras.setSetting("theme", dark ? "dark" : "light");
        scene.getStylesheets().setAll(Ui.sheets());
    }

    // ================= TOASTS =================

    @Override
    public void toast(String message) {
        toast(message, null, null);
    }

    @Override
    public void toast(String message, String actionLabel, Runnable action) {
        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        HBox box = new HBox(14, text);
        box.getStyleClass().add("toast");
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Region.USE_PREF_SIZE);

        PauseTransition timer = new PauseTransition(Duration.seconds(actionLabel == null ? 2.8 : 6));
        timer.setOnFinished(e -> {
            FadeTransition out = new FadeTransition(Duration.millis(220), box);
            out.setToValue(0);
            out.setOnFinished(ev -> toastHost.getChildren().remove(box));
            out.play();
        });

        if (actionLabel != null && action != null) {
            Button button = new Button(actionLabel);
            button.getStyleClass().add("toast-action");
            button.setOnAction(e -> {
                timer.stop();
                toastHost.getChildren().remove(box);
                action.run();
            });
            box.getChildren().add(button);
        }

        box.setOpacity(0);
        toastHost.getChildren().add(box);
        FadeTransition in = new FadeTransition(Duration.millis(180), box);
        in.setToValue(1);
        in.play();
        timer.play();
    }

    // ================= SIDEBAR =================

    private VBox buildSidebar() {
        sidebar.getStyleClass().add("sidebar");

        Label name = new Label("Settlr");
        name.getStyleClass().add("brand-name");
        Label caption = new Label("EXPENSE MANAGER");
        caption.getStyleClass().add("brand-caption");

        HBox brand = new HBox(12, buildLogo(), new VBox(1, name, caption));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand-row");
        sidebar.getChildren().add(brand);

        Label menu = new Label("MENU");
        menu.getStyleClass().add("sidebar-section");
        sidebar.getChildren().add(menu);

        for (String[] page : PAGES) {
            Button button = new Button(page[1] + "   " + page[0]);
            button.setUserData(page[0]);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.getStyleClass().add("nav-item");
            button.setOnAction(e -> show(page[0]));
            navButtons.add(button);
            sidebar.getChildren().add(button);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label footer = new Label("Manage money, together.");
        footer.getStyleClass().add("sidebar-foot-copy");
        sidebar.getChildren().addAll(spacer, footer);
        return sidebar;
    }

    /** Two overlapping circles: the shared part (the lens) is the bill you split. */
    private Node buildLogo() {
        Circle left = new Circle(16, 22, 10);
        Circle right = new Circle(28, 22, 10);
        Shape lens = Shape.intersect(left, right);

        left.setFill(Color.web("#FFFFFF", 0.40));
        right.setFill(Color.web("#FFFFFF", 0.40));
        lens.setFill(Color.WHITE);

        Pane marks = new Pane(left, right, lens);
        marks.setMinSize(44, 44);
        marks.setPrefSize(44, 44);
        marks.setMaxSize(44, 44);
        marks.setMouseTransparent(true);

        StackPane tile = new StackPane(marks);
        tile.getStyleClass().add("brand-logo");
        return tile;
    }

    public static void main(String[] args) {
        launch(args);
    }
}