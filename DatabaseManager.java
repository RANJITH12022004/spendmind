package com.example.spendmind;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseManager extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "spendmind.db";
    public static final int DATABASE_VERSION = 1;

    public DatabaseManager(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS transactions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "amount REAL, " +
                        "transaction_type TEXT, " +
                        "merchant TEXT, " +
                        "category TEXT, " +
                        "bank TEXT, " +
                        "account TEXT, " +
                        "reference TEXT, " +
                        "date TEXT" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS categories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT, " +
                        "icon TEXT, " +
                        "color TEXT" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS budgets (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "category TEXT, " +
                        "monthly_limit REAL, " +
                        "spent REAL, " +
                        "month INTEGER, " +
                        "year INTEGER" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS merchant_rules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "keyword TEXT, " +
                        "merchant_name TEXT, " +
                        "category TEXT" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS savings_goals (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "goal_name TEXT, " +
                        "target_amount REAL, " +
                        "saved_amount REAL, " +
                        "deadline TEXT" +
                        ")"
        );

        seedDefaultCategories(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS transactions");
        db.execSQL("DROP TABLE IF EXISTS categories");
        db.execSQL("DROP TABLE IF EXISTS budgets");
        db.execSQL("DROP TABLE IF EXISTS merchant_rules");
        db.execSQL("DROP TABLE IF EXISTS savings_goals");
        onCreate(db);
    }

    private void seedDefaultCategories(SQLiteDatabase db) {
        String[] defaultCategories = new String[]{
                "Food",
                "Transport",
                "Shopping",
                "Bills",
                "Rent",
                "Subscriptions",
                "Savings",
                "Investments",
                "Transfers",
                "Uncategorized"
        };

        for (String name : defaultCategories) {
            db.execSQL(
                    "INSERT INTO categories (name, icon, color) VALUES (?, ?, ?)",
                    new Object[]{name, null, null}
            );
        }
    }

    private SQLiteDatabase rw() {
        return getWritableDatabase();
    }

    private SQLiteDatabase ro() {
        return getReadableDatabase();
    }

    // --- Transactions ---

    public long insertTransaction(Models.Transaction transaction) {
        SQLiteDatabase db = rw();
        ContentValues values = new ContentValues();
        values.put("amount", transaction.getAmount());
        values.put("transaction_type", transaction.getTransactionType());
        values.put("merchant", transaction.getMerchant());
        values.put("category", transaction.getCategory());
        values.put("bank", transaction.getBank());
        values.put("account", transaction.getAccount());
        values.put("reference", transaction.getReference());
        values.put("date", transaction.getDate());

        long id = db.insert("transactions", null, values);
        transaction.setId(id);
        return id;
    }

    public int updateTransaction(Models.Transaction transaction) {
        SQLiteDatabase db = rw();
        ContentValues values = new ContentValues();
        values.put("amount", transaction.getAmount());
        values.put("transaction_type", transaction.getTransactionType());
        values.put("merchant", transaction.getMerchant());
        values.put("category", transaction.getCategory());
        values.put("bank", transaction.getBank());
        values.put("account", transaction.getAccount());
        values.put("reference", transaction.getReference());
        values.put("date", transaction.getDate());

        return db.update(
                "transactions",
                values,
                "id = ?",
                new String[]{String.valueOf(transaction.getId())}
        );
    }

    public int deleteTransaction(long id) {
        SQLiteDatabase db = rw();
        return db.delete("transactions", "id = ?", new String[]{String.valueOf(id)});
    }

    public Models.Transaction getTransactionById(long id) {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query(
                "transactions",
                null,
                "id = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        );

        try {
            if (cursor != null && cursor.moveToFirst()) {
                return mapCursorToTransaction(cursor);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public List<Models.Transaction> getAllTransactions() {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query("transactions", null, null, null, null, null, "date DESC");
        List<Models.Transaction> result = new ArrayList<>();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    result.add(mapCursorToTransaction(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public List<Models.Transaction> getTransactionsByDateRange(String startDate, String endDate) {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query(
                "transactions",
                null,
                "date BETWEEN ? AND ?",
                new String[]{startDate, endDate},
                null,
                null,
                "date DESC"
        );
        List<Models.Transaction> result = new ArrayList<>();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    result.add(mapCursorToTransaction(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public double getTotalForTypeInRange(String type, String startDate, String endDate) {
        SQLiteDatabase db = ro();
        Cursor cursor = db.rawQuery(
                "SELECT SUM(amount) FROM transactions WHERE transaction_type = ? AND date BETWEEN ? AND ?",
                new String[]{type, startDate, endDate}
        );
        try {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getDouble(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0d;
    }

    public Map<String, Double> getCategoryBreakdown(String startDate, String endDate) {
        SQLiteDatabase db = ro();
        Cursor cursor = db.rawQuery(
                "SELECT category, SUM(amount) FROM transactions WHERE transaction_type = 'debit' AND date BETWEEN ? AND ? GROUP BY category",
                new String[]{startDate, endDate}
        );
        Map<String, Double> result = new HashMap<>();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String category = cursor.getString(0);
                    if (category == null) {
                        category = "Uncategorized";
                    }
                    double sum = cursor.getDouble(1);
                    result.put(category, sum);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    private Models.Transaction mapCursorToTransaction(Cursor cursor) {
        Models.Transaction transaction = new Models.Transaction();
        transaction.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        transaction.setAmount(cursor.getDouble(cursor.getColumnIndexOrThrow("amount")));
        transaction.setTransactionType(cursor.getString(cursor.getColumnIndexOrThrow("transaction_type")));
        transaction.setMerchant(cursor.getString(cursor.getColumnIndexOrThrow("merchant")));
        transaction.setCategory(cursor.getString(cursor.getColumnIndexOrThrow("category")));
        transaction.setBank(cursor.getString(cursor.getColumnIndexOrThrow("bank")));
        transaction.setAccount(cursor.getString(cursor.getColumnIndexOrThrow("account")));
        transaction.setReference(cursor.getString(cursor.getColumnIndexOrThrow("reference")));
        transaction.setDate(cursor.getString(cursor.getColumnIndexOrThrow("date")));
        return transaction;
    }

    // --- Categories ---

    public long upsertCategory(Models.Category category) {
        SQLiteDatabase db = rw();
        ContentValues values = new ContentValues();
        values.put("name", category.getName());
        values.put("icon", category.getIcon());
        values.put("color", category.getColor());
        if (category.getId() > 0) {
            db.update("categories", values, "id = ?", new String[]{String.valueOf(category.getId())});
            return category.getId();
        } else {
            long id = db.insert("categories", null, values);
            category.setId(id);
            return id;
        }
    }

    public int deleteCategory(long id) {
        SQLiteDatabase db = rw();
        return db.delete("categories", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Models.Category> getAllCategories() {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query("categories", null, null, null, null, null, "name ASC");
        List<Models.Category> result = new ArrayList<>();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    result.add(mapCursorToCategory(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    private Models.Category mapCursorToCategory(Cursor cursor) {
        Models.Category category = new Models.Category();
        category.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
        category.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        category.setIcon(cursor.getString(cursor.getColumnIndexOrThrow("icon")));
        category.setColor(cursor.getString(cursor.getColumnIndexOrThrow("color")));
        return category;
    }

    // --- Budgets ---

    public long upsertBudget(Models.Budget budget) {
        SQLiteDatabase db = rw();
        ContentValues values = new ContentValues();
        values.put("category", budget.getCategory());
        values.put("monthly_limit", budget.getMonthlyLimit());
        values.put("spent", budget.getSpent());
        values.put("month", budget.getMonth());
        values.put("year", budget.getYear());
        if (budget.getId() > 0) {
            db.update("budgets", values, "id = ?", new String[]{String.valueOf(budget.getId())});
            return budget.getId();
        } else {
            long id = db.insert("budgets", null, values);
            budget.setId(id);
            return id;
        }
    }

    public int deleteBudget(long id) {
        SQLiteDatabase db = rw();
        return db.delete("budgets", "id = ?", new String[]{String.valueOf(id)});
    }

    public Models.Budget getBudgetForCategoryMonth(String category, int month, int year) {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query(
                "budgets",
                null,
                "category = ? AND month = ? AND year = ?",
                new String[]{category, String.valueOf(month), String.valueOf(year)},
                null,
                null,
                null
        );

        try {
            if (cursor != null && cursor.moveToFirst()) {
                Models.Budget budget = new Models.Budget();
                budget.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                budget.setCategory(cursor.getString(cursor.getColumnIndexOrThrow("category")));
                budget.setMonthlyLimit(cursor.getDouble(cursor.getColumnIndexOrThrow("monthly_limit")));
                budget.setSpent(cursor.getDouble(cursor.getColumnIndexOrThrow("spent")));
                budget.setMonth(cursor.getInt(cursor.getColumnIndexOrThrow("month")));
                budget.setYear(cursor.getInt(cursor.getColumnIndexOrThrow("year")));
                return budget;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public List<Models.Budget> getAllBudgets() {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query("budgets", null, null, null, null, null, "year DESC, month DESC");
        List<Models.Budget> result = new ArrayList<>();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Models.Budget budget = new Models.Budget();
                    budget.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                    budget.setCategory(cursor.getString(cursor.getColumnIndexOrThrow("category")));
                    budget.setMonthlyLimit(cursor.getDouble(cursor.getColumnIndexOrThrow("monthly_limit")));
                    budget.setSpent(cursor.getDouble(cursor.getColumnIndexOrThrow("spent")));
                    budget.setMonth(cursor.getInt(cursor.getColumnIndexOrThrow("month")));
                    budget.setYear(cursor.getInt(cursor.getColumnIndexOrThrow("year")));
                    result.add(budget);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public void updateBudgetSpent(String category, int month, int year, double delta) {
        Models.Budget budget = getBudgetForCategoryMonth(category, month, year);
        if (budget == null) {
            budget = new Models.Budget();
            budget.setCategory(category);
            budget.setMonthlyLimit(0d);
            budget.setSpent(Math.max(0d, delta));
            budget.setMonth(month);
            budget.setYear(year);
            upsertBudget(budget);
        } else {
            double newSpent = budget.getSpent() + delta;
            budget.setSpent(Math.max(0d, newSpent));
            upsertBudget(budget);
        }
    }

    // --- Savings goals ---

    public long upsertSavingsGoal(Models.SavingsGoal goal) {
        SQLiteDatabase db = rw();
        ContentValues values = new ContentValues();
        values.put("goal_name", goal.getGoalName());
        values.put("target_amount", goal.getTargetAmount());
        values.put("saved_amount", goal.getSavedAmount());
        values.put("deadline", goal.getDeadline());
        if (goal.getId() > 0) {
            db.update("savings_goals", values, "id = ?", new String[]{String.valueOf(goal.getId())});
            return goal.getId();
        } else {
            long id = db.insert("savings_goals", null, values);
            goal.setId(id);
            return id;
        }
    }

    public int deleteSavingsGoal(long id) {
        SQLiteDatabase db = rw();
        return db.delete("savings_goals", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Models.SavingsGoal> getActiveSavingsGoals() {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query("savings_goals", null, null, null, null, null, "deadline ASC");
        List<Models.SavingsGoal> result = new ArrayList<>();
        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Models.SavingsGoal goal = new Models.SavingsGoal();
                    goal.setId(cursor.getLong(cursor.getColumnIndexOrThrow("id")));
                    goal.setGoalName(cursor.getString(cursor.getColumnIndexOrThrow("goal_name")));
                    goal.setTargetAmount(cursor.getDouble(cursor.getColumnIndexOrThrow("target_amount")));
                    goal.setSavedAmount(cursor.getDouble(cursor.getColumnIndexOrThrow("saved_amount")));
                    goal.setDeadline(cursor.getString(cursor.getColumnIndexOrThrow("deadline")));
                    result.add(goal);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    // --- Merchant rules ---

    public long insertMerchantRule(String keyword, String merchantName, String category) {
        SQLiteDatabase db = rw();
        ContentValues values = new ContentValues();
        values.put("keyword", keyword);
        values.put("merchant_name", merchantName);
        values.put("category", category);
        return db.insert("merchant_rules", null, values);
    }

    public int deleteMerchantRule(long id) {
        SQLiteDatabase db = rw();
        return db.delete("merchant_rules", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Map<String, String>> getAllMerchantRules() {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query("merchant_rules", null, null, null, null, null, null);
        List<Map<String, String>> result = new ArrayList<>();

        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Map<String, String> rule = new HashMap<>();
                    rule.put("id", String.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow("id"))));
                    rule.put("keyword", cursor.getString(cursor.getColumnIndexOrThrow("keyword")));
                    rule.put("merchant_name", cursor.getString(cursor.getColumnIndexOrThrow("merchant_name")));
                    rule.put("category", cursor.getString(cursor.getColumnIndexOrThrow("category")));
                    result.add(rule);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public Map<String, String> findMerchantRuleForKeyword(String keyword) {
        SQLiteDatabase db = ro();
        Cursor cursor = db.query(
                "merchant_rules",
                null,
                "keyword = ?",
                new String[]{keyword},
                null,
                null,
                null
        );

        try {
            if (cursor != null && cursor.moveToFirst()) {
                Map<String, String> rule = new HashMap<>();
                rule.put("id", String.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow("id"))));
                rule.put("keyword", cursor.getString(cursor.getColumnIndexOrThrow("keyword")));
                rule.put("merchant_name", cursor.getString(cursor.getColumnIndexOrThrow("merchant_name")));
                rule.put("category", cursor.getString(cursor.getColumnIndexOrThrow("category")));
                return rule;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
}
