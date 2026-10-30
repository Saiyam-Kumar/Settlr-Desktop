package com.settlr.ui;

import static com.settlr.ui.Ui.*;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.settlr.Balance;
import com.settlr.CsvExport;
import com.settlr.Group;
import com.settlr.Member;
import com.settlr.Money;
import com.settlr.SettlementCalculator;
import com.settlr.SettlementCalculator.Transfer;
import com.settlr.SplitCalculator;
import com.settlr.SplitCalculator.Mode;
import com.settlr.db.Database;
import com.settlr.db.GroupExpenses;
import com.settlr.db.GroupExpenses.Expense;
import com.settlr.db.Stats;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public final class TripsPage {

    private final Nav nav;

    public TripsPage(Nav nav) {
        this.nav = nav;
    }

    // ================= LIST =================

    public void renderList(VBox content) {
        pageHeader(content, "My Trips", "Create groups, manage members and track shared expenses.");

        TextField groupName = new TextField();
        groupName.setPromptText("Trip or group name, e.g. Goa 2026");
        groupName.setPrefWidth(320);
        Button create = primaryButton("Create trip");
        HBox createRow = new HBox(10, groupName, create);
        createRow.setAlignment(Pos.CENTER_LEFT);

        Runnable doCreate = () -> {
            String name = groupName.getText().trim();
            if (name.isEmpty()) {
                message("Missing name", "Enter a trip name.");
                return;
            }
            int id = Database.createGroup(name);
            if (id > 0) {
                nav.openTrip(new Group(id, name));
                nav.toast("Trip created");
            } else {
                message("Error", "Could not create the trip.");
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

            Label title = heading(g.getName());
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
            open.setOnAction(e -> nav.openTrip(g));

            VBox box = new VBox(10, title, total, meta, chips, open);
            box.getStyleClass().add("card");
            box.setPrefWidth(330);
            grid.getChildren().add(box);
        }
        content.getChildren().add(grid);
    }

    // ================= DETAIL =================

    public void renderDetail(VBox content, Group group) {
        int gid = group.getId();

        Button back = secondaryButton("←  All trips");
        back.setOnAction(e -> nav.show("My Trips"));
        Button export = secondaryButton("⇩  Export report");
        Button settle = secondaryButton("Settle up →");
        settle.setOnAction(e -> nav.settleUp(gid));
        Button deleteTrip = dangerButton("Delete trip");
        deleteTrip.setOnAction(e -> {
            if (confirm("Delete trip", "Delete \"" + group.getName()
                    + "\" with all its expenses, members and settlements? This cannot be undone.")) {
                if (Stats.deleteGroup(gid)) {
                    nav.show("My Trips");
                    nav.toast("Trip deleted");
                } else {
                    message("Error", "Could not delete this trip.");
                }
            }
        });
        pageHeader(content, group.getName(), "Members, shared expenses and balances for this trip.",
                back, export, settle, deleteTrip);

        List<Member> members = Database.getGroupMembers(gid);
        List<Expense> expenses = GroupExpenses.list(gid);
        List<Balance> balances = Database.getGroupBalances(gid);
        List<Transfer> transfers = SettlementCalculator.suggest(balances);
        double outstanding = transfers.stream().mapToDouble(Transfer::amount).sum();

        export.setOnAction(e -> exportReport(group, expenses, balances, transfers));

        content.getChildren().add(new HBox(16,
                statCard("Total spent", money(Stats.groupTotal(gid)), "All shared expenses"),
                statCard("Expenses", String.valueOf(expenses.size()), "Recorded"),
                statCard("Members", String.valueOf(members.size()), "In this trip"),
                statCard("To be settled", money(outstanding), "Outstanding")));

        // ---- members
        TextField memberName = new TextField();
        memberName.setPromptText("Member name");
        memberName.setPrefWidth(220);
        Button addMember = primaryButton("Add member");
        Runnable doAdd = () -> {
            if (memberName.getText().isBlank()) {
                message("Missing name", "Enter a member name.");
                return;
            }
            if (Database.addMember(gid, memberName.getText())) {
                nav.openTrip(group);
            } else {
                message("Error", "Could not add the member.");
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
                        nav.openTrip(group);
                    } else {
                        message("Cannot remove", m.getName()
                                + " already has expenses or settlements in this trip, so they can't be removed.");
                    }
                }
            });
            HBox chipBox = new HBox(6, new Label(m.getName()), x);
            chipBox.setAlignment(Pos.CENTER_LEFT);
            chipBox.getStyleClass().add("member-chip");
            memberChips.getChildren().add(chipBox);
        }
        if (members.isEmpty()) {
            memberChips.getChildren().add(smallText("Add at least one member to start recording expenses."));
        }
        content.getChildren().addAll(sectionTitle("Members"),
                card(new VBox(12, new HBox(10, memberName, addMember), memberChips)));

        // ---- expenses
        Button record = primaryButton("＋  Record expense");
        record.setOnAction(e -> nav.expenseForm(group, null));
        HBox expHeader = new HBox(sectionTitle("Expenses"), spacer(), record);
        expHeader.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(expHeader);

        if (expenses.isEmpty()) {
            content.getChildren().add(emptyMessage("No expenses yet. Record the first shared cost."));
        } else {
            VBox list = new VBox(8);
            for (Expense ex : expenses) {
                HBox meta = new HBox(8, chip(ex.category(), categoryColor(ex.category())),
                        smallText((ex.date().isBlank() ? "" : ex.date() + " • ")
                                + "Paid by " + ex.payer() + " • split between " + ex.sharedBy()));
                meta.setAlignment(Pos.CENTER_LEFT);
                VBox labels = new VBox(5, heading(ex.name()), meta);
                HBox.setHgrow(labels, Priority.ALWAYS);
                Label price = new Label(money(ex.amount()));
                price.getStyleClass().add("amount");

                Button edit = smallButton("Edit");
                edit.setOnAction(e -> nav.expenseForm(group, ex));
                Button del = dangerSmallButton("Delete");
                del.setOnAction(e -> {
                    if (confirm("Delete expense", "Delete \"" + ex.name() + "\"? Its shares will be removed too.")) {
                        if (Database.deleteGroupExpense(ex.id(), gid)) {
                            nav.openTrip(group);
                            nav.toast("Expense deleted");
                        } else {
                            message("Error", "Could not delete this expense.");
                        }
                    }
                });

                HBox row = new HBox(10, labels, price, edit, del);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("row-card");
                list.getChildren().add(row);
            }
            content.getChildren().add(list);

            // ---- by category
            Map<String, Double> byCategory = new LinkedHashMap<>();
            expenses.forEach(ex -> byCategory.merge(ex.category(), ex.amount(), Double::sum));
            double total = byCategory.values().stream().mapToDouble(Double::doubleValue).sum();
            VBox breakdown = new VBox(14);
            byCategory.entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .forEach(en -> {
                        double share = total == 0 ? 0 : en.getValue() / total;
                        HBox top = new HBox(8, chip(en.getKey(), categoryColor(en.getKey())), spacer(),
                                smallText(money(en.getValue()) + "  ·  " + Math.round(share * 100) + "%"));
                        top.setAlignment(Pos.CENTER_LEFT);
                        ProgressBar bar = new ProgressBar(share);
                        bar.setMaxWidth(Double.MAX_VALUE);
                        bar.setPrefHeight(8);
                        bar.setStyle("-fx-accent: " + categoryColor(en.getKey()) + ";");
                        breakdown.getChildren().add(new VBox(6, top, bar));
                    });
            content.getChildren().addAll(sectionTitle("Spending by category"), card(breakdown));
        }

        // ---- balances
        content.getChildren().add(sectionTitle("Balances"));
        if (balances.isEmpty()) {
            content.getChildren().add(emptyMessage("Balances appear once the trip has members."));
        } else {
            VBox rows = new VBox(8);
            balances.forEach(b -> rows.getChildren().add(SettlementsPage.balanceRow(b)));
            content.getChildren().add(card(rows));
        }
    }

    private void exportReport(Group group, List<Expense> expenses, List<Balance> balances, List<Transfer> transfers) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export trip report");
        chooser.setInitialFileName(group.getName().replaceAll("[^A-Za-z0-9_-]+", "-") + "-report.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File file = chooser.showSaveDialog(nav.stage());
        if (file == null) {
            return;
        }
        try {
            CsvExport.trip(file.toPath(), group, expenses, balances, transfers);
            nav.toast("Trip report exported");
        } catch (IOException ex) {
            message("Export failed", ex.getMessage());
        }
    }

    // ================= EXPENSE FORM (same window; add / edit, any split) =================

    private record Row(Member member, CheckBox check, TextField input) {
    }

    public void renderExpenseForm(VBox content, Group group, Expense existing) {
        List<Member> members = Database.getGroupMembers(group.getId());
        boolean editing = existing != null;

        Button back = secondaryButton("←  Back to trip");
        back.setOnAction(e -> nav.openTrip(group));
        pageHeader(content, editing ? "Edit expense" : "Record expense",
                (editing ? "Update the shared cost for " : "Split a shared cost for ") + group.getName(), back);

        if (members.isEmpty()) {
            content.getChildren().add(emptyMessage("Add at least one member to this trip before recording an expense."));
            return;
        }

        Map<Integer, Double> oldShares = new HashMap<>();
        if (editing) {
            for (double[] s : Database.getExpenseSplits(existing.id())) {
                oldShares.put((int) s[0], s[1]);
            }
        }
        boolean oldWasEqual = true;
        if (!oldShares.isEmpty()) {
            double min = oldShares.values().stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = oldShares.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            oldWasEqual = max - min <= 0.011;
        }

        TextField name = new TextField(editing ? existing.name() : "");
        name.setPromptText("e.g. Hotel, dinner, taxi");
        TextField amount = new TextField(editing ? String.valueOf(existing.amount()) : "");
        amount.setPromptText("0.00");

        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        category.setValue(editing && CATEGORIES.contains(existing.category()) ? existing.category() : "Food");
        category.setMaxWidth(Double.MAX_VALUE);

        DatePicker date = new DatePicker(LocalDate.now());
        if (editing && !existing.date().isBlank()) {
            try {
                date.setValue(LocalDate.parse(existing.date()));
            } catch (RuntimeException ignored) {
                // keep today
            }
        }
        date.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Member> payer = new ComboBox<>(FXCollections.observableArrayList(members));
        payer.setPromptText("Who paid?");
        payer.setMaxWidth(Double.MAX_VALUE);
        if (editing) {
            members.stream().filter(m -> m.getId() == existing.payerId()).findFirst().ifPresent(payer::setValue);
        }

        ComboBox<Mode> mode = new ComboBox<>(FXCollections.observableArrayList(Mode.values()));
        mode.setValue(editing && !oldWasEqual ? Mode.EXACT : Mode.EQUAL);
        mode.setMaxWidth(Double.MAX_VALUE);

        // participants: checkbox + value box (value box depends on the split mode)
        List<Row> rows = new ArrayList<>();
        VBox participantBox = new VBox(8);
        for (Member m : members) {
            CheckBox check = new CheckBox(m.getName());
            check.setMinWidth(160);
            check.setSelected(!editing || oldShares.containsKey(m.getId()));
            TextField input = new TextField();
            input.setPrefWidth(140);
            if (editing && !oldWasEqual && oldShares.containsKey(m.getId())) {
                input.setText(String.valueOf(oldShares.get(m.getId())));
            }
            HBox line = new HBox(10, check, input);
            line.setAlignment(Pos.CENTER_LEFT);
            participantBox.getChildren().add(line);
            rows.add(new Row(m, check, input));
        }

        Button toggleAll = smallButton("Select / clear all");
        toggleAll.setOnAction(e -> {
            boolean select = rows.stream().anyMatch(r -> !r.check().isSelected());
            rows.forEach(r -> r.check().setSelected(select));
        });

        Label preview = smallText("");
        preview.setWrapText(true);
        Label error = new Label();
        error.getStyleClass().add("error-label");
        error.setWrapText(true);

        // shares in paise, or IllegalArgumentException with a friendly message
        Supplier<Map<Integer, Long>> compute = () -> {
            Double total = Money.parse(amount.getText());
            if (total == null) {
                throw new IllegalArgumentException("Enter a positive amount with at most two decimals.");
            }
            Map<Integer, Double> inputs = new LinkedHashMap<>();
            for (Row r : rows) {
                if (!r.check().isSelected()) {
                    continue;
                }
                double value = 1.0;
                if (mode.getValue() != Mode.EQUAL) {
                    String text = r.input().getText() == null ? "" : r.input().getText().trim();
                    if (text.isEmpty()) {
                        if (mode.getValue() != Mode.SHARES) {
                            throw new IllegalArgumentException("Enter a value for " + r.member().getName() + ".");
                        }
                    } else {
                        try {
                            value = Double.parseDouble(text);
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("Invalid number for " + r.member().getName() + ".");
                        }
                    }
                }
                inputs.put(r.member().getId(), value);
            }
            return SplitCalculator.compute(mode.getValue(), Math.round(total * 100), inputs);
        };

        Runnable updatePreview = () -> {
            Mode m = mode.getValue();
            for (Row r : rows) {
                boolean show = m != Mode.EQUAL;
                r.input().setVisible(show);
                r.input().setManaged(show);
                r.input().setDisable(!r.check().isSelected());
                r.input().setPromptText(switch (m) {
                    case EXACT -> "₹ amount";
                    case PERCENT -> "% share";
                    case SHARES -> "shares (1)";
                    default -> "";
                });
            }
            try {
                Map<Integer, Long> shares = compute.get();
                preview.setText(rows.stream()
                        .filter(r -> shares.containsKey(r.member().getId()))
                        .map(r -> r.member().getName() + " " + money(shares.get(r.member().getId()) / 100.0))
                        .collect(Collectors.joining("   ·   ")));
            } catch (IllegalArgumentException ex) {
                preview.setText(ex.getMessage());
            }
        };
        amount.textProperty().addListener((o, a, b) -> updatePreview.run());
        mode.valueProperty().addListener((o, a, b) -> updatePreview.run());
        for (Row r : rows) {
            r.check().selectedProperty().addListener((o, a, b) -> updatePreview.run());
            r.input().textProperty().addListener((o, a, b) -> updatePreview.run());
        }
        updatePreview.run();

        Button save = primaryButton(editing ? "Save changes" : "Save expense");
        Button cancel = secondaryButton("Cancel");
        cancel.setOnAction(e -> nav.openTrip(group));

        save.setOnAction(e -> {
            String problem = null;
            if (name.getText().isBlank()) {
                problem = "Enter an expense name.";
            } else if (payer.getValue() == null) {
                problem = "Choose who paid.";
            } else if (date.getValue() == null) {
                problem = "Pick a date.";
            } else {
                try {
                    compute.get();
                } catch (IllegalArgumentException ex) {
                    problem = ex.getMessage();
                }
            }
            if (problem != null) {
                error.setText(problem);
                return;
            }

            Map<Integer, Long> shares = compute.get();
            long cents = Math.round(Money.parse(amount.getText()) * 100);
            boolean ok = GroupExpenses.save(editing ? existing.id() : null, group.getId(), name.getText().trim(),
                    cents, payer.getValue().getId(), date.getValue().toString(), category.getValue(), shares);
            if (ok) {
                nav.openTrip(group);
                nav.toast(editing ? "Expense updated" : "Expense saved");
            } else {
                error.setText("Could not save the expense. Please check the details and try again.");
            }
        });

        GridPane form = formGrid();
        form.setPrefWidth(760);
        form.addRow(0, new Label("Expense"), name);
        form.addRow(1, new Label("Amount (₹)"), amount);
        form.addRow(2, new Label("Category"), category);
        form.addRow(3, new Label("Date"), date);
        form.addRow(4, new Label("Paid by"), payer);
        form.addRow(5, new Label("Split"), mode);
        form.add(toggleAll, 0, 6);
        form.add(participantBox, 1, 6);
        form.add(preview, 0, 7, 2, 1);
        form.add(error, 0, 8, 2, 1);
        form.add(new HBox(10, save, cancel), 1, 9);

        VBox box = card(form);
        box.setMaxWidth(860);
        content.getChildren().add(box);

        if (!editing) {
            javafx.application.Platform.runLater(name::requestFocus);
        }
    }
}