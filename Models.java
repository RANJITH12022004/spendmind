package com.example.spendmind;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class Models {

    private Models() {
    }

    public static class Transaction {

        private long id;
        private double amount;
        private String transactionType;
        private String merchant;
        private String category;
        private String bank;
        private String account;
        private String reference;
        private String date;

        public Transaction() {
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public String getTransactionType() {
            return transactionType;
        }

        public void setTransactionType(String transactionType) {
            this.transactionType = transactionType;
        }

        public String getMerchant() {
            return merchant;
        }

        public void setMerchant(String merchant) {
            this.merchant = merchant;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getBank() {
            return bank;
        }

        public void setBank(String bank) {
            this.bank = bank;
        }

        public String getAccount() {
            return account;
        }

        public void setAccount(String account) {
            this.account = account;
        }

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    public static class Category {

        private long id;
        private String name;
        private String icon;
        private String color;

        public Category() {
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }

    public static class Budget {

        private long id;
        private String category;
        private double monthlyLimit;
        private double spent;
        private int month;
        private int year;

        public Budget() {
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public double getMonthlyLimit() {
            return monthlyLimit;
        }

        public void setMonthlyLimit(double monthlyLimit) {
            this.monthlyLimit = monthlyLimit;
        }

        public double getSpent() {
            return spent;
        }

        public void setSpent(double spent) {
            this.spent = spent;
        }

        public int getMonth() {
            return month;
        }

        public void setMonth(int month) {
            this.month = month;
        }

        public int getYear() {
            return year;
        }

        public void setYear(int year) {
            this.year = year;
        }
    }

    public static class SavingsGoal {

        private long id;
        private String goalName;
        private double targetAmount;
        private double savedAmount;
        private String deadline;

        public SavingsGoal() {
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getGoalName() {
            return goalName;
        }

        public void setGoalName(String goalName) {
            this.goalName = goalName;
        }

        public double getTargetAmount() {
            return targetAmount;
        }

        public void setTargetAmount(double targetAmount) {
            this.targetAmount = targetAmount;
        }

        public double getSavedAmount() {
            return savedAmount;
        }

        public void setSavedAmount(double savedAmount) {
            this.savedAmount = savedAmount;
        }

        public String getDeadline() {
            return deadline;
        }

        public void setDeadline(String deadline) {
            this.deadline = deadline;
        }
    }

    public static class BudgetNotifier {

        private static final String CHANNEL_ID = "spendmind_budget";
        private static final int NOTIFICATION_ID_BASE = 2000;

        private final Context context;
        private final NotificationManagerCompat notificationManager;

        public BudgetNotifier(Context context) {
            this.context = context.getApplicationContext();
            this.notificationManager = NotificationManagerCompat.from(this.context);
            createChannel();
        }

        private void createChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Budget alerts",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.createNotificationChannel(channel);
                }
            }
        }

        public void notifyBudgetExceeded(String category, double limit, double spent) {
            String title = "Budget exceeded";
            String text = category + " spending exceeded your monthly budget (Limit ₹" + (long) limit + ", Spent ₹" + (long) spent + ").";

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.download)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

            try {
                int id = NOTIFICATION_ID_BASE + (category != null ? category.hashCode() : 0);
                notificationManager.notify(Math.abs(id), builder.build());
            } catch (SecurityException ignored) {
            }
        }
    }
}
