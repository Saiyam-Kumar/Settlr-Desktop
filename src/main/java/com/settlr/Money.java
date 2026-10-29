package com.settlr;

/** Currency formatting (Indian lakh grouping) and amount parsing. */
public final class Money {

    private Money() {
    }

    public static String format(double amount) {
        boolean negative = amount < 0;
        long cents = Math.round(Math.abs(amount) * 100);
        long rupees = cents / 100;
        long paise = cents % 100;

        String digits = Long.toString(rupees);
        String grouped;
        if (digits.length() <= 3) {
            grouped = digits;
        } else {
            String lastThree = digits.substring(digits.length() - 3);
            String rest = digits.substring(0, digits.length() - 3);
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (int i = rest.length() - 1; i >= 0; i--) {
                sb.insert(0, rest.charAt(i));
                count++;
                if (count % 2 == 0 && i > 0) {
                    sb.insert(0, ',');
                }
            }
            grouped = sb + "," + lastThree;
        }
        return (negative ? "-" : "") + "₹" + grouped + String.format(".%02d", paise);
    }

    /** Returns null unless the text is a positive amount with at most two decimals. */
    public static Double parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text.trim().replace(",", ""));
            if (!Double.isFinite(value) || value <= 0
                    || Math.abs(value * 100 - Math.round(value * 100)) > 0.0001) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}