package com.settlr.ui;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.settlr.CsvExport;
import com.settlr.Money;
import com.settlr.db.Database;
import com.settlr.db.Extras;
import static com.settlr.ui.Ui.CATEGORIES;
import static com.settlr.ui.Ui.card;
import static com.settlr.ui.Ui.categoryColor;
import static com.settlr.ui.Ui.chip;
import static com.settlr.ui.Ui.dangerSmallButton;
import static com.settlr.ui.Ui.emptyMessage;
import static com.settlr.ui.Ui.formGrid;
import static com.settlr.ui.Ui.heading;
import static com.settlr.ui.Ui.message;
import static com.settlr.ui.Ui.money;
import static com.settlr.ui.Ui.pageHeader;
import static com.settlr.ui.Ui.primaryButton;
import static com.settlr.ui.Ui.secondaryButton;
import static com.settlr.ui.Ui.sectionTitle;
import static com.settlr.ui.Ui.smallButton;
import static com.settlr.ui.Ui.smallLabel;
import static com.settlr.ui.Ui.smallText;
import static com.settlr.ui.Ui.style;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public final class PersonalPage {

    private final Nav nav;

    public PersonalPage(Nav nav) {
        this.nav = nav;
    }

    public void render(VBox content) {
        Button export = secondaryButton("⇩  Export CSV");
        pageHeader(content, "Personal Expenses", "Track, search and manage everything you spend.", export);

        // ---- add form
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

        // ---- filters
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

        // ---- summary + list
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

        AtomicReference<List<String[]>> shown = new AtomicReference<>(List.of());
        Runnable[] refresh = new Runnable[1];

        refresh[0] = () -> {
            String start = null;
            String end = null;
            if (from.getValue() != null || to.getValue() != null) {
                start = from.getValue() == null ? null : from.getValue().toString();
                end = to.getValue() == null ? null : to.getValue().toString();
            } else if (!"All Months".equals(monthFilter.getValue())) {
                YearMonth ym = YearMonth.parse(monthFilter.getValue());
                start = ym.atDay(1).toString();
                end = ym.atEndOfMonth().toString();
            }
            String needle = search.getText() == null ? "" : search.getText().trim().toLowerCase();

            List<String[]> rows = Database.getPersonalExpenses(start, end, categoryFilter.getValue()).stream()
                    .filter(r -> needle.isEmpty() || r[1].toLowerCase().contains(needle))
                    .collect(Collectors.toList());
            shown.set(rows);

            double total = rows.stream().mapToDouble(r -> Double.parseDouble(r[2])).sum();
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

                HBox meta = new HBox(8, chip(cat, categoryColor(cat)), smallText(date));
                meta.setAlignment(Pos.CENTER_LEFT);
                VBox labels = new VBox(5, heading(name), meta);
                HBox.setHgrow(labels, Priority.ALWAYS);

                Label price = new Label(money(amount));
                price.getStyleClass().add("amount");

                Button edit = smallButton("Edit");
                edit.setOnAction(e -> {
                    if (editExpense(id, name, amount, cat, date)) {
                        refresh[0].run();
                        nav.toast("Changes saved");
                    }
                });
                Button delete = dangerSmallButton("Delete");
                delete.setOnAction(e -> {
                    if (Database.deletePersonalExpense(id)) {
                        refresh[0].run();
                        nav.toast("Deleted \"" + name + "\"", "Undo", () -> {
                            Database.addPersonalExpense(name, amount, cat, date);
                            refresh[0].run();
                        });
                    } else {
                        message("Error", "Could not delete this expense.");
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
                message("Invalid input", "Enter a name, a positive amount (max 2 decimals) and a date.");
                return;
            }
            String category = categoryField.getValue();
            if (Database.addPersonalExpense(name, amount, category, dateField.getValue().toString())) {
                nameField.clear();
                amountField.clear();
                refresh[0].run();
                String warning = Extras.budgetWarning(category);
                nav.toast(warning != null ? warning : "Expense saved");
            } else {
                message("Error", "Could not save the expense.");
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

        export.setOnAction(e -> exportCsv(shown.get()));
        refresh[0].run();
    }

    private void exportCsv(List<String[]> rows) {
        if (rows.isEmpty()) {
            message("Nothing to export", "There are no expenses in the current view.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export expenses");
        chooser.setInitialFileName("settlr-expenses-" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
        File file = chooser.showSaveDialog(nav.stage());
        if (file == null) {
            return;
        }
        try {
            CsvExport.personal(file.toPath(), rows);
            nav.toast("Exported " + rows.size() + " expense(s)");
        } catch (IOException ex) {
            message("Export failed", ex.getMessage());
        }
    }

    private boolean editExpense(int id, String oldName, double oldAmount, String oldCategory, String oldDate) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit expense");
        dialog.setHeaderText("Update expense details");
        ButtonType saveType = new ButtonType("Save changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        style(dialog.getDialogPane());

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
                message("Error", "Could not update this expense.");
            }
            return ok;
        }
        return false;
    }
}