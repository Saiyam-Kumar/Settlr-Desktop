package com.settlr;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.settlr.SettlementCalculator.Transfer;
import com.settlr.db.Database;
import com.settlr.db.Stats;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static final String CSS_PATH = "/com/settlr/ui/settlr.css";

    private static final List<String> CATEGORIES = List.of(
            "Food", "Travel", "Shopping", "Bills", "Entertainment", "Education", "Other");

    private static final Map<String, String> CATEGORY_COLORS = Map.of(
            "Food", "#F59E0B", "Travel", "#0EA5E9", "Shopping", "#EC4899",
            "Bills", "#EF4444", "Entertainment", "#8B5CF6",
            "Education", "#10B981", "Other", "#64748B");

    private static final String[][] PAGES = {
            {"Dashboard", "▦"}, {"Personal Expenses", "₹"}, {"My Trips", "✈"},
            {"Settlements", "⇄"}, {"Analytics", "◔"}
    };

    private final BorderPane root = new BorderPane();
    private final VBox sidebar = new VBox(6);
    private final VBox content = new VBox(20);
    private final List<Button> navButtons = new ArrayList<>();
    private ScrollPane pageScroll;

    private Integer settleGroupId = null; // lets "Settle up" jump straight to one trip

    // ================= START =================

    @Override
    public void start(Stage stage) {
        Database.createTable();

        for (String weight : new String[] {"Regular", "Medium", "SemiBold", "Bold"}) {
            javafx.scene.text.Font.loadFont(
                    getClass().getResourceAsStream("/com/settlr/fonts/Poppins-" + weight + ".ttf"), 13);
        }

        root.getStyleClass().add("app-root");
        root.setLeft(buildSidebar());

        content.getStyleClass().add("page-content");
        pageScroll = new ScrollPane(content);
        pageScroll.setFitToWidth(true);
        pageScroll.getStyleClass().add("page-scroll");
        root.setCenter(pageScroll);

        showPage("Dashboard");

        Scene scene = new Scene(root, 1280, 820);
        scene.getStylesheets().add(css());
        stage.setTitle("Settlr — Expense Manager");
        stage.setMinWidth(980);
        stage.setMinHeight(660);
        stage.setScene(scene);
        stage.show();
    }

    // ================= SIDEBAR / NAV =================

    private VBox buildSidebar() {
        sidebar.getStyleClass().add("sidebar");

        Node logo = buildLogo();

        Label name = new Label("Settlr");
        name.getStyleClass().add("brand-name");
        Label caption = new Label("EXPENSE MANAGER");
        caption.getStyleClass().add("brand-caption");

        HBox brand = new HBox(12, logo, new VBox(1, name, caption));
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
            button.setOnAction(e -> showPage(page[0]));
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
        javafx.scene.shape.Circle left = new javafx.scene.shape.Circle(16, 22, 10);
        javafx.scene.shape.Circle right = new javafx.scene.shape.Circle(28, 22, 10);
        javafx.scene.shape.Shape lens = javafx.scene.shape.Shape.intersect(left, right);

        left.setFill(javafx.scene.paint.Color.web("#FFFFFF", 0.40));
        right.setFill(javafx.scene.paint.Color.web("#FFFFFF", 0.40));
        lens.setFill(javafx.scene.paint.Color.WHITE);

        javafx.scene.layout.Pane marks = new javafx.scene.layout.Pane(left, right, lens);
        marks.setMinSize(44, 44);
        marks.setPrefSize(44, 44);
        marks.setMaxSize(44, 44);
        marks.setMouseTransparent(true);

        StackPane tile = new StackPane(marks);
        tile.getStyleClass().add("brand-logo");
        return tile;
    }

    private void setActiveNav(String page) {
        for (Button b : navButtons) {
            b.getStyleClass().remove("nav-selected");
            if (page.equals(b.getUserData())) {
                b.getStyleClass().add("nav-selected");
            }
        }
    }

    private void resetContent(String activeNav) {
        setActiveNav(activeNav);
        content.getChildren().clear();
        pageScroll.setVvalue(0);
    }

    private void showPage(String page) {
        resetContent(page);
        switch (page) {
            case "Personal Expenses" -> showPersonalExpenses();
            case "My Trips" -> showTrips();
            case "Settlements" -> showSettlements();
            case "Analytics" -> showAnalytics();
            default -> showDashboard();
        }
    }

    private void pageHeader(String title, String subtitle, Node... actions) {
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

    // ================= DASHBOARD =================

    private void showDashboard() {
        Button add = primaryButton("＋  Add expense");
        add.setOnAction(e -> showPage("Personal Expenses"));
        pageHeader("Dashboard", "Your personal spending and shared trips at a glance.", add);

        YearMonth now = YearMonth.now();
        double monthTotal = Database.getPersonalTotal(
                now.atDay(1).toString(), now.atEndOfMonth().toString(), null);
        double allTotal = Database.getPersonalTotal(null, null, null);

        List<Group> groups = Database.getGroups();
        double tripTotal = 0;
        double outstanding = 0;
        for (Group g : groups) {
            tripTotal += Stats.groupTotal(g.getId());
            for (Transfer t : SettlementCalculator.suggest(Database.getGroupBalances(g.getId()))) {
                outstanding += t.amount();
            }
        }

        HBox stats = new HBox(16,
                statCard("This month", money(monthTotal), "Personal spending"),
                statCard("All-time personal", money(allTotal), "Every expense logged"),
                statCard("Trip expenses", money(tripTotal), groups.size() + " trip(s)"),
                statCard("To be settled", money(outstanding), "Across all trips"));
        content.getChildren().add(stats);

        // Trend + recent
        VBox trend = new VBox(8, sectionTitle("Spending — last 6 months"), monthlyChart(6));
        trend.getStyleClass().add("card");
        HBox.setHgrow(trend, Priority.ALWAYS);
        trend.setMinWidth(380);

        VBox recent = new VBox(8, sectionTitle("Recent expenses"));
        recent.getStyleClass().add("card");
        recent.setPrefWidth(380);
        List<String[]> rows = Database.getPersonalExpenses(null, null, null);
        if (rows.isEmpty()) {
            recent.getChildren().add(smallText("No personal expenses yet."));
        }
        for (int i = 0; i < Math.min(5, rows.size()); i++) {
            String[] r = rows.get(i);
            recent.getChildren().add(infoRow(r[1], r[3] + " • " + r[4], money(Double.parseDouble(r[2]))));
        }

        HBox lower = new HBox(16, trend, recent);
        content.getChildren().add(lower);

        Button trips = secondaryButton("Open my trips");
        trips.setOnAction(e -> showPage("My Trips"));
        Button settle = secondaryButton("View settlements");
        settle.setOnAction(e -> showPage("Settlements"));
        content.getChildren().add(new HBox(10, trips, settle));
    }

    private BarChart<String, Number> monthlyChart(int months) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(true);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(20);
        chart.setPrefHeight(280);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        DateTimeFormatter label = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);
        for (Map.Entry<String, Double> e : Stats.personalByMonth(months).entrySet()) {
            series.getData().add(new XYChart.Data<>(YearMonth.parse(e.getKey()).format(label), e.getValue()));
        }
        chart.getData().add(series);
        return chart;
    }

    // ================= PERSONAL EXPENSES =================

    private void showPersonalExpenses() {
        pageHeader("Personal Expenses", "Track, search and manage everything you spend.");

        // ---- Add form
        TextField nameField = new TextField();
        nameField.setPromptText("Expense name");
        nameField.setPrefWidth(220);
        TextField amountField = new TextField();
        amountField.setPromptText("Amount (₹)");
        amountField.setPrefWidth(130);
        ComboBox<String> categoryField = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        categoryField.setValue("Food");
        DatePicker dateField = new DatePicker(LocalDate.now());
        dateField.setPrefWidth(150);
        Button save = primaryButton("Save expense");

        FlowPane form = new FlowPane(10, 10, nameField, amountField, categoryField, dateField, save);
        form.setAlignment(Pos.CENTER_LEFT);

        // ---- Filters
        TextField search = new TextField();
        search.setPromptText("Search by name");
        search.setPrefWidth(200);

        ComboBox<String> monthFilter = new ComboBox<>();
        monthFilter.getItems().add("All Months");
        YearMonth thisMonth = YearMonth.now();
        for (int i = 0; i < 24; i++) {
            monthFilter.getItems().add(thisMonth.minusMonths(i).toString());
        }
        monthFilter.setValue("All Months");

        ComboBox<String> categoryFilter = new ComboBox<>();
        categoryFilter.getItems().add("All Categories");
        categoryFilter.getItems().addAll(CATEGORIES);
        categoryFilter.setValue("All Categories");

        DatePicker from = new DatePicker();
        from.setPromptText("From date");
        from.setPrefWidth(140);
        DatePicker to = new DatePicker();
        to.setPromptText("To date");
        to.setPrefWidth(140);
        Button clear = secondaryButton("Clear filters");

        FlowPane filters = new FlowPane(10, 10, search, monthFilter, categoryFilter, from, to, clear);
        filters.setAlignment(Pos.CENTER_LEFT);

        // ---- Summary
        Label totalLabel = new Label();
        totalLabel.getStyleClass().add("stat-value");
        Label countLabel = smallText("");
        VBox summary = new VBox(4, smallLabel("Filtered spending"), totalLabel, countLabel);
        summary.getStyleClass().add("card");

        VBox list = new VBox(8);

        content.getChildren().addAll(
                sectionTitle("Add expense"), card(form),
                sectionTitle("Filter"), card(filters),
                summary,
                sectionTitle("Expense history"), list);

        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            String start = null;
            String end = null;
            if (from.getValue() != null || to.getValue() != null) {
                start = from.getValue() == null ? null : from.getValue().toString();
                end = to.getValue() == null ? null : to.getValue().toString();
            } else if (monthFilter.getValue() != null && !monthFilter.getValue().equals("All Months")) {
                YearMonth ym = YearMonth.parse(monthFilter.getValue());
                start = ym.atDay(1).toString();
                end = ym.atEndOfMonth().toString();
            }
            String category = categoryFilter.getValue();
            String needle = search.getText() == null ? "" : search.getText().trim().toLowerCase();

            List<String[]> rows = Database.getPersonalExpenses(start, end, category).stream()
                    .filter(r -> needle.isEmpty() || r[1].toLowerCase().contains(needle))
                    .collect(Collectors.toList());

            double total = 0;
            for (String[] r : rows) {
                total += Double.parseDouble(r[2]);
            }
            totalLabel.setText(money(total));
            countLabel.setText(rows.size() + " expense(s) match");

            list.getChildren().clear();
            if (rows.isEmpty()) {
                list.getChildren().add(emptyMessage("No expenses match these filters."));
                return;
            }

            for (String[] r : rows) {
                int id = Integer.parseInt(r[0]);
                String name = r[1];
                double amount = Double.parseDouble(r[2]);
                String cat = r[3];
                String date = r[4];

                Label title = heading(name);
                HBox meta = new HBox(8, chip(cat, CATEGORY_COLORS.getOrDefault(cat, "#64748B")), smallText(date));
                meta.setAlignment(Pos.CENTER_LEFT);
                VBox labels = new VBox(5, title, meta);
                HBox.setHgrow(labels, Priority.ALWAYS);

                Label price = new Label(money(amount));
                price.getStyleClass().add("amount");

                Button edit = smallButton("Edit");
                edit.setOnAction(e -> {
                    if (editPersonalExpense(id, name, amount, cat, date)) {
                        refresh[0].run();
                    }
                });
                Button delete = dangerSmallButton("Delete");
                delete.setOnAction(e -> {
                    if (confirm("Delete expense", "Delete \"" + name + "\"?")) {
                        if (Database.deletePersonalExpense(id)) {
                            refresh[0].run();
                        } else {
                            showMessage("Error", "Could not delete this expense.");
                        }
                    }
                });

                HBox line = new HBox(10, labels, price, edit, delete);
                line.setAlignment(Pos.CENTER_LEFT);
                line.getStyleClass().add("row-card");
                list.getChildren().add(line);
            }
        };

        save.setOnAction(e -> {
            String name = nameField.getText().trim();
            Double amount = Money.parse(amountField.getText());
            if (name.isEmpty() || amount == null || dateField.getValue() == null) {
                showMessage("Invalid input", "Enter a name, a positive amount (max 2 decimals) and a date.");
                return;
            }
            if (Database.addPersonalExpense(name, amount, categoryField.getValue(),
                    dateField.getValue().toString())) {
                nameField.clear();
                amountField.clear();
                refresh[0].run();
            } else {
                showMessage("Error", "Could not save the expense.");
            }
        });

        search.textProperty().addListener((o, a, b) -> refresh[0].run());
        monthFilter.setOnAction(e -> refresh[0].run());
        categoryFilter.setOnAction(e -> refresh[0].run());
        from.setOnAction(e -> refresh[0].run());
        to.setOnAction(e -> refresh[0].run());
        clear.setOnAction(e -> {
            search.clear();
            from.setValue(null);
            to.setValue(null);
            monthFilter.setValue("All Months");
            categoryFilter.setValue("All Categories");
            refresh[0].run();
        });

        refresh[0].run();
    }

    private boolean editPersonalExpense(int id, String oldName, double oldAmount,
                                        String oldCategory, String oldDate) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit expense");
        dialog.setHeaderText("Update expense details");
        ButtonType saveType = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(css());

        TextField name = new TextField(oldName);
        TextField amount = new TextField(String.valueOf(oldAmount));
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        category.setValue(oldCategory);
        category.setMaxWidth(Double.MAX_VALUE);
        DatePicker date = new DatePicker();
        try {
            date.setValue(LocalDate.parse(oldDate));
        } catch (RuntimeException ex) {
            date.setValue(LocalDate.now());
        }
        date.setMaxWidth(Double.MAX_VALUE);
        Label error = new Label();
        error.getStyleClass().add("error-label");

        GridPane grid = formGrid();
        grid.addRow(0, new Label("Name"), name);
        grid.addRow(1, new Label("Amount (₹)"), amount);
        grid.addRow(2, new Label("Category"), category);
        grid.addRow(3, new Label("Date"), date);
        grid.add(error, 0, 4, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            if (name.getText().isBlank() || Money.parse(amount.getText()) == null || date.getValue() == null) {
                error.setText("Enter a name, a positive amount (max 2 decimals) and a date.");
                ev.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveType) {
            boolean ok = Database.updatePersonalExpense(id, name.getText(), Money.parse(amount.getText()),
                    category.getValue(), date.getValue().toString());
            if (!ok) {
                showMessage("Error", "Could not update this expense.");
            }
            return ok;
        }
        return false;
    }

    // ================= TRIPS =================

    private void showTrips() {
        pageHeader("My Trips", "Create groups, manage members and track shared expenses.");

        TextField groupName = new TextField();
        groupName.setPromptText("Trip or group name, e.g. Goa 2026");
        groupName.setPrefWidth(320);
        Button create = primaryButton("Create trip");
        HBox createRow = new HBox(10, groupName, create);
        createRow.setAlignment(Pos.CENTER_LEFT);

        Runnable doCreate = () -> {
            if (groupName.getText().isBlank()) {
                showMessage("Missing name", "Enter a trip name.");
                return;
            }
            int id = Database.createGroup(groupName.getText().trim());
            if (id > 0) {
                showTripDetail(new Group(id, groupName.getText().trim()));
            } else {
                showMessage("Error", "Could not create the trip.");
            }
        };
        create.setOnAction(e -> doCreate.run());
        groupName.setOnAction(e -> doCreate.run());

        content.getChildren().addAll(sectionTitle("Create a trip"), card(createRow), sectionTitle("Your trips"));

        List<Group> groups = Database.getGroups();
        if (groups.isEmpty()) {
            content.getChildren().add(emptyMessage("No trips yet. Create your first one above."));
            return;
        }

        FlowPane grid = new FlowPane(16, 16);
        for (Group g : groups) {
            List<Member> members = Database.getGroupMembers(g.getId());

            Label title = new Label(g.getName());
            title.getStyleClass().add("row-title");
            Label total = new Label(money(Stats.groupTotal(g.getId())));
            total.getStyleClass().add("stat-value");
            Label meta = smallText(members.size() + " member(s) • total spent");

            FlowPane chips = new FlowPane(6, 6);
            for (int i = 0; i < Math.min(5, members.size()); i++) {
                chips.getChildren().add(chip(members.get(i).getName(), null));
            }
            if (members.size() > 5) {
                chips.getChildren().add(chip("+" + (members.size() - 5), null));
            }
            if (members.isEmpty()) {
                chips.getChildren().add(smallText("No members yet"));
            }

            Button open = primaryButton("Open trip");
            open.setOnAction(e -> showTripDetail(g));

            VBox box = new VBox(10, title, total, meta, chips, open);
            box.getStyleClass().add("card");
            box.setPrefWidth(330);
            grid.getChildren().add(box);
        }
        content.getChildren().add(grid);
    }

    private void showTripDetail(Group group) {
        resetContent("My Trips");
        int gid = group.getId();

        Button back = secondaryButton("←  All trips");
        back.setOnAction(e -> showPage("My Trips"));
        Button settle = secondaryButton("Settle up →");
        settle.setOnAction(e -> {
            settleGroupId = gid;
            showPage("Settlements");
        });
        Button deleteTrip = dangerButton("Delete trip");
        deleteTrip.setOnAction(e -> {
            if (confirm("Delete trip",
                    "Delete \"" + group.getName() + "\" with all its expenses, members and settlements? This cannot be undone.")) {
                if (Stats.deleteGroup(gid)) {
                    showPage("My Trips");
                } else {
                    showMessage("Error", "Could not delete this trip.");
                }
            }
        });
        pageHeader(group.getName(), "Members, shared expenses and balances for this trip.", back, settle, deleteTrip);

        List<Member> members = Database.getGroupMembers(gid);
        List<String[]> expenses = Database.getGroupExpenses(gid);
        List<Balance> balances = Database.getGroupBalances(gid);
        double outstanding = SettlementCalculator.suggest(balances).stream().mapToDouble(Transfer::amount).sum();

        content.getChildren().add(new HBox(16,
                statCard("Total spent", money(Stats.groupTotal(gid)), "All shared expenses"),
                statCard("Expenses", String.valueOf(expenses.size()), "Recorded"),
                statCard("Members", String.valueOf(members.size()), "In this trip"),
                statCard("To be settled", money(outstanding), "Outstanding")));

        // ---- Members
        TextField memberName = new TextField();
        memberName.setPromptText("Member name");
        memberName.setPrefWidth(220);
        Button addMember = primaryButton("Add member");
        Runnable doAdd = () -> {
            if (memberName.getText().isBlank()) {
                showMessage("Missing name", "Enter a member name.");
                return;
            }
            if (Database.addMember(gid, memberName.getText())) {
                showTripDetail(group);
            } else {
                showMessage("Error", "Could not add the member.");
            }
        };
        addMember.setOnAction(e -> doAdd.run());
        memberName.setOnAction(e -> doAdd.run());

        FlowPane memberChips = new FlowPane(8, 8);
        for (Member m : members) {
            Button x = new Button("✕");
            x.getStyleClass().add("chip-remove");
            x.setOnAction(e -> {
                if (confirm("Remove member", "Remove " + m.getName() + " from this trip?")) {
                    if (Stats.deleteMember(gid, m.getId())) {
                        showTripDetail(group);
                    } else {
                        showMessage("Cannot remove",
                                m.getName() + " already has expenses or settlements in this trip, so they can't be removed.");
                    }
                }
            });
            HBox chip = new HBox(6, new Label(m.getName()), x);
            chip.getStyleClass().add("member-chip");
            memberChips.getChildren().add(chip);
        }
        if (members.isEmpty()) {
            memberChips.getChildren().add(smallText("Add at least one member to start recording expenses."));
        }

        VBox memberCard = new VBox(12, new HBox(10, memberName, addMember), memberChips);
        content.getChildren().addAll(sectionTitle("Members"), card(memberCard));

        // ---- Expenses
        Button record = primaryButton("＋  Record expense");
        record.setOnAction(e -> expenseDialog(group, null));
        HBox expHeader = new HBox(sectionTitle("Expenses"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        expHeader.getChildren().addAll(spacer, record);
        expHeader.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(expHeader);

        if (expenses.isEmpty()) {
            content.getChildren().add(emptyMessage("No expenses yet. Record the first shared cost."));
        } else {
            VBox list = new VBox(8);
            for (String[] ex : expenses) {
                int id = Integer.parseInt(ex[0]);
                String name = ex[1];
                double amount = Double.parseDouble(ex[2]);
                int sharedBy = Database.getExpenseSplits(id).size();

                VBox labels = new VBox(4, heading(name),
                        smallText("Paid by " + ex[3] + " • split between " + sharedBy));
                HBox.setHgrow(labels, Priority.ALWAYS);
                Label price = new Label(money(amount));
                price.getStyleClass().add("amount");

                Button edit = smallButton("Edit");
                edit.setOnAction(e -> expenseDialog(group, ex));
                Button del = dangerSmallButton("Delete");
                del.setOnAction(e -> {
                    if (confirm("Delete expense", "Delete \"" + name + "\"? Its shares will be removed too.")) {
                        if (Database.deleteGroupExpense(id, gid)) {
                            showTripDetail(group);
                        } else {
                            showMessage("Error", "Could not delete this expense.");
                        }
                    }
                });

                HBox row = new HBox(10, labels, price, edit, del);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("row-card");
                list.getChildren().add(row);
            }
            content.getChildren().add(list);
        }

        // ---- Balances
        content.getChildren().add(sectionTitle("Balances"));
        if (balances.isEmpty()) {
            content.getChildren().add(emptyMessage("Balances appear once the trip has members."));
        } else {
            VBox rows = new VBox(8);
            for (Balance b : balances) {
                rows.getChildren().add(balanceRow(b));
            }
            content.getChildren().add(card(rows));
        }
    }

    /** Add (existing == null) or edit (existing = DB row) a group expense. */
    private void expenseDialog(Group group, String[] existing) {
        List<Member> members = Database.getGroupMembers(group.getId());
        if (members.isEmpty()) {
            showMessage("Add members first", "Add at least one member before recording an expense.");
            return;
        }
        boolean editing = existing != null;

        Set<Integer> selectedIds = new HashSet<>();
        if (editing) {
            for (double[] s : Database.getExpenseSplits(Integer.parseInt(existing[0]))) {
                selectedIds.add((int) s[0]);
            }
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Edit expense" : "Record expense");
        dialog.setHeaderText((editing ? "Update the shared cost for " : "Split a shared cost for ") + group.getName());
        ButtonType saveType = new ButtonType(editing ? "Save changes" : "Save expense", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(css());

        TextField name = new TextField(editing ? existing[1] : "");
        name.setPromptText("e.g. Hotel, dinner, taxi");
        TextField amount = new TextField(editing ? existing[2] : "");
        amount.setPromptText("0.00");

        ComboBox<Member> payer = new ComboBox<>(FXCollections.observableArrayList(members));
        payer.setPromptText("Who paid?");
        payer.setMaxWidth(Double.MAX_VALUE);
        if (editing) {
            int payerId = Integer.parseInt(existing[4]);
            for (Member m : members) {
                if (m.getId() == payerId) {
                    payer.setValue(m);
                }
            }
        }

        VBox participantBox = new VBox(8);
        List<CheckBox> checks = new ArrayList<>();
        for (Member m : members) {
            CheckBox c = new CheckBox(m.getName());
            c.setUserData(m.getId());
            c.setSelected(!editing || selectedIds.contains(m.getId()));
            checks.add(c);
            participantBox.getChildren().add(c);
        }
        ScrollPane participantScroll = new ScrollPane(participantBox);
        participantScroll.setFitToWidth(true);
        participantScroll.setPrefViewportHeight(130);
        participantBox.setPadding(new Insets(6));

        Button toggleAll = smallButton("Select / clear all");
        toggleAll.setOnAction(e -> {
            boolean select = checks.stream().anyMatch(c -> !c.isSelected());
            checks.forEach(c -> c.setSelected(select));
        });

        Label preview = smallText("Enter an amount to preview the split.");
        preview.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("error-label");

        Runnable updatePreview = () -> {
            Double v = Money.parse(amount.getText());
            long n = checks.stream().filter(CheckBox::isSelected).count();
            if (v != null && n > 0) {
                preview.setText(n + " participant(s) • about " + money(v / n) + " each (rounded to paise).");
            } else {
                preview.setText("Enter an amount and select participants to preview the split.");
            }
        };
        amount.textProperty().addListener((o, a, b) -> updatePreview.run());
        checks.forEach(c -> c.selectedProperty().addListener((o, a, b) -> updatePreview.run()));
        updatePreview.run();

        GridPane form = formGrid();
        form.addRow(0, new Label("Expense"), name);
        form.addRow(1, new Label("Amount (₹)"), amount);
        form.addRow(2, new Label("Paid by"), payer);
        form.addRow(3, new Label("Split between"), toggleAll);
        form.add(participantScroll, 1, 4);
        form.add(preview, 0, 5, 2, 1);
        form.add(error, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(form);

        Button saveBtn = (Button) dialog.getDialogPane().lookupButton(saveType);
        saveBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            String problem = null;
            if (name.getText().isBlank()) {
                problem = "Enter an expense name.";
            } else if (Money.parse(amount.getText()) == null) {
                problem = "Enter a positive amount with at most two decimals.";
            } else if (payer.getValue() == null) {
                problem = "Choose who paid.";
            } else if (checks.stream().noneMatch(CheckBox::isSelected)) {
                problem = "Select at least one participant.";
            }
            if (problem != null) {
                error.setText(problem);
                ev.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveType) {
            return;
        }

        double value = Money.parse(amount.getText());
        List<Integer> participants = checks.stream().filter(CheckBox::isSelected)
                .map(c -> (Integer) c.getUserData()).collect(Collectors.toList());

        boolean ok = editing
                ? Stats.updateGroupExpense(Integer.parseInt(existing[0]), group.getId(), name.getText().trim(),
                        value, payer.getValue().getId(), participants)
                : Database.addGroupExpense(group.getId(), name.getText().trim(), value,
                        payer.getValue().getId(), participants);

        if (ok) {
            showTripDetail(group);
        } else {
            showMessage("Could not save expense", "Please check the details and try again.");
        }
    }

    // ================= SETTLEMENTS =================

    private void showSettlements() {
        pageHeader("Settlements", "See who owes whom and record transfers once they are paid.");

        List<Group> groups = Database.getGroups();
        if (groups.isEmpty()) {
            content.getChildren().add(emptyMessage("Create a trip and add members before using settlements."));
            return;
        }

        ComboBox<Group> picker = new ComboBox<>(FXCollections.observableArrayList(groups));
        picker.setMinWidth(260);
        Group initial = groups.get(0);
        for (Group g : groups) {
            if (settleGroupId != null && g.getId() == settleGroupId) {
                initial = g;
            }
        }
        settleGroupId = null;
        picker.setValue(initial);

        HBox pickerRow = new HBox(10, smallLabel("Trip"), picker);
        pickerRow.setAlignment(Pos.CENTER_LEFT);
        VBox body = new VBox(16);
        content.getChildren().addAll(pickerRow, body);

        Runnable refresh = () -> {
            body.getChildren().clear();
            Group group = picker.getValue();
            if (group == null) {
                return;
            }

            List<Balance> balances = Database.getGroupBalances(group.getId());
            body.getChildren().add(sectionTitle("Member balances"));
            if (balances.isEmpty()) {
                body.getChildren().add(emptyMessage("This trip has no members yet."));
            } else {
                VBox rows = new VBox(8);
                balances.forEach(b -> rows.getChildren().add(balanceRow(b)));
                body.getChildren().add(card(rows));
            }

            body.getChildren().add(sectionTitle("Outstanding transfers"));
            List<Transfer> suggestions = SettlementCalculator.suggest(balances);
            if (suggestions.isEmpty()) {
                body.getChildren().add(emptyMessage("Everyone is settled up for this trip. 🎉"));
            } else {
                VBox rows = new VBox(8);
                for (Transfer t : suggestions) {
                    VBox details = new VBox(4, heading(t.fromName() + "  →  " + t.toName()),
                            smallText("Suggested transfer"));
                    HBox.setHgrow(details, Priority.ALWAYS);
                    Label amt = new Label(money(t.amount()));
                    amt.getStyleClass().add("amount");

                    Button paid = primaryButton("Mark as paid");
                    paid.setOnAction(e -> {
                        if (confirm("Record transfer",
                                t.fromName() + " paid " + t.toName() + " " + money(t.amount()) + "?")) {
                            if (Database.recordSettlement(group.getId(), t.fromId(), t.toId(), t.amount())) {
                                settleGroupId = group.getId();
                                showPage("Settlements");
                            } else {
                                showMessage("Error", "Could not record this transfer.");
                            }
                        }
                    });
                    HBox row = new HBox(10, details, amt, paid);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.getStyleClass().add("row-card");
                    rows.getChildren().add(row);
                }
                body.getChildren().add(rows);
            }

            body.getChildren().add(sectionTitle("Completed transfers"));
            List<Settlement> history = Database.getSettlements(group.getId());
            if (history.isEmpty()) {
                body.getChildren().add(emptyMessage("No completed transfers recorded yet."));
            } else {
                VBox rows = new VBox(8);
                for (Settlement s : history) {
                    rows.getChildren().add(infoRow(s.getFromName() + "  →  " + s.getToName(),
                            s.getSettledAt().toLocalDate() + " • Completed", money(s.getAmount())));
                }
                body.getChildren().add(card(rows));
            }
        };

        picker.setOnAction(e -> refresh.run());
        refresh.run();
    }

    // ================= ANALYTICS =================

    private void showAnalytics() {
        pageHeader("Analytics", "Understand where your personal money goes.");

        ComboBox<String> range = new ComboBox<>(FXCollections.observableArrayList(
                "All time", "This month", "Last 3 months", "This year"));
        range.setValue("All time");
        content.getChildren().add(new HBox(10, smallLabel("Period"), range));

        VBox body = new VBox(18);
        content.getChildren().add(body);

        Runnable refresh = () -> {
            body.getChildren().clear();
            YearMonth now = YearMonth.now();
            String start = null;
            String end = null;
            switch (range.getValue()) {
                case "This month" -> {
                    start = now.atDay(1).toString();
                    end = now.atEndOfMonth().toString();
                }
                case "Last 3 months" -> start = now.minusMonths(2).atDay(1).toString();
                case "This year" -> start = LocalDate.of(now.getYear(), 1, 1).toString();
                default -> {
                }
            }

            Map<String, Double> totals = Stats.personalByCategory(start, end);
            double total = totals.values().stream().mapToDouble(Double::doubleValue).sum();

            if (totals.isEmpty()) {
                body.getChildren().add(emptyMessage("No personal expenses in this period."));
                return;
            }

            String topName = totals.keySet().iterator().next();
            body.getChildren().add(new HBox(16,
                    statCard("Total spending", money(total), range.getValue()),
                    statCard("Top category", topName, money(totals.get(topName))),
                    statCard("Categories used", String.valueOf(totals.size()), "In this period")));

            PieChart pie = new PieChart();
            pie.setLegendVisible(true);
            pie.setLabelsVisible(false);
            pie.setAnimated(false);
            pie.setPrefHeight(320);
            totals.forEach((k, v) -> pie.getData().add(new PieChart.Data(k, v)));
            VBox pieCard = new VBox(8, sectionTitle("Spending by category"), pie);
            pieCard.getStyleClass().add("card");
            HBox.setHgrow(pieCard, Priority.ALWAYS);
            pieCard.setMinWidth(340);

            VBox breakdown = new VBox(14, sectionTitle("Breakdown"));
            breakdown.getStyleClass().add("card");
            breakdown.setPrefWidth(420);
            for (Map.Entry<String, Double> e : totals.entrySet()) {
                double share = total == 0 ? 0 : e.getValue() / total;
                Label cat = new Label(e.getKey());
                cat.getStyleClass().add("row-title");
                Label amt = new Label(money(e.getValue()) + "  ·  " + Math.round(share * 100) + "%");
                amt.getStyleClass().add("text-muted");
                Region gap = new Region();
                HBox.setHgrow(gap, Priority.ALWAYS);
                ProgressBar bar = new ProgressBar(share);
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setPrefHeight(8);
                breakdown.getChildren().add(new VBox(6, new HBox(cat, gap, amt), bar));
            }
            body.getChildren().add(new HBox(16, pieCard, breakdown));

            VBox trend = new VBox(8, sectionTitle("Monthly trend — last 12 months"), monthlyChart(12));
            trend.getStyleClass().add("card");
            body.getChildren().add(trend);
        };

        range.setOnAction(e -> refresh.run());
        refresh.run();
    }

    // ================= UI HELPERS =================

    private HBox balanceRow(Balance b) {
        double amount = b.getBalance();
        String status = Math.abs(amount) < 0.01 ? "Settled up"
                : amount > 0 ? "Should receive " + money(amount) : "Owes " + money(-amount);

        VBox labels = new VBox(4, heading(b.getName()), smallText(status));
        HBox.setHgrow(labels, Priority.ALWAYS);

        Label value = new Label(money(amount));
        value.getStyleClass().add(Math.abs(amount) < 0.01 ? "amount"
                : amount > 0 ? "amount-positive" : "amount-negative");

        HBox row = new HBox(12, labels, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox statCard(String title, String value, String note) {
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

    private VBox card(Node node) {
        VBox box = new VBox(node);
        box.getStyleClass().add("card");
        return box;
    }

    private HBox infoRow(String title, String subtitle, String value) {
        VBox labels = new VBox(4, heading(title), smallText(subtitle));
        HBox.setHgrow(labels, Priority.ALWAYS);
        Label amount = new Label(value);
        amount.getStyleClass().add("amount");
        HBox row = new HBox(12, labels, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.setPrefWidth(460);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(100);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
        return grid;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    private Label heading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("row-title");
        return l;
    }

    private Label smallText(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("text-muted");
        return l;
    }

    private Label smallLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("stat-label");
        return l;
    }

    private Label chip(String text, String color) {
        Label l = new Label(text);
        l.getStyleClass().add("chip");
        if (color != null) {
            l.setStyle("-fx-background-color: " + color + "22; -fx-text-fill: " + color + ";");
        }
        return l;
    }

    private Label emptyMessage(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.getStyleClass().add("empty-state");
        return l;
    }

    private Button primaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-primary");
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-secondary");
        return b;
    }

    private Button dangerButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("btn-danger");
        return b;
    }

    private Button smallButton(String text) {
        Button b = secondaryButton(text);
        b.getStyleClass().add("btn-small");
        return b;
    }

    private Button dangerSmallButton(String text) {
        Button b = dangerButton(text);
        b.getStyleClass().add("btn-small");
        return b;
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(css());
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void showMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getDialogPane().getStylesheets().add(css());
        alert.showAndWait();
    }

    private String css() {
        return getClass().getResource(CSS_PATH).toExternalForm();
    }

    private String money(double amount) {
        return Money.format(amount);
    }

    public static void main(String[] args) {
        launch(args);
    }
}