
package com.settlr.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.settlr.Balance;
import com.settlr.Group;
import com.settlr.Member;
import com.settlr.Settlement;

public class Database {

    private static final String URL = "jdbc:sqlite:settlr.db";

    public static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    // ---------- INITIALIZATION AND MIGRATION ----------

    public static void createTable() {
        String[] sql = {
            """
            CREATE TABLE IF NOT EXISTS groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS group_members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                amount REAL NOT NULL CHECK(amount > 0),
                payer TEXT NOT NULL,
                group_id INTEGER REFERENCES groups(id),
                payer_member_id INTEGER REFERENCES group_members(id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS expense_splits (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                expense_id INTEGER NOT NULL,
                member_id INTEGER NOT NULL,
                share_amount REAL NOT NULL CHECK(share_amount >= 0),
                FOREIGN KEY(expense_id) REFERENCES expenses(id) ON DELETE CASCADE,
                FOREIGN KEY(member_id) REFERENCES group_members(id) ON DELETE CASCADE,
                UNIQUE(expense_id, member_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS personal_expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                amount REAL NOT NULL CHECK(amount > 0),
                category TEXT NOT NULL,
                expense_date TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS settlements (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id INTEGER NOT NULL,
                from_member_id INTEGER NOT NULL,
                to_member_id INTEGER NOT NULL,
                amount REAL NOT NULL CHECK(amount > 0),
                settled_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(group_id) REFERENCES groups(id) ON DELETE CASCADE,
                FOREIGN KEY(from_member_id) REFERENCES group_members(id),
                FOREIGN KEY(to_member_id) REFERENCES group_members(id),
                CHECK(from_member_id <> to_member_id)
            )
            """
        };

        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            for (String query : sql) {
                statement.execute(query);
            }

            // Keep older database files compatible.
            ensureColumn(connection, "expenses", "group_id",
                    "INTEGER REFERENCES groups(id)");
            ensureColumn(connection, "expenses", "payer_member_id",
                    "INTEGER REFERENCES group_members(id)");

            System.out.println("Settlr database is ready.");
        } catch (SQLException e) {
            System.out.println("Database initialization error: " + e.getMessage());
        }
    }

    private static void ensureColumn(
            Connection connection, String table,
            String column, String definition) throws SQLException {
        boolean exists = false;

        try (Statement statement = connection.createStatement();
             ResultSet result =
                     statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (result.next()) {
                if (column.equals(result.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }

        if (!exists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table
                        + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    // ---------- GROUPS AND MEMBERS ----------

    public static int createGroup(String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO groups(name) VALUES (?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name.trim());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not create group: " + e.getMessage());
        }
        return -1;
    }

    public static List<Group> getGroups() {
        List<Group> groups = new ArrayList<>();
        String sql = "SELECT id, name FROM groups ORDER BY id DESC";

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                groups.add(new Group(result.getInt("id"),
                        result.getString("name")));
            }
        } catch (SQLException e) {
            System.out.println("Could not load groups: " + e.getMessage());
        }
        return groups;
    }

    public static boolean addMember(int groupId, String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String sql = """
                INSERT INTO group_members(group_id, name)
                VALUES (?, ?)
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);
            statement.setString(2, name.trim());
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Could not add member: " + e.getMessage());
            return false;
        }
    }

    public static List<Member> getGroupMembers(int groupId) {
        List<Member> members = new ArrayList<>();
        String sql = """
                SELECT id, name FROM group_members
                WHERE group_id = ? ORDER BY id
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    members.add(new Member(result.getInt("id"),
                            result.getString("name")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not load members: " + e.getMessage());
        }
        return members;
    }

    public static List<String> getMembers(int groupId) {
        List<String> names = new ArrayList<>();
        for (Member member : getGroupMembers(groupId)) {
            names.add(member.getName());
        }
        return names;
    }

    // ---------- PERSONAL EXPENSES ----------

    public static boolean addExpense(
            String name, double amount, String payer) {
        if (name == null || name.isBlank() || payer == null
                || payer.isBlank() || !validAmount(amount)) {
            return false;
        }

        String sql = """
                INSERT INTO expenses(name, amount, payer)
                VALUES (?, ?, ?)
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.trim());
            statement.setDouble(2, amount);
            statement.setString(3, payer.trim());
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Could not save expense: " + e.getMessage());
            return false;
        }
    }

    public static List<String[]> getExpenses() {
        List<String[]> expenses = new ArrayList<>();
        String sql = """
                SELECT name, amount, payer FROM expenses ORDER BY id DESC
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                expenses.add(new String[] {
                    result.getString("name"),
                    String.valueOf(result.getDouble("amount")),
                    result.getString("payer")
                });
            }
        } catch (SQLException e) {
            System.out.println("Could not load expenses: " + e.getMessage());
        }
        return expenses;
    }

    public static boolean addPersonalExpense(
            String name, double amount, String category, String date) {
        if (name == null || name.isBlank() || category == null
                || category.isBlank() || date == null || !validAmount(amount)) {
            return false;
        }

        String sql = """
                INSERT INTO personal_expenses(name, amount, category, expense_date)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.trim());
            statement.setDouble(2, amount);
            statement.setString(3, category);
            statement.setString(4, date);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Could not save personal expense: " + e.getMessage());
            return false;
        }
    }

    public static boolean updatePersonalExpense(
            int id, String name, double amount, String category, String date) {
        if (id <= 0 || name == null || name.isBlank()
                || category == null || category.isBlank()
                || date == null || !validAmount(amount)) {
            return false;
        }

        String sql = """
                UPDATE personal_expenses
                SET name = ?, amount = ?, category = ?, expense_date = ?
                WHERE id = ?
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name.trim());
            statement.setDouble(2, amount);
            statement.setString(3, category);
            statement.setString(4, date);
            statement.setInt(5, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Could not update personal expense: " + e.getMessage());
            return false;
        }
    }

    public static boolean deletePersonalExpense(int id) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM personal_expenses WHERE id = ?")) {
            statement.setInt(1, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Could not delete personal expense: " + e.getMessage());
            return false;
        }
    }

    // Returns rows: [id, name, amount, category, date]
    // Empty date/category means no filter. Dates use ISO format YYYY-MM-DD.
    public static List<String[]> getPersonalExpenses(
            String startDate, String endDate, String category) {
        List<String[]> rows = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
                SELECT id, name, amount, category, expense_date
                FROM personal_expenses WHERE 1 = 1
                """);

        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND expense_date >= ?");
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND expense_date <= ?");
        }
        if (category != null && !category.isBlank()
                && !category.equals("All Categories")) {
            sql.append(" AND category = ?");
        }
        sql.append(" ORDER BY expense_date DESC, id DESC");

        try (Connection connection = connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {
            int index = 1;

            if (startDate != null && !startDate.isBlank()) {
                statement.setString(index++, startDate);
            }
            if (endDate != null && !endDate.isBlank()) {
                statement.setString(index++, endDate);
            }
            if (category != null && !category.isBlank()
                    && !category.equals("All Categories")) {
                statement.setString(index, category);
            }

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new String[] {
                        String.valueOf(result.getInt("id")),
                        result.getString("name"),
                        String.valueOf(result.getDouble("amount")),
                        result.getString("category"),
                        result.getString("expense_date")
                    });
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not filter personal expenses: " + e.getMessage());
        }
        return rows;
    }

    public static double getPersonalTotal(
            String startDate, String endDate, String category) {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(amount), 0) FROM personal_expenses WHERE 1=1");

        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND expense_date >= ?");
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND expense_date <= ?");
        }
        if (category != null && !category.isBlank()
                && !category.equals("All Categories")) {
            sql.append(" AND category = ?");
        }

        try (Connection connection = connect();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {
            int index = 1;

            if (startDate != null && !startDate.isBlank()) {
                statement.setString(index++, startDate);
            }
            if (endDate != null && !endDate.isBlank()) {
                statement.setString(index++, endDate);
            }
            if (category != null && !category.isBlank()
                    && !category.equals("All Categories")) {
                statement.setString(index, category);
            }

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            System.out.println("Could not calculate personal total: " + e.getMessage());
            return 0;
        }
    }

    // ---------- GROUP EXPENSES ----------

    public static boolean addGroupExpense(
            int groupId, String name, double amount,
            int payerMemberId, List<Integer> participants) {
        if (groupId <= 0 || name == null || name.isBlank()
                || !validAmount(amount) || payerMemberId <= 0
                || participants == null || participants.isEmpty()) {
            return false;
        }

        Set<Integer> uniqueIds = new HashSet<>(participants);
        if (uniqueIds.size() != participants.size()
                || uniqueIds.contains(null)
                || uniqueIds.stream().anyMatch(id -> id <= 0)) {
            return false;
        }

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);

            try {
                String payerName = getMemberName(
                        connection, groupId, payerMemberId);

                if (payerName == null) {
                    connection.rollback();
                    return false;
                }

                for (int memberId : participants) {
                    if (getMemberName(connection, groupId, memberId) == null) {
                        connection.rollback();
                        return false;
                    }
                }

                String sql = """
                        INSERT INTO expenses
                            (name, amount, payer, group_id, payer_member_id)
                        VALUES (?, ?, ?, ?, ?)
                        """;

                int expenseId;

                try (PreparedStatement statement = connection.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, name.trim());
                    statement.setDouble(2, amount);
                    statement.setString(3, payerName);
                    statement.setInt(4, groupId);
                    statement.setInt(5, payerMemberId);
                    statement.executeUpdate();

                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            connection.rollback();
                            return false;
                        }
                        expenseId = keys.getInt(1);
                    }
                }

                if (!insertSplits(connection, expenseId, amount, participants)) {
                    connection.rollback();
                    return false;
                }

                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                System.out.println("Could not save group expense: " + e.getMessage());
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return false;
        }
    }

    // Update a group expense and recalculate shares for its existing participants.
    public static boolean updateGroupExpense(
            int expenseId, int groupId, String name,
            double amount, int payerMemberId) {
        if (expenseId <= 0 || groupId <= 0 || name == null
                || name.isBlank() || !validAmount(amount) || payerMemberId <= 0) {
            return false;
        }

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);

            try {
                List<Integer> participants = new ArrayList<>();

                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT member_id FROM expense_splits
                        WHERE expense_id = ? ORDER BY id
                        """)) {
                    statement.setInt(1, expenseId);
                    try (ResultSet result = statement.executeQuery()) {
                        while (result.next()) {
                            participants.add(result.getInt("member_id"));
                        }
                    }
                }

                if (participants.isEmpty()
                        || !participants.contains(payerMemberId)) {
                    connection.rollback();
                    return false;
                }

                String payerName = getMemberName(
                        connection, groupId, payerMemberId);
                if (payerName == null) {
                    connection.rollback();
                    return false;
                }

                for (int memberId : participants) {
                    if (getMemberName(connection, groupId, memberId) == null) {
                        connection.rollback();
                        return false;
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE expenses
                        SET name = ?, amount = ?, payer = ?, payer_member_id = ?
                        WHERE id = ? AND group_id = ?
                        """)) {
                    statement.setString(1, name.trim());
                    statement.setDouble(2, amount);
                    statement.setString(3, payerName);
                    statement.setInt(4, payerMemberId);
                    statement.setInt(5, expenseId);
                    statement.setInt(6, groupId);

                    if (statement.executeUpdate() == 0) {
                        connection.rollback();
                        return false;
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM expense_splits WHERE expense_id = ?")) {
                    statement.setInt(1, expenseId);
                    statement.executeUpdate();
                }

                if (!insertSplits(connection, expenseId, amount, participants)) {
                    connection.rollback();
                    return false;
                }

                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                System.out.println("Could not update group expense: " + e.getMessage());
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteGroupExpense(int expenseId, int groupId) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM expenses WHERE id = ? AND group_id = ?")) {
            statement.setInt(1, expenseId);
            statement.setInt(2, groupId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Could not delete group expense: " + e.getMessage());
            return false;
        }
    }

    private static boolean insertSplits(
            Connection connection, int expenseId,
            double amount, List<Integer> participants) throws SQLException {
        if (participants.isEmpty()) {
            return false;
        }

        long cents = Math.round(amount * 100);
        if (Math.abs(amount * 100 - cents) > 0.0001) {
            return false;
        }

        long base = cents / participants.size();
        long remainder = cents % participants.size();

        String sql = """
                INSERT INTO expense_splits(expense_id, member_id, share_amount)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < participants.size(); i++) {
                long share = base + (i < remainder ? 1 : 0);
                statement.setInt(1, expenseId);
                statement.setInt(2, participants.get(i));
                statement.setDouble(3, share / 100.0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return true;
    }

    private static String getMemberName(
            Connection connection, int groupId, int memberId)
            throws SQLException {
        String sql = """
                SELECT name FROM group_members
                WHERE id = ? AND group_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, memberId);
            statement.setInt(2, groupId);

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString("name") : null;
            }
        }
    }

    // Rows: [id, name, amount, payer, payer_member_id]
    public static List<String[]> getGroupExpenses(int groupId) {
        List<String[]> expenses = new ArrayList<>();
        String sql = """
                SELECT id, name, amount, payer, payer_member_id
                FROM expenses WHERE group_id = ?
                ORDER BY id DESC
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    expenses.add(new String[] {
                        String.valueOf(result.getInt("id")),
                        result.getString("name"),
                        String.valueOf(result.getDouble("amount")),
                        result.getString("payer"),
                        String.valueOf(result.getInt("payer_member_id"))
                    });
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not load trip expenses: " + e.getMessage());
        }
        return expenses;
    }

    // ---------- BALANCES AND SETTLEMENTS ----------

    public static List<Balance> getGroupBalances(int groupId) {
        List<Balance> balances = new ArrayList<>();

        String sql = """
                SELECT
                    m.id,
                    m.name,
                    COALESCE((
                        SELECT SUM(e.amount) FROM expenses e
                        WHERE e.group_id = ? AND e.payer_member_id = m.id
                    ), 0) AS paid,
                    COALESCE((
                        SELECT SUM(s.share_amount)
                        FROM expense_splits s
                        JOIN expenses e ON e.id = s.expense_id
                        WHERE e.group_id = ? AND s.member_id = m.id
                    ), 0) AS share,
                    COALESCE((
                        SELECT SUM(st.amount) FROM settlements st
                        WHERE st.group_id = ? AND st.from_member_id = m.id
                    ), 0) AS sent,
                    COALESCE((
                        SELECT SUM(st.amount) FROM settlements st
                        WHERE st.group_id = ? AND st.to_member_id = m.id
                    ), 0) AS received
                FROM group_members m
                WHERE m.group_id = ?
                ORDER BY m.id
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 5; i++) {
                statement.setInt(i, groupId);
            }

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    balances.add(new Balance(
                            result.getInt("id"),
                            result.getString("name"),
                            result.getDouble("paid"),
                            result.getDouble("share"),
                            result.getDouble("sent"),
                            result.getDouble("received")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not calculate balances: " + e.getMessage());
        }
        return balances;
    }

    public static boolean recordSettlement(
            int groupId, int fromMemberId, int toMemberId, double amount) {
        if (groupId <= 0 || fromMemberId <= 0 || toMemberId <= 0
                || fromMemberId == toMemberId || !validAmount(amount)) {
            return false;
        }

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);

            try {
                if (getMemberName(connection, groupId, fromMemberId) == null
                        || getMemberName(connection, groupId, toMemberId) == null) {
                    connection.rollback();
                    return false;
                }

                String sql = """
                        INSERT INTO settlements
                            (group_id, from_member_id, to_member_id, amount)
                        VALUES (?, ?, ?, ?)
                        """;

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, groupId);
                    statement.setInt(2, fromMemberId);
                    statement.setInt(3, toMemberId);
                    statement.setDouble(4, amount);
                    statement.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                System.out.println("Could not record settlement: " + e.getMessage());
                return false;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Database error recording settlement: " + e.getMessage());
            return false;
        }
    }

    public static List<Settlement> getSettlements(int groupId) {
        List<Settlement> settlements = new ArrayList<>();

        String sql = """
                SELECT s.id, s.group_id, s.from_member_id, s.to_member_id,
                       fm.name AS from_name, tm.name AS to_name,
                       s.amount, s.settled_at
                FROM settlements s
                JOIN group_members fm ON fm.id = s.from_member_id
                JOIN group_members tm ON tm.id = s.to_member_id
                WHERE s.group_id = ?
                ORDER BY s.id DESC
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Timestamp timestamp =
                            Timestamp.valueOf(result.getString("settled_at")
                                    .replace('T', ' '));

                    settlements.add(new Settlement(
                            result.getInt("id"),
                            result.getInt("group_id"),
                            result.getInt("from_member_id"),
                            result.getInt("to_member_id"),
                            result.getString("from_name"),
                            result.getString("to_name"),
                            result.getDouble("amount"),
                            timestamp.toLocalDateTime()));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            System.out.println("Could not load settlements: " + e.getMessage());
        }
        return settlements;
    }

    // ---------- SHARES ----------

    public static List<double[]> getExpenseSplits(int expenseId) {
        List<double[]> splits = new ArrayList<>();
        String sql = """
                SELECT member_id, share_amount FROM expense_splits
                WHERE expense_id = ? ORDER BY id
                """;

        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, expenseId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    splits.add(new double[] {
                        result.getInt("member_id"),
                        result.getDouble("share_amount")
                    });
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not load shares: " + e.getMessage());
        }
        return splits;
    }

    private static boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount > 0
                && Math.abs(amount * 100 - Math.round(amount * 100)) < 0.0001;
    }
}
