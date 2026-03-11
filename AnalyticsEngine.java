package com.example.spendmind;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsEngine {

    private final DatabaseManager db;

    public AnalyticsEngine(Context context) {
        this.db = new DatabaseManager(context.getApplicationContext());
    }

    // --- Totals / spending ---

    public Totals getTotals(String startDate, String endDate) {
        double income = getTotalIncome(startDate, endDate);
        double expenses = getTotalExpenses(startDate, endDate);
        double projected = getProjectedMonthlySpend(startDate, endDate);
        return new Totals(income, expenses, projected);
    }

    public double getTotalIncome(String startDate, String endDate) {
        return db.getTotalForTypeInRange("credit", startDate, endDate);
    }

    public double getTotalExpenses(String startDate, String endDate) {
        return db.getTotalForTypeInRange("debit", startDate, endDate);
    }

    public double getDailyAverageSpend(String startDate, String endDate) {
        double total = getTotalExpenses(startDate, endDate);
        long days = daysBetween(startDate, endDate);
        return days > 0 ? total / days : 0;
    }

    public double getProjectedMonthlySpend(String startDate, String endDate) {
        return getDailyAverageSpend(startDate, endDate) * 30;
    }

    public Map<String, Double> getCategoryBreakdown(String startDate, String endDate) {
        return db.getCategoryBreakdown(startDate, endDate);
    }

    private static long daysBetween(String start, String end) {
        try {
            long startMs = java.sql.Date.valueOf(start).getTime();
            long endMs = java.sql.Date.valueOf(end).getTime();
            return Math.max(1, (endMs - startMs) / (24 * 60 * 60 * 1000));
        } catch (Exception e) {
            return 1;
        }
    }

    // --- Trends ---

    public List<MonthSummary> getMonthlySpendingTrend(int monthsBack) {
        List<MonthSummary> result = new ArrayList<>();
        Calendar cal = Calendar.getInstance(Locale.US);

        for (int i = 0; i < monthsBack; i++) {
            int month = cal.get(Calendar.MONTH) + 1;
            int year = cal.get(Calendar.YEAR);
            String start = String.format(Locale.US, "%04d-%02d-01", year, month);
            int lastDay;
            Calendar tmp = (Calendar) cal.clone();
            lastDay = tmp.getActualMaximum(Calendar.DAY_OF_MONTH);
            String end = String.format(Locale.US, "%04d-%02d-%02d", year, month, lastDay);

            double total = db.getTotalForTypeInRange("debit", start, end);
            String label = String.format(Locale.US, "%04d-%02d", year, month);
            result.add(0, new MonthSummary(label, total));
            cal.add(Calendar.MONTH, -1);
        }
        return result;
    }

    public double getWeeklyChangeForCategory(String category) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance(Locale.US);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        String today = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
        String thisWeekStart = sdf.format(cal.getTime());
        String thisWeekEnd = today;

        cal.add(Calendar.DAY_OF_YEAR, -1);
        String prevWeekEnd = sdf.format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -6);
        String prevWeekStart = sdf.format(cal.getTime());

        double thisWeek = getCategorySpendInRange(category, thisWeekStart, thisWeekEnd);
        double prevWeek = getCategorySpendInRange(category, prevWeekStart, prevWeekEnd);

        if (prevWeek == 0) {
            return thisWeek > 0 ? 100 : 0;
        }
        return ((thisWeek - prevWeek) / prevWeek) * 100;
    }

    private double getCategorySpendInRange(String category, String start, String end) {
        List<Models.Transaction> list =
                db.getTransactionsByDateRange(start, end);
        double sum = 0;
        String cat = category != null ? category : "Uncategorized";
        for (Models.Transaction t : list) {
            if ("debit".equalsIgnoreCase(t.getTransactionType())) {
                String c = t.getCategory() != null ? t.getCategory() : "Uncategorized";
                if (cat.equals(c)) {
                    sum += t.getAmount();
                }
            }
        }
        return sum;
    }

    // --- Budget status & alerts ---

    public BudgetStatus getBudgetStatus(String category, int month, int year) {
        Models.Budget budget =
                db.getBudgetForCategoryMonth(category, month, year);
        String start = String.format(Locale.US, "%04d-%02d-01", year, month);
        Calendar c = Calendar.getInstance();
        c.set(year, month - 1, 1);
        int lastDay;
        if (month == Calendar.getInstance().get(Calendar.MONTH) + 1
                && year == Calendar.getInstance().get(Calendar.YEAR)) {
            lastDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        } else {
            lastDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        }
        String end = String.format(Locale.US, "%04d-%02d-%02d", year, month, lastDay);

        double spent = 0;
        for (Models.Transaction t : db.getTransactionsByDateRange(start, end)) {
            if ("debit".equalsIgnoreCase(t.getTransactionType())) {
                String cat = t.getCategory() != null ? t.getCategory() : "Uncategorized";
                if (category.equals(cat)) {
                    spent += t.getAmount();
                }
            }
        }
        double limit = budget != null ? budget.getMonthlyLimit() : 0;
        double remaining = limit - spent;
        double percentUsed = limit > 0 ? (spent / limit) * 100 : 0;
        return new BudgetStatus(limit, spent, remaining, percentUsed);
    }

    public List<BudgetAlert> getExceededBudgets(int month, int year) {
        List<Models.Budget> budgets = db.getAllBudgets();
        List<BudgetAlert> exceeded = new ArrayList<>();
        for (Models.Budget b : budgets) {
            if (b.getMonth() != month || b.getYear() != year) {
                continue;
            }
            BudgetStatus status = getBudgetStatus(b.getCategory(), month, year);
            if (b.getMonthlyLimit() > 0 && status.spent > b.getMonthlyLimit()) {
                exceeded.add(new BudgetAlert(b.getCategory(), b.getMonthlyLimit(), status.spent));
            }
        }
        return exceeded;
    }

    // --- Insights ---

    public List<String> getInsightsForPeriod(String startDate, String endDate) {
        List<String> lines = new ArrayList<>();
        double projected = getProjectedMonthlySpend(startDate, endDate);
        lines.add(String.format(Locale.US, "Your projected monthly spending is ₹%.0f", projected));

        Calendar cal = Calendar.getInstance();
        String today = String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        if (today.equals(endDate)) {
            double todaySpend = getTotalExpenses(today, today);
            lines.add(String.format(Locale.US, "You spent ₹%.0f today", todaySpend));
        }

        for (String category : new String[]{"Food", "Transport", "Shopping"}) {
            double pct = getWeeklyChangeForCategory(category);
            if (pct != 0) {
                String dir = pct > 0 ? "increased" : "decreased";
                lines.add(String.format(Locale.US, "%s spending %s %.0f%% this week",
                        category, dir, Math.abs(pct)));
            }
        }
        return lines;
    }

    // --- DTOs ---

    public static class Totals {
        public final double totalIncome;
        public final double totalExpenses;
        public final double projectedMonthlySpend;

        public Totals(double totalIncome, double totalExpenses, double projectedMonthlySpend) {
            this.totalIncome = totalIncome;
            this.totalExpenses = totalExpenses;
            this.projectedMonthlySpend = projectedMonthlySpend;
        }
    }

    public static class MonthSummary {
        public final String label;
        public final double value;

        public MonthSummary(String label, double value) {
            this.label = label;
            this.value = value;
        }
    }

    public static class BudgetStatus {
        public final double limit;
        public final double spent;
        public final double remaining;
        public final double percentUsed;

        public BudgetStatus(double limit, double spent, double remaining, double percentUsed) {
            this.limit = limit;
            this.spent = spent;
            this.remaining = remaining;
            this.percentUsed = percentUsed;
        }
    }

    public static class BudgetAlert {
        public final String category;
        public final double limit;
        public final double spent;

        public BudgetAlert(String category, double limit, double spent) {
            this.category = category;
            this.limit = limit;
            this.spent = spent;
        }
    }
}
