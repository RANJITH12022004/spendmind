package com.example.spendmind;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.content.IntentFilter;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SMS_PERMISSIONS = 1001;
    private static final String PREFS_NAME = "SpendMind";
    private static final String KEY_ASKED_SMS_SCAN = "asked_sms_scan";
    public static final String ACTION_DATA_CHANGED = "com.example.spendmind.ACTION_DATA_CHANGED";

    private WebView webView;
    private final BroadcastReceiver dataChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_DATA_CHANGED.equals(intent.getAction()) && webView != null) {
                runOnUiThread(() -> webView.evaluateJavascript(
                        "window.SpendMindOnDataChanged && window.SpendMindOnDataChanged('sms')",
                        null
                ));
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        configureWebView();
        requestSmsPermissionsIfNeeded();
        maybeAskToScanSmsHistory();

        // Listen for data-change broadcasts from SMSReceiver / history imports
        IntentFilter filter = new IntentFilter(ACTION_DATA_CHANGED);
        registerReceiver(dataChangedReceiver, filter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(dataChangedReceiver);
        } catch (IllegalArgumentException ignored) {
            // receiver may already be unregistered
        }
    }

    private void configureWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new WebAppInterface(this), "SpendMind");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestSmsPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        boolean receiveGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                        == PackageManager.PERMISSION_GRANTED;
        boolean readGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                        == PackageManager.PERMISSION_GRANTED;

        if (!receiveGranted || !readGranted) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                    },
                    REQUEST_SMS_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SMS_PERMISSIONS && hasSmsPermissions()) {
            maybeAskToScanSmsHistory();
        }
    }

    private boolean hasSmsPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void maybeAskToScanSmsHistory() {
        if (!hasSmsPermissions()) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_ASKED_SMS_SCAN, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_ASKED_SMS_SCAN, true).apply();

        new AlertDialog.Builder(this)
                .setTitle("Scan past SMS?")
                .setMessage("Do you want SpendMind to scan your message inbox for past transaction SMS? This helps build your history.")
                .setPositiveButton("Yes", (dialog, which) -> runSmsHistoryImport())
                .setNegativeButton("No", null)
                .show();
    }

    private void runSmsHistoryImport() {
        Executors.newSingleThreadExecutor().execute(() -> {
            SmsHistoryImporter importer = new SmsHistoryImporter(this);
            importer.importInbox();
            sendDataChangedBroadcast(this);
        });
    }

    public static void sendDataChangedBroadcast(Context context) {
        Intent intent = new Intent(ACTION_DATA_CHANGED);
        context.sendBroadcast(intent);
    }

    public static class SMSReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
                return;
            }

            Bundle bundle = intent.getExtras();
            if (bundle == null) {
                return;
            }

            Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus == null || pdus.length == 0) {
                return;
            }

            ParserEngine parser = new ParserEngine(context);
            DatabaseManager db = new DatabaseManager(context);
            AnalyticsEngine analyticsEngine = new AnalyticsEngine(context);
            Models.BudgetNotifier notifier = new Models.BudgetNotifier(context);

            for (Object pdu : pdus) {
                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                String messageBody = smsMessage.getMessageBody();
                String originatingAddress = smsMessage.getOriginatingAddress();
                long timestampMillis = smsMessage.getTimestampMillis();

                Models.Transaction tx = parser.parse(messageBody, originatingAddress, timestampMillis);
                if (tx == null) {
                    continue;
                }

                long id = db.insertTransaction(tx);
                if (id <= 0) {
                    continue;
                }

                if ("debit".equalsIgnoreCase(tx.getTransactionType())) {
                    String category = tx.getCategory() != null ? tx.getCategory() : "Uncategorized";
                    Calendar cal = Calendar.getInstance();
                    cal.setTimeInMillis(timestampMillis);
                    int month = cal.get(Calendar.MONTH) + 1;
                    int year = cal.get(Calendar.YEAR);
                    db.updateBudgetSpent(category, month, year, tx.getAmount());

                    List<AnalyticsEngine.BudgetAlert> exceeded =
                            analyticsEngine.getExceededBudgets(month, year);
                    for (AnalyticsEngine.BudgetAlert alert : exceeded) {
                        notifier.notifyBudgetExceeded(alert.category, alert.limit, alert.spent);
                    }
                }
            }
            MainActivity.sendDataChangedBroadcast(context);
        }
    }

    public static class SmsHistoryImporter {

        private static final Uri SMS_INBOX_URI = Uri.parse("content://sms/inbox");
        private static final int MAX_MESSAGES = 500;

        private final Context context;
        private final ParserEngine parser;
        private final DatabaseManager db;

        public SmsHistoryImporter(Context context) {
            this.context = context.getApplicationContext();
            this.parser = new ParserEngine(this.context);
            this.db = new DatabaseManager(this.context);
        }

        public int importInbox() {
            String[] projection = new String[]{
                    Telephony.Sms.BODY,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.DATE
            };

            Cursor cursor = null;
            int imported = 0;

            try {
                cursor = context.getContentResolver().query(
                        SMS_INBOX_URI,
                        projection,
                        null,
                        null,
                        Telephony.Sms.DATE + " DESC"
                );

                if (cursor == null) {
                    return 0;
                }

                int count = 0;
                while (cursor.moveToNext() && count < MAX_MESSAGES) {
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    long dateMs = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE));

                    if (body == null || body.trim().isEmpty()) {
                        continue;
                    }

                    Models.Transaction tx = parser.parse(body, address, dateMs);
                    if (tx == null) {
                        continue;
                    }

                    long id = db.insertTransaction(tx);
                    if (id > 0) {
                        imported++;
                        if ("debit".equalsIgnoreCase(tx.getTransactionType())) {
                            String category = tx.getCategory() != null ? tx.getCategory() : "Uncategorized";
                            Calendar cal = Calendar.getInstance();
                            cal.setTimeInMillis(dateMs);
                            int month = cal.get(Calendar.MONTH) + 1;
                            int year = cal.get(Calendar.YEAR);
                            db.updateBudgetSpent(category, month, year, tx.getAmount());
                        }
                    }
                    count++;
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            return imported;
        }
    }

    public static class WebAppInterface {

        private final Context context;
        private final DatabaseManager db;
        private final AnalyticsEngine analyticsEngine;

        public WebAppInterface(Context context) {
            this.context = context.getApplicationContext();
            this.db = new DatabaseManager(this.context);
            this.analyticsEngine = new AnalyticsEngine(this.context);
        }

        @JavascriptInterface
        public String getDashboardData(String filterJson) {
            try {
                String filter = "monthly";
                if (filterJson != null && !filterJson.isEmpty()) {
                    JSONObject obj = new JSONObject(filterJson);
                    filter = obj.optString("filter", "monthly");
                }
                String[] range = getDateRange(filter);
                String start = range[0];
                String end = range[1];

                AnalyticsEngine.Totals totals = analyticsEngine.getTotals(start, end);
                Map<String, Double> categoryBreakdown = analyticsEngine.getCategoryBreakdown(start, end);
                List<AnalyticsEngine.MonthSummary> trend = analyticsEngine.getMonthlySpendingTrend(6);
                List<String> insights = analyticsEngine.getInsightsForPeriod(start, end);
                List<Models.Transaction> recent = db.getAllTransactions();
                int limit = Math.min(5, recent.size());

                JSONObject root = new JSONObject();
                root.put("totalIncome", totals.totalIncome);
                root.put("totalExpenses", totals.totalExpenses);
                root.put("projectedMonthlySpend", totals.projectedMonthlySpend);

                JSONObject breakdown = new JSONObject();
                for (Map.Entry<String, Double> e : categoryBreakdown.entrySet()) {
                    breakdown.put(e.getKey(), e.getValue());
                }
                root.put("categoryBreakdown", breakdown);

                JSONArray trendArr = new JSONArray();
                for (AnalyticsEngine.MonthSummary m : trend) {
                    trendArr.put(new JSONObject().put("label", m.label).put("value", m.value));
                }
                root.put("spendingTrend", trendArr);

                JSONArray insightsArr = new JSONArray();
                for (String s : insights) {
                    insightsArr.put(s);
                }
                root.put("insights", insightsArr);

                JSONArray recentArr = new JSONArray();
                for (int i = 0; i < limit; i++) {
                    Models.Transaction t = recent.get(i);
                    recentArr.put(transactionToJson(t));
                }
                root.put("recentTransactions", recentArr);

                return root.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String getTransactions(String filterJson) {
            try {
                String start;
                String end;
                if (filterJson != null && !filterJson.isEmpty()) {
                    JSONObject obj = new JSONObject(filterJson);
                    if (obj.has("startDate") && obj.has("endDate")) {
                        start = obj.getString("startDate");
                        end = obj.getString("endDate");
                    } else {
                        String filter = obj.optString("filter", "monthly");
                        String[] range = getDateRange(filter);
                        start = range[0];
                        end = range[1];
                    }
                } else {
                    String[] range = getDateRange("monthly");
                    start = range[0];
                    end = range[1];
                }
                List<Models.Transaction> list = db.getTransactionsByDateRange(start, end);
                JSONArray arr = new JSONArray();
                for (Models.Transaction t : list) {
                    arr.put(transactionToJson(t));
                }
                return arr.toString();
            } catch (JSONException e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String getBudgets() {
            try {
                List<Models.Budget> list = db.getAllBudgets();
                Calendar cal = Calendar.getInstance();
                int month = cal.get(Calendar.MONTH) + 1;
                int year = cal.get(Calendar.YEAR);
                JSONArray arr = new JSONArray();
                for (Models.Budget b : list) {
                    if (b.getMonth() != month || b.getYear() != year) continue;
                    AnalyticsEngine.BudgetStatus status = analyticsEngine.getBudgetStatus(b.getCategory(), month, year);
                    JSONObject o = new JSONObject();
                    o.put("id", b.getId());
                    o.put("category", b.getCategory());
                    o.put("monthlyLimit", b.getMonthlyLimit());
                    o.put("spent", status.spent);
                    o.put("month", b.getMonth());
                    o.put("year", b.getYear());
                    arr.put(o);
                }
                return arr.toString();
            } catch (JSONException e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String getSavingsGoals() {
            try {
                List<Models.SavingsGoal> list = db.getActiveSavingsGoals();
                JSONArray arr = new JSONArray();
                for (Models.SavingsGoal g : list) {
                    arr.put(new JSONObject()
                            .put("id", g.getId())
                            .put("goalName", g.getGoalName())
                            .put("targetAmount", g.getTargetAmount())
                            .put("savedAmount", g.getSavedAmount())
                            .put("deadline", g.getDeadline() != null ? g.getDeadline() : ""));
                }
                return arr.toString();
            } catch (JSONException e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void saveTransaction(String transactionJson) {
            try {
                if (transactionJson == null || transactionJson.isEmpty()) return;
                JSONObject o = new JSONObject(transactionJson);
                Models.Transaction t = new Models.Transaction();
                if (o.has("id") && !o.isNull("id")) {
                    t.setId(o.getLong("id"));
                }
                t.setAmount(o.optDouble("amount", 0));
                t.setTransactionType(o.optString("transactionType", "debit"));
                t.setMerchant(o.optString("merchant", ""));
                String category = o.optString("category", "");
                if (category == null || category.trim().isEmpty()) {
                    t.setCategory(null);
                } else {
                    t.setCategory(category);
                }
                t.setBank(o.optString("bank", ""));
                t.setAccount(o.optString("account", ""));
                t.setReference(o.optString("reference", ""));
                t.setDate(o.optString("date", todayStr()));

                if (t.getId() > 0) {
                    db.updateTransaction(t);
                } else {
                    db.insertTransaction(t);
                    if ("debit".equalsIgnoreCase(t.getTransactionType())) {
                        int month = monthFromDateStr(t.getDate());
                        int year = yearFromDateStr(t.getDate());
                        String budgetCategory = t.getCategory() != null ? t.getCategory() : "Uncategorized";
                        db.updateBudgetSpent(budgetCategory, month, year, t.getAmount());
                    }
                }
            } catch (JSONException ignored) {
            }
        }

        @JavascriptInterface
        public String getCategories() {
            try {
                List<Models.Category> list = db.getAllCategories();
                JSONArray arr = new JSONArray();
                for (Models.Category c : list) {
                    arr.put(new JSONObject()
                            .put("id", c.getId())
                            .put("name", c.getName())
                            .put("icon", c.getIcon() != null ? c.getIcon() : "")
                            .put("color", c.getColor() != null ? c.getColor() : ""));
                }
                return arr.toString();
            } catch (JSONException e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String getMerchantRules() {
            try {
                List<Map<String, String>> list = db.getAllMerchantRules();
                JSONArray arr = new JSONArray();
                for (Map<String, String> r : list) {
                    arr.put(new JSONObject()
                            .put("id", r.get("id"))
                            .put("keyword", r.get("keyword"))
                            .put("merchantName", r.get("merchant_name"))
                            .put("category", r.get("category")));
                }
                return arr.toString();
            } catch (JSONException e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void deleteMerchantRule(long id) {
            db.deleteMerchantRule(id);
        }

        @JavascriptInterface
        public void updateSettings(String settingsJson) {
            try {
                if (settingsJson == null || settingsJson.isEmpty()) return;
                JSONObject root = new JSONObject(settingsJson);

                if (root.has("categories")) {
                    JSONArray arr = root.getJSONArray("categories");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject c = arr.getJSONObject(i);
                        Models.Category cat = new Models.Category();
                        if (c.has("id") && !c.isNull("id")) cat.setId(c.getLong("id"));
                        cat.setName(c.optString("name", ""));
                        cat.setIcon(c.optString("icon", ""));
                        cat.setColor(c.optString("color", ""));
                        db.upsertCategory(cat);
                    }
                }
                if (root.has("merchantRules")) {
                    JSONArray arr = root.getJSONArray("merchantRules");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject r = arr.getJSONObject(i);
                        String keyword = r.optString("keyword", "");
                        String merchantName = r.optString("merchantName", "");
                        String category = r.optString("category", "");
                        if (!keyword.isEmpty()) db.insertMerchantRule(keyword, merchantName, category);
                    }
                }
                if (root.has("budgets")) {
                    JSONArray arr = root.getJSONArray("budgets");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject b = arr.getJSONObject(i);
                        Models.Budget budget = new Models.Budget();
                        if (b.has("id") && !b.isNull("id")) budget.setId(b.getLong("id"));
                        String category = b.optString("category", "");
                        if (category == null || category.trim().isEmpty()) {
                            budget.setCategory("Uncategorized");
                        } else {
                            budget.setCategory(category);
                        }
                        budget.setMonthlyLimit(b.optDouble("monthlyLimit", 0));
                        budget.setSpent(b.optDouble("spent", 0));
                        budget.setMonth(b.optInt("month", Calendar.getInstance().get(Calendar.MONTH) + 1));
                        budget.setYear(b.optInt("year", Calendar.getInstance().get(Calendar.YEAR)));
                        db.upsertBudget(budget);
                    }
                }
                if (root.has("savingsGoals")) {
                    JSONArray arr = root.getJSONArray("savingsGoals");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject g = arr.getJSONObject(i);
                        Models.SavingsGoal goal = new Models.SavingsGoal();
                        if (g.has("id") && !g.isNull("id")) goal.setId(g.getLong("id"));
                        goal.setGoalName(g.optString("goalName", ""));
                        goal.setTargetAmount(g.optDouble("targetAmount", 0));
                        goal.setSavedAmount(g.optDouble("savedAmount", 0));
                        goal.setDeadline(g.optString("deadline", ""));
                        db.upsertSavingsGoal(goal);
                    }
                }
            } catch (JSONException ignored) {
            }
        }

        @JavascriptInterface
        public void deleteCategory(long id) {
            db.deleteCategory(id);
        }

        @JavascriptInterface
        public void deleteSavingsGoal(long id) {
            db.deleteSavingsGoal(id);
        }

        @JavascriptInterface
        public void runSmsHistoryImport() {
            Executors.newSingleThreadExecutor().execute(() -> {
                SmsHistoryImporter importer = new SmsHistoryImporter(context);
                importer.importInbox();
                MainActivity.sendDataChangedBroadcast(context);
            });
        }

        private String[] getDateRange(String filter) {
            Calendar cal = Calendar.getInstance(Locale.US);
            String end = String.format(Locale.US, "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            String start;
            switch (filter != null ? filter : "monthly") {
                case "daily":
                    start = end;
                    break;
                case "weekly":
                    cal.add(Calendar.DAY_OF_YEAR, -6);
                    start = String.format(Locale.US, "%04d-%02d-%02d",
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
                    break;
                case "yearly":
                    start = String.format(Locale.US, "%04d-01-01", cal.get(Calendar.YEAR));
                    break;
                default:
                    start = String.format(Locale.US, "%04d-%02d-01",
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1);
                    break;
            }
            return new String[]{start, end};
        }

        private JSONObject transactionToJson(Models.Transaction t) throws JSONException {
            return new JSONObject()
                    .put("id", t.getId())
                    .put("date", t.getDate())
                    .put("merchant", t.getMerchant() != null ? t.getMerchant() : "")
                    .put("category", t.getCategory() != null ? t.getCategory() : "Uncategorized")
                    .put("amount", t.getAmount())
                    .put("transactionType", t.getTransactionType() != null ? t.getTransactionType() : "debit");
        }

        private String todayStr() {
            Calendar cal = Calendar.getInstance();
            return String.format(Locale.US, "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
        }

        private int monthFromDateStr(String date) {
            if (date == null || date.length() < 7) return Calendar.getInstance().get(Calendar.MONTH) + 1;
            try {
                return Integer.parseInt(date.substring(5, 7));
            } catch (Exception e) {
                return Calendar.getInstance().get(Calendar.MONTH) + 1;
            }
        }

        private int yearFromDateStr(String date) {
            if (date == null || date.length() < 4) return Calendar.getInstance().get(Calendar.YEAR);
            try {
                return Integer.parseInt(date.substring(0, 4));
            } catch (Exception e) {
                return Calendar.getInstance().get(Calendar.YEAR);
            }
        }
    }
}

