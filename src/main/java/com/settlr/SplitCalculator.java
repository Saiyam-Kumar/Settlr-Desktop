package com.settlr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns "total + how to split" into exact per-member shares in paise (cents).
 * The shares always add up to the total, with no rounding drift.
 */
public final class SplitCalculator {

    public enum Mode {
        EQUAL("Equally"), EXACT("Exact amounts"), PERCENT("Percentages"), SHARES("Shares");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private SplitCalculator() {
    }

    /**
     * @param inputs memberId -> value typed by the user (ignored for EQUAL); only participants are included
     * @throws IllegalArgumentException with a user-friendly message when the input is invalid
     */
    public static Map<Integer, Long> compute(Mode mode, long totalCents, Map<Integer, Double> inputs) {
        if (totalCents <= 0) {
            throw new IllegalArgumentException("Enter a positive amount.");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("Select at least one participant.");
        }

        return switch (mode) {
            case EQUAL -> {
                Map<Integer, Double> weights = new LinkedHashMap<>();
                inputs.keySet().forEach(id -> weights.put(id, 1.0));
                yield allocate(totalCents, weights);
            }
            case EXACT -> exact(totalCents, inputs);
            case PERCENT -> {
                double sum = inputs.values().stream().mapToDouble(Double::doubleValue).sum();
                if (Math.abs(sum - 100) > 0.01) {
                    throw new IllegalArgumentException(String.format(Locale.ROOT,
                            "Percentages add up to %.2f%%, they must total 100%%.", sum));
                }
                yield allocate(totalCents, inputs);
            }
            case SHARES -> allocate(totalCents, inputs);
        };
    }

    private static Map<Integer, Long> exact(long totalCents, Map<Integer, Double> inputs) {
        Map<Integer, Long> out = new LinkedHashMap<>();
        long sum = 0;
        for (Map.Entry<Integer, Double> e : inputs.entrySet()) {
            double v = e.getValue();
            long cents = Math.round(v * 100);
            if (v < 0) {
                throw new IllegalArgumentException("Amounts cannot be negative.");
            }
            if (Math.abs(v * 100 - cents) > 0.0001) {
                throw new IllegalArgumentException("Amounts can have at most two decimals.");
            }
            out.put(e.getKey(), cents);
            sum += cents;
        }
        if (sum != totalCents) {
            throw new IllegalArgumentException("Amounts add up to " + Money.format(sum / 100.0)
                    + " but the total is " + Money.format(totalCents / 100.0) + ".");
        }
        return out;
    }

    /** Largest-remainder allocation: proportional to weights, leftover paise go to the biggest fractions. */
    private static Map<Integer, Long> allocate(long totalCents, Map<Integer, Double> weights) {
        double sum = 0;
        for (double w : weights.values()) {
            if (w < 0) {
                throw new IllegalArgumentException("Values cannot be negative.");
            }
            sum += w;
        }
        if (sum <= 0) {
            throw new IllegalArgumentException("Enter values greater than zero.");
        }

        Map<Integer, Long> out = new LinkedHashMap<>();
        Map<Integer, Double> fractions = new LinkedHashMap<>();
        long assigned = 0;
        for (Map.Entry<Integer, Double> e : weights.entrySet()) {
            double exact = totalCents * e.getValue() / sum;
            long floor = (long) Math.floor(exact + 1e-9);
            out.put(e.getKey(), floor);
            fractions.put(e.getKey(), exact - floor);
            assigned += floor;
        }

        long left = totalCents - assigned;
        List<Integer> order = new ArrayList<>(fractions.keySet());
        order.sort((a, b) -> Double.compare(fractions.get(b), fractions.get(a))); // stable: ties keep input order
        for (int i = 0; i < left && i < order.size(); i++) {
            out.merge(order.get(i), 1L, Long::sum);
        }
        return out;
    }
}