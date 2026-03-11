package com.example.spendmind;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified parser that handles bank detection, merchant resolution,
 * and SMS -> Transaction parsing.
 */
public class ParserEngine {

    private final DatabaseManager db;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(Rs\\.?|INR)\\s*([0-9]+(\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFERENCE_PATTERN =
            Pattern.compile("Ref[:\\s]*([A-Za-z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACCOUNT_PATTERN =
            Pattern.compile("A/?C\\s*X+([0-9]{4})", Pattern.CASE_INSENSITIVE);

    public ParserEngine(Context context) {
        this.db = new DatabaseManager(context.getApplicationContext());
    }

    public Models.Transaction parse(String messageBody, String originatingAddress, long timestampMillis) {
        if (messageBody == null) {
            return null;
        }

        Double amount = extractAmount(messageBody);
        if (amount == null) {
            return null;
        }

        String type = extractType(messageBody);
        String account = extractAccount(messageBody);
        String reference = extractReference(messageBody);
        String bank = detectBank(messageBody);

        MerchantInfo resolvedMerchant = resolveMerchant(messageBody);

        Models.Transaction transaction = new Models.Transaction();
        transaction.setAmount(amount);
        transaction.setTransactionType(type);
        transaction.setAccount(account);
        transaction.setReference(reference);
        transaction.setBank(bank);
        transaction.setMerchant(
                resolvedMerchant != null ? resolvedMerchant.name : originatingAddress
        );
        transaction.setCategory(
                resolvedMerchant != null ? resolvedMerchant.categoryHint : null
        );

        String dateString = dateFormat.format(new Date(timestampMillis));
        transaction.setDate(dateString);

        return transaction;
    }

    // --- Amount / type / account / reference helpers ---

    private Double extractAmount(String message) {
        Matcher matcher = AMOUNT_PATTERN.matcher(message);
        if (matcher.find()) {
            String value = matcher.group(2);
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String extractType(String message) {
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains(" dr") || lower.contains(" debited") || lower.contains(" spent")) {
            return "debit";
        }
        if (lower.contains(" cr") || lower.contains(" credited")) {
            return "credit";
        }
        return "debit";
    }

    private String extractAccount(String message) {
        Matcher matcher = ACCOUNT_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractReference(String message) {
        Matcher matcher = REFERENCE_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    // --- Bank detection (from BankDetector) ---

    private String detectBank(String message) {
        if (message == null) {
            return "Unknown";
        }

        String upper = message.toUpperCase(Locale.US);

        if (upper.contains("BOB") || upper.contains("BANK OF BARODA")) {
            return "Bank of Baroda";
        }
        if (upper.contains("SBI") || upper.contains("STATE BANK OF INDIA")) {
            return "State Bank of India";
        }
        if (upper.contains("HDFC")) {
            return "HDFC Bank";
        }
        if (upper.contains("ICICI")) {
            return "ICICI Bank";
        }
        if (upper.contains("AXIS")) {
            return "Axis Bank";
        }

        return "Unknown";
    }

    // --- Merchant resolution (from MerchantResolver) ---

    private static class MerchantInfo {
        final String name;
        final String categoryHint;

        MerchantInfo(String name, String categoryHint) {
            this.name = name;
            this.categoryHint = categoryHint;
        }
    }

    private MerchantInfo resolveMerchant(String message) {
        if (message == null) {
            return null;
        }

        String lower = message.toLowerCase(Locale.US);

        // 1. Try explicit merchant rules from DB
        List<Map<String, String>> rules = db.getAllMerchantRules();
        for (Map<String, String> rule : rules) {
            String keyword = rule.get("keyword");
            if (keyword != null && lower.contains(keyword.toLowerCase(Locale.US))) {
                String merchantName = rule.get("merchant_name");
                String category = rule.get("category");
                return new MerchantInfo(merchantName, category);
            }
        }

        // 2. Fallback UPI heuristic
        String upiId = extractUpiId(lower);
        if (upiId != null) {
            String normalized = normalizeUpiMerchant(upiId);
            return new MerchantInfo(normalized, null);
        }

        return null;
    }

    private String extractUpiId(String text) {
        int atIndex = text.indexOf('@');
        if (atIndex <= 0) {
            return null;
        }

        int start = atIndex - 1;
        while (start >= 0) {
            char c = text.charAt(start);
            if (!Character.isLetterOrDigit(c) && c != '.' && c != '_' && c != '-') {
                break;
            }
            start--;
        }

        start = start + 1;
        if (start >= atIndex) {
            return null;
        }
        return text.substring(start, atIndex);
    }

    private String normalizeUpiMerchant(String upiPrefix) {
        if (upiPrefix == null) {
            return "Unknown Merchant";
        }

        String lower = upiPrefix.toLowerCase(Locale.US);

        if (lower.startsWith("bharatpe")) {
            return "BharatPe Merchant";
        }
        if (lower.startsWith("paytmqr") || lower.startsWith("paytm")) {
            return "Paytm QR";
        }
        if (lower.contains("amazon")) {
            return "Amazon";
        }

        return upiPrefix;
    }
}
