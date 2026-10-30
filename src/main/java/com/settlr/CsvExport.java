package com.settlr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.settlr.SettlementCalculator.Transfer;
import com.settlr.db.GroupExpenses;

/** CSV reports that open cleanly in Excel / Google Sheets (UTF-8 with BOM so ₹ and names display right). */
public final class CsvExport {

    private CsvExport() {
    }

    /** rows: [id, name, amount, category, date] as returned by Database.getPersonalExpenses. */
    public static void personal(Path file, List<String[]> rows) throws IOException {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(line("Date", "Name", "Category", "Amount (INR)"));
        for (String[] r : rows) {
            sb.append(line(r[4], r[1], r[3], amount(Double.parseDouble(r[2]))));
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public static void trip(Path file, Group group, List<GroupExpenses.Expense> expenses,
                            List<Balance> balances, List<Transfer> transfers) throws IOException {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(line("Settlr trip report", group.getName()));
        sb.append(line());

        sb.append(line("EXPENSES"));
        sb.append(line("Date", "Name", "Category", "Amount (INR)", "Paid by", "Split between"));
        double total = 0;
        for (GroupExpenses.Expense e : expenses) {
            sb.append(line(e.date(), e.name(), e.category(), amount(e.amount()), e.payer(),
                    String.valueOf(e.sharedBy())));
            total += e.amount();
        }
        sb.append(line("", "TOTAL", "", amount(total)));
        sb.append(line());

        sb.append(line("BALANCES (positive = should receive)"));
        sb.append(line("Member", "Paid", "Share", "Net balance"));
        for (Balance b : balances) {
            sb.append(line(b.getName(), amount(b.getPaid()), amount(b.getShare()), amount(b.getBalance())));
        }
        sb.append(line());

        sb.append(line("WHO PAYS WHOM"));
        sb.append(line("From", "To", "Amount (INR)"));
        for (Transfer t : transfers) {
            sb.append(line(t.fromName(), t.toName(), amount(t.amount())));
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String amount(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String line(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        return sb.append('\n').toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}