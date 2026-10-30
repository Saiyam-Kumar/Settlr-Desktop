package com.settlr.ui;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import com.settlr.db.Stats;

import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;

public final class Charts {

    private Charts() {
    }

    public static BarChart<String, Number> monthly(int months) {
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

    /** Pie whose slices use the same colours as the category chips. */
    public static PieChart categoryPie(Map<String, Double> totals) {
        PieChart pie = new PieChart();
        pie.setLegendVisible(false);
        pie.setLabelsVisible(true);
        pie.setAnimated(false);
        pie.setStartAngle(90);
        pie.setPrefHeight(320);

        for (Map.Entry<String, Double> e : totals.entrySet()) {
            PieChart.Data data = new PieChart.Data(e.getKey(), e.getValue());
            String style = "-fx-pie-color: " + Ui.categoryColor(e.getKey()) + ";";
            data.nodeProperty().addListener((o, old, node) -> {
                if (node != null) {
                    node.setStyle(style);
                }
            });
            pie.getData().add(data);
            if (data.getNode() != null) {
                data.getNode().setStyle(style);
            }
        }
        return pie;
    }
}