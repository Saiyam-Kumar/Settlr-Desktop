package com.settlr.ui;

import static com.settlr.ui.Ui.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import com.settlr.db.Stats;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class AnalyticsPage {

    public void render(VBox content) {
        pageHeader(content, "Analytics", "Understand where your personal money goes.");

        ComboBox<String> range = new ComboBox<>(FXCollections.observableArrayList(
                "All time", "This month", "Last 3 months", "This year"));
        range.setValue("All time");
        HBox rangeRow = new HBox(10, smallLabel("Period"), range);
        rangeRow.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(18);
        content.getChildren().addAll(rangeRow, body);

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

            String top = totals.keySet().iterator().next();
            body.getChildren().add(new HBox(16,
                    statCard("Total spending", money(total), range.getValue()),
                    statCard("Top category", top, money(totals.get(top))),
                    statCard("Categories used", String.valueOf(totals.size()), "In this period")));

            PieChart pie = Charts.categoryPie(totals);
            VBox pieCard = new VBox(8, sectionTitle("Spending by category"), pie);
            pieCard.getStyleClass().add("card");
            pieCard.setMinWidth(340);
            HBox.setHgrow(pieCard, Priority.ALWAYS);

            VBox breakdown = new VBox(14, sectionTitle("Breakdown"));
            breakdown.getStyleClass().add("card");
            breakdown.setPrefWidth(420);
            for (Map.Entry<String, Double> e : totals.entrySet()) {
                double share = total == 0 ? 0 : e.getValue() / total;
                HBox top2 = new HBox(8, chip(e.getKey(), categoryColor(e.getKey())), spacer(),
                        smallText(money(e.getValue()) + "  ·  " + Math.round(share * 100) + "%"));
                top2.setAlignment(Pos.CENTER_LEFT);
                ProgressBar bar = new ProgressBar(share);
                bar.setMaxWidth(Double.MAX_VALUE);
                bar.setPrefHeight(8);
                bar.setStyle("-fx-accent: " + categoryColor(e.getKey()) + ";");
                breakdown.getChildren().add(new VBox(6, top2, bar));
            }
            body.getChildren().add(new HBox(16, pieCard, breakdown));

            VBox trend = new VBox(8, sectionTitle("Monthly trend — last 12 months"), Charts.monthly(12));
            trend.getStyleClass().add("card");
            body.getChildren().add(trend);
        };

        range.setOnAction(e -> refresh.run());
        refresh.run();
    }
}