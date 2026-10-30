package com.settlr.ui;

import static com.settlr.ui.Ui.*;

import java.util.Map;

import com.settlr.Money;
import com.settlr.db.Extras;
import com.settlr.db.Extras.Recurring;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class BudgetsPage {

    private final Nav nav;

    public BudgetsPage(Nav nav) {
        this.nav = nav;
    }

    public void render(VBox content) {
        pageHeader(content, "Budgets", "Set monthly limits and automate the expenses that repeat.");

        // ---------- budgets ----------
        ComboBox<String> category = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        category.setValue("Food");
        TextField limit = new TextField();
        limit.setPromptText("Monthly limit (₹)");
        limit.setPrefWidth(180);
        Button save = primaryButton("Save budget");
        save.setOnAction(e -> {
            Double value = Money.parse(limit.getText());
            if (value == null) {
                message("Invalid limit", "Enter a positive amount with at most two decimals.");
                return;
            }
            if (Extras.setBudget(category.getValue(), value)) {
                nav.show("Budgets");
                nav.toast(category.getValue() + " budget saved");
            } else {
                message("Error", "Could not save the budget.");
            }
        });
        FlowPane budgetForm = new FlowPane(10, 10, category, limit, save);
        budgetForm.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(sectionTitle("Set a monthly budget"), card(budgetForm),
                sectionTitle("This month"));

        Map<String, Double> budgets = Extras.budgets();
        if (budgets.isEmpty()) {
            content.getChildren().add(emptyMessage("No budgets yet. Pick a category above and set a limit."));
        } else {
            Map<String, Double> spent = Extras.spentThisMonth();
            VBox rows = new VBox(16);
            budgets.forEach((cat, max) -> {
                VBox row = budgetRow(cat, spent.getOrDefault(cat, 0.0), max);
                HBox.setHgrow(row, Priority.ALWAYS);
                Button remove = dangerSmallButton("Remove");
                remove.setOnAction(e -> {
                    Extras.deleteBudget(cat);
                    nav.show("Budgets");
                    nav.toast(cat + " budget removed");
                });
                HBox line = new HBox(14, row, remove);
                line.setAlignment(Pos.CENTER_LEFT);
                rows.getChildren().add(line);
            });
            content.getChildren().add(card(rows));
        }

        // ---------- recurring ----------
        TextField rName = new TextField();
        rName.setPromptText("e.g. Rent, Netflix");
        rName.setPrefWidth(200);
        TextField rAmount = new TextField();
        rAmount.setPromptText("Amount (₹)");
        rAmount.setPrefWidth(130);
        ComboBox<String> rCategory = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        rCategory.setValue("Bills");
        ComboBox<Integer> rDay = new ComboBox<>();
        for (int d = 1; d <= 28; d++) {
            rDay.getItems().add(d);
        }
        rDay.setValue(1);
        Button add = primaryButton("Add recurring");
        add.setOnAction(e -> {
            Double value = Money.parse(rAmount.getText());
            if (rName.getText().isBlank() || value == null) {
                message("Invalid input", "Enter a name and a positive amount.");
                return;
            }
            if (Extras.addRecurring(rName.getText(), value, rCategory.getValue(), rDay.getValue())) {
                nav.show("Budgets");
                nav.toast("Recurring expense added");
            } else {
                message("Error", "Could not add the recurring expense.");
            }
        });
        FlowPane recurringForm = new FlowPane(10, 10, rName, rAmount, rCategory,
                smallLabel("on day"), rDay, add);
        recurringForm.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(sectionTitle("Recurring expenses"), card(recurringForm),
                smallText("Added automatically to your personal expenses when Settlr opens on or after that day each month."));

        var recurring = Extras.recurring();
        if (recurring.isEmpty()) {
            content.getChildren().add(emptyMessage("No recurring expenses yet."));
        } else {
            VBox list = new VBox(8);
            for (Recurring r : recurring) {
                HBox meta = new HBox(8, chip(r.category(), categoryColor(r.category())),
                        smallText("Day " + r.day() + " of every month"));
                meta.setAlignment(Pos.CENTER_LEFT);
                VBox labels = new VBox(5, heading(r.name()), meta);
                HBox.setHgrow(labels, Priority.ALWAYS);
                Label price = new Label(money(r.amount()));
                price.getStyleClass().add("amount");
                Button del = dangerSmallButton("Delete");
                del.setOnAction(e -> {
                    Extras.deleteRecurring(r.id());
                    nav.show("Budgets");
                    nav.toast("Recurring expense deleted");
                });
                HBox row = new HBox(10, labels, price, del);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("row-card");
                list.getChildren().add(row);
            }
            content.getChildren().add(list);
        }
    }
}