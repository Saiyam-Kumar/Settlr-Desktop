package com.settlr.ui;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.settlr.Group;
import com.settlr.SettlementCalculator;
import com.settlr.SettlementCalculator.Transfer;
import com.settlr.db.Database;
import com.settlr.db.Extras;
import com.settlr.db.Stats;
import static com.settlr.ui.Ui.budgetRow;
import static com.settlr.ui.Ui.infoRow;
import static com.settlr.ui.Ui.money;
import static com.settlr.ui.Ui.pageHeader;
import static com.settlr.ui.Ui.primaryButton;
import static com.settlr.ui.Ui.secondaryButton;
import static com.settlr.ui.Ui.sectionTitle;
import static com.settlr.ui.Ui.smallText;
import static com.settlr.ui.Ui.statCard;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class DashboardPage {

    private final Nav nav;

    public DashboardPage(Nav nav) {
        this.nav = nav;
    }

    public void render(VBox content) {
        String name = Extras.getSetting("name", "").trim();
        Button add = primaryButton("＋  Add expense");
        add.setOnAction(e -> nav.show("Personal Expenses"));
        pageHeader(content, name.isEmpty() ? "Dashboard" : "Hi, " + name,
                "Your personal spending and shared trips at a glance.", add);

        YearMonth now = YearMonth.now();
        double monthTotal = Database.getPersonalTotal(now.atDay(1).toString(), now.atEndOfMonth().toString(), null);
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

        content.getChildren().add(new HBox(16,
                statCard("This month", money(monthTotal), "Personal spending"),
                statCard("All-time personal", money(allTotal), "Every expense logged"),
                statCard("Trip expenses", money(tripTotal), groups.size() + " trip(s)"),
                statCard("To be settled", money(outstanding), "Across all trips")));

        // Budget watch
        Map<String, Double> budgets = Extras.budgets();
        if (!budgets.isEmpty()) {
            Map<String, Double> spent = Extras.spentThisMonth();
            VBox watch = new VBox(14, sectionTitle("Budget watch — this month"));
            watch.getStyleClass().add("card");
            budgets.forEach((cat, limit) -> watch.getChildren().add(budgetRow(cat, spent.getOrDefault(cat, 0.0), limit)));
            content.getChildren().add(watch);
        }

        // Trend + recent
        VBox trend = new VBox(8, sectionTitle("Spending — last 6 months"), Charts.monthly(6));
        trend.getStyleClass().add("card");
        trend.setMinWidth(380);
        HBox.setHgrow(trend, Priority.ALWAYS);

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
        content.getChildren().add(new HBox(16, trend, recent));

        Button trips = secondaryButton("Open my trips");
        trips.setOnAction(e -> nav.show("My Trips"));
        Button settle = secondaryButton("View settlements");
        settle.setOnAction(e -> nav.show("Settlements"));
        content.getChildren().add(new HBox(10, trips, settle));
    }
}