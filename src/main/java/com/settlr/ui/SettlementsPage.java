package com.settlr.ui;

import static com.settlr.ui.Ui.*;

import java.util.List;

import com.settlr.Balance;
import com.settlr.Group;
import com.settlr.Settlement;
import com.settlr.SettlementCalculator;
import com.settlr.SettlementCalculator.Transfer;
import com.settlr.db.Database;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class SettlementsPage {

    private final Nav nav;

    public SettlementsPage(Nav nav) {
        this.nav = nav;
    }

    /** One member's balance, green when they should receive, red when they owe. */
    static HBox balanceRow(Balance b) {
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

    public void render(VBox content, Integer preselectGroupId) {
        pageHeader(content, "Settlements", "See who owes whom and record transfers once they are paid.");

        List<Group> groups = Database.getGroups();
        if (groups.isEmpty()) {
            content.getChildren().add(emptyMessage("Create a trip and add members before using settlements."));
            return;
        }

        ComboBox<Group> picker = new ComboBox<>(FXCollections.observableArrayList(groups));
        picker.setMinWidth(260);
        Group initial = groups.get(0);
        for (Group g : groups) {
            if (preselectGroupId != null && g.getId() == preselectGroupId) {
                initial = g;
            }
        }
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
                body.getChildren().add(emptyMessage("Everyone is settled up for this trip."));
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
                                nav.settleUp(group.getId());
                                nav.toast("Transfer recorded");
                            } else {
                                message("Error", "Could not record this transfer.");
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
}