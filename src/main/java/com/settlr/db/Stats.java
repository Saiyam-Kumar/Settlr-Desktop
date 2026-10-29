package com.settlr.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Additional queries that sit next to Database (same schema, no migration needed). */
public final class Stats {

    private Stats() {
    }

    /** Category -> total, biggest first. Dates are ISO yyyy-MM-dd; null means no limit. */
    public static Map<String, Double> personalByCategory(String start, String end) {
        Map<String, Double> out = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder(
                "SELECT category, SUM(amount) AS total FROM personal_expenses WHERE 1=1");
        boolean hasStart = start != null && !start.isBlank();
        boolean hasEnd = end != null && !end.isBlank();
        if (hasStart) {
            sql.append(" AND expense_date >= ?");
        }
        if (hasEnd) {
            sql.append(" AND expense_date <= ?");
        }
        sql.append(" GROUP BY category ORDER BY total DESC");

        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            if (hasStart) {
                ps.setString(i++, start);
            }
            if (hasEnd) {
                ps.setString(i, end);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("category"), rs.getDouble("total"));
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not load category totals: " + e.getMessage());
        }
        return out;
    }

    /** Last N months (oldest first), keys like "2026-10", zero-filled. */
    public static Map<String, Double> personalByMonth(int months) {
        Map<String, Double> out = new LinkedHashMap<>();
        YearMonth now = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            out.put(now.minusMonths(i).toString(), 0.0);
        }
        String from = now.minusMonths(months - 1L).atDay(1).toString();

        String sql = """
                SELECT substr(expense_date, 1, 7) AS ym, SUM(amount) AS total
                FROM personal_expenses WHERE expense_date >= ?
                GROUP BY ym
                """;
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, from);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ym = rs.getString("ym");
                    if (out.containsKey(ym)) {
                        out.put(ym, rs.getDouble("total"));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not load monthly totals: " + e.getMessage());
        }
        return out;
    }

    public static double groupTotal(int groupId) {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE group_id = ?")) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            System.out.println("Could not load trip total: " + e.getMessage());
            return 0;
        }
    }

    /** Deletes a trip with its expenses, shares, settlements and members. */
    public static boolean deleteGroup(int groupId) {
        try (Connection c = Database.connect()) {
            c.setAutoCommit(false);
            try {
                run(c, "DELETE FROM settlements WHERE group_id = ?", groupId);
                run(c, "DELETE FROM expenses WHERE group_id = ?", groupId); // splits cascade
                run(c, "DELETE FROM groups WHERE id = ?", groupId);         // members cascade
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                System.out.println("Could not delete trip: " + e.getMessage());
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return false;
        }
    }

    /** Removes a member only if they have no expenses, shares or settlements. */
    public static boolean deleteMember(int groupId, int memberId) {
        String check = """
                SELECT
                  (SELECT COUNT(*) FROM expenses WHERE payer_member_id = ?)
                + (SELECT COUNT(*) FROM expense_splits WHERE member_id = ?)
                + (SELECT COUNT(*) FROM settlements WHERE from_member_id = ? OR to_member_id = ?)
                """;
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement(check)) {
            for (int i = 1; i <= 4; i++) {
                ps.setInt(i, memberId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return false;
                }
            }
            try (PreparedStatement del = c.prepareStatement(
                    "DELETE FROM group_members WHERE id = ? AND group_id = ?")) {
                del.setInt(1, memberId);
                del.setInt(2, groupId);
                return del.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.out.println("Could not remove member: " + e.getMessage());
            return false;
        }
    }

    /** Full edit: name, amount, payer AND participants. Shares are recalculated. */
    public static boolean updateGroupExpense(int expenseId, int groupId, String name,
                                             double amount, int payerId, List<Integer> participants) {
        long cents = Math.round(amount * 100);
        if (expenseId <= 0 || groupId <= 0 || name == null || name.isBlank()
                || cents <= 0 || Math.abs(amount * 100 - cents) > 0.0001
                || participants == null || participants.isEmpty()
                || participants.stream().distinct().count() != participants.size()) {
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
                for (int id : participants) {
                    if (memberName(c, groupId, id) == null) {
                        c.rollback();
                        return false;
                    }
                }

                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE expenses SET name = ?, amount = ?, payer = ?, payer_member_id = ?
                        WHERE id = ? AND group_id = ?
                        """)) {
                    ps.setString(1, name.trim());
                    ps.setDouble(2, amount);
                    ps.setString(3, payerName);
                    ps.setInt(4, payerId);
                    ps.setInt(5, expenseId);
                    ps.setInt(6, groupId);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        return false;
                    }
                }

                run(c, "DELETE FROM expense_splits WHERE expense_id = ?", expenseId);

                long base = cents / participants.size();
                long remainder = cents % participants.size();
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO expense_splits(expense_id, member_id, share_amount) VALUES (?, ?, ?)")) {
                    for (int i = 0; i < participants.size(); i++) {
                        long share = base + (i < remainder ? 1 : 0);
                        ps.setInt(1, expenseId);
                        ps.setInt(2, participants.get(i));
                        ps.setDouble(3, share / 100.0);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                System.out.println("Could not update expense: " + e.getMessage());
                return false;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return false;
        }
    }

    private static void run(Connection c, String sql, int id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
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