package com.settlr.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Group expenses with a date, a category and any kind of split (equal, exact, percent, shares). */
public final class GroupExpenses {

    public record Expense(int id, String name, double amount, String payer, int payerId,
                          String date, String category, int sharedBy) {
    }

    private GroupExpenses() {
    }

    public static List<Expense> list(int groupId) {
        List<Expense> out = new ArrayList<>();
        String sql = """
                SELECT e.id, e.name, e.amount, e.payer,
                       COALESCE(e.payer_member_id, 0) AS pid,
                       COALESCE(e.expense_date, '') AS d,
                       COALESCE(e.category, 'Other') AS c,
                       (SELECT COUNT(*) FROM expense_splits s WHERE s.expense_id = e.id) AS n
                FROM expenses e
                WHERE e.group_id = ?
                ORDER BY COALESCE(e.expense_date, '') DESC, e.id DESC
                """;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Expense(rs.getInt("id"), rs.getString("name"), rs.getDouble("amount"),
                            rs.getString("payer"), rs.getInt("pid"), rs.getString("d"),
                            rs.getString("c"), rs.getInt("n")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not load trip expenses: " + e.getMessage());
        }
        return out;
    }

    /**
     * Inserts (expenseId == null) or updates an expense and replaces its shares, atomically.
     * shares: memberId -> paise; must add up to totalCents.
     */
    public static boolean save(Integer expenseId, int groupId, String name, long totalCents, int payerId,
                               String date, String category, Map<Integer, Long> shares) {
        if (groupId <= 0 || name == null || name.isBlank() || totalCents <= 0
                || shares == null || shares.isEmpty()) {
            return false;
        }
        long sum = shares.values().stream().mapToLong(Long::longValue).sum();
        if (sum != totalCents || shares.values().stream().anyMatch(v -> v < 0)) {
            return false;
        }

        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                String payerName = memberName(c, groupId, payerId);
                if (payerName == null) {
                    c.rollback();
                    return false;
                }
                for (int memberId : shares.keySet()) {
                    if (memberName(c, groupId, memberId) == null) {
                        c.rollback();
                        return false;
                    }
                }

                double amount = totalCents / 100.0;
                int id;
                if (expenseId == null) {
                    try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO expenses
                                (name, amount, payer, group_id, payer_member_id, expense_date, category)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, name.trim());
                        ps.setDouble(2, amount);
                        ps.setString(3, payerName);
                        ps.setInt(4, groupId);
                        ps.setInt(5, payerId);
                        ps.setString(6, date);
                        ps.setString(7, category);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) {
                                c.rollback();
                                return false;
                            }
                            id = keys.getInt(1);
                        }
                    }
                } else {
                    id = expenseId;
                    try (PreparedStatement ps = c.prepareStatement("""
                            UPDATE expenses
                            SET name = ?, amount = ?, payer = ?, payer_member_id = ?,
                                expense_date = ?, category = ?
                            WHERE id = ? AND group_id = ?
                            """)) {
                        ps.setString(1, name.trim());
                        ps.setDouble(2, amount);
                        ps.setString(3, payerName);
                        ps.setInt(4, payerId);
                        ps.setString(5, date);
                        ps.setString(6, category);
                        ps.setInt(7, id);
                        ps.setInt(8, groupId);
                        if (ps.executeUpdate() == 0) {
                            c.rollback();
                            return false;
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "DELETE FROM expense_splits WHERE expense_id = ?")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO expense_splits(expense_id, member_id, share_amount) VALUES (?, ?, ?)")) {
                    for (Map.Entry<Integer, Long> e : shares.entrySet()) {
                        ps.setInt(1, id);
                        ps.setInt(2, e.getKey());
                        ps.setDouble(3, e.getValue() / 100.0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                c.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                System.out.println("Could not save expense: " + e.getMessage());
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return false;
        }
    }

    private static String memberName(Connection c, int groupId, int memberId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT name FROM group_members WHERE id = ? AND group_id = ?")) {
            ps.setInt(1, memberId);
            ps.setInt(2, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("name") : null;
            }
        }
    }
}