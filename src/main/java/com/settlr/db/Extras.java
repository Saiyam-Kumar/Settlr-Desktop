package com.settlr.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.settlr.Money;

/** Settings, budgets, recurring expenses, backup, and the schema additions they need. */
public final class Extras {

    public record Recurring(int id, String name, double amount, String category, int day, String lastRun) {
    }

    private Extras() {
    }

    // ---------- schema (safe to run on every start; existing data is kept) ----------

    public static void init() {
        String[] sql = {
            "CREATE TABLE IF NOT EXISTS settings (k TEXT PRIMARY KEY, v TEXT NOT NULL)",
            """
            CREATE TABLE IF NOT EXISTS budgets (
                category TEXT PRIMARY KEY,
                monthly_limit REAL NOT NULL CHECK(monthly_limit > 0)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS recurring (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                amount REAL NOT NULL CHECK(amount > 0),
                category TEXT NOT NULL,
                day_of_month INTEGER NOT NULL CHECK(day_of_month BETWEEN 1 AND 28),
                last_run TEXT
            )
            """
        };
        try (Connection c = Database.connect(); Statement st = c.createStatement()) {
            for (String q : sql) {
                st.execute(q);
            }
            ensureColumn(c, "expenses", "expense_date", "TEXT");
            ensureColumn(c, "expenses", "category", "TEXT");
        } catch (SQLException e) {
            System.out.println("Could not initialise extras: " + e.getMessage());
        }
    }

    private static void ensureColumn(Connection c, String table, String column, String type) throws SQLException {
        boolean exists = false;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    exists = true;
                }
            }
        }
        if (!exists) {
            try (Statement st = c.createStatement()) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            }
        }
    }

    // ---------- settings ----------

    public static String getSetting(String key, String fallback) {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("SELECT v FROM settings WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : fallback;
            }
        } catch (SQLException e) {
            return fallback;
        }
    }

    public static void setSetting(String key, String value) {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO settings(k, v) VALUES (?, ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Could not save setting: " + e.getMessage());
        }
    }

    // ---------- budgets ----------

    public static Map<String, Double> budgets() {
        Map<String, Double> out = new LinkedHashMap<>();
        try (Connection c = Database.connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT category, monthly_limit FROM budgets ORDER BY category")) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getDouble(2));
            }
        } catch (SQLException e) {
            System.out.println("Could not load budgets: " + e.getMessage());
        }
        return out;
    }

    public static boolean setBudget(String category, double limit) {
        if (category == null || category.isBlank() || limit <= 0) {
            return false;
        }
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO budgets(category, monthly_limit) VALUES (?, ?)
                     ON CONFLICT(category) DO UPDATE SET monthly_limit = excluded.monthly_limit
                     """)) {
            ps.setString(1, category);
            ps.setDouble(2, limit);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Could not save budget: " + e.getMessage());
            return false;
        }
    }

    public static void deleteBudget(String category) {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM budgets WHERE category = ?")) {
            ps.setString(1, category);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Could not delete budget: " + e.getMessage());
        }
    }

    /** This month's spending per category (personal expenses). */
    public static Map<String, Double> spentThisMonth() {
        YearMonth now = YearMonth.now();
        return Stats.personalByCategory(now.atDay(1).toString(), now.atEndOfMonth().toString());
    }

    /** A message when this category's budget is over 80% used, otherwise null. */
    public static String budgetWarning(String category) {
        Double limit = budgets().get(category);
        if (limit == null) {
            return null;
        }
        double spent = spentThisMonth().getOrDefault(category, 0.0);
        if (spent > limit) {
            return category + " budget exceeded: " + Money.format(spent) + " of " + Money.format(limit);
        }
        if (spent >= 0.8 * limit) {
            return category + " budget " + Math.round(spent / limit * 100) + "% used";
        }
        return null;
    }

    // ---------- recurring expenses ----------

    public static List<Recurring> recurring() {
        List<Recurring> out = new ArrayList<>();
        try (Connection c = Database.connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, name, amount, category, day_of_month, last_run FROM recurring ORDER BY day_of_month, id")) {
            while (rs.next()) {
                out.add(new Recurring(rs.getInt(1), rs.getString(2), rs.getDouble(3),
                        rs.getString(4), rs.getInt(5), rs.getString(6)));
            }
        } catch (SQLException e) {
            System.out.println("Could not load recurring expenses: " + e.getMessage());
        }
        return out;
    }

    public static boolean addRecurring(String name, double amount, String category, int day) {
        if (name == null || name.isBlank() || amount <= 0 || day < 1 || day > 28) {
            return false;
        }
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO recurring(name, amount, category, day_of_month) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, name.trim());
            ps.setDouble(2, amount);
            ps.setString(3, category);
            ps.setInt(4, day);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Could not add recurring expense: " + e.getMessage());
            return false;
        }
    }

    public static void deleteRecurring(int id) {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM recurring WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Could not delete recurring expense: " + e.getMessage());
        }
    }

    /** Posts this month's recurring expenses that are due and not yet posted. Returns how many were added. */
    public static int postDueRecurring() {
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        int posted = 0;

        for (Recurring r : recurring()) {
            LocalDate due = month.atDay(Math.min(r.day(), month.lengthOfMonth()));
            boolean alreadyDone = r.lastRun() != null && r.lastRun().compareTo(month.toString()) >= 0;
            if (alreadyDone || today.isBefore(due)) {
                continue;
            }
            if (Database.addPersonalExpense(r.name(), r.amount(), r.category(), due.toString())) {
                try (Connection c = Database.connect();
                     PreparedStatement ps = c.prepareStatement("UPDATE recurring SET last_run = ? WHERE id = ?")) {
                    ps.setString(1, month.toString());
                    ps.setInt(2, r.id());
                    ps.executeUpdate();
                    posted++;
                } catch (SQLException e) {
                    System.out.println("Could not update recurring expense: " + e.getMessage());
                }
            }
        }
        return posted;
    }

    // ---------- backup ----------

    /** Writes a consistent copy of the whole database to the given file. */
    public static boolean backupTo(Path destination) {
        try {
            Files.deleteIfExists(destination);
            try (Connection c = Database.connect(); Statement st = c.createStatement()) {
                st.execute("VACUUM INTO '" + destination.toAbsolutePath().toString().replace("'", "''") + "'");
            }
            return true;
        } catch (IOException | SQLException e) {
            System.out.println("Backup failed: " + e.getMessage());
            return false;
        }
    }
}