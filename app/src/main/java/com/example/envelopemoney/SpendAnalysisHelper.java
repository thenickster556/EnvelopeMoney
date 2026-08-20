package com.example.envelopemoney;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Spend-over-time analysis for selected ponds. Over-budget uses {@link Envelope#getLimit()}
 * (user monthly budget), not payday Remaining and not {@code MonthData.limit}.
 */
public final class SpendAnalysisHelper {

    public static final int DEFAULT_LAST_N = 3;
    public static final double MIN_BAR_SCALE = 0.01d;
    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");

    private SpendAnalysisHelper() {
    }

    public static int normalizeLastN(int lastNMonths) {
        if (lastNMonths == 6 || lastNMonths == 12) {
            return lastNMonths;
        }
        return DEFAULT_LAST_N;
    }

    public static boolean countsAsSpend(Transaction transaction, boolean includeTransfers) {
        if (transaction == null) {
            return false;
        }
        if (includeTransfers) {
            return true;
        }
        String transferId = transaction.getTransferId();
        return transferId == null || transferId.trim().isEmpty();
    }

    public static List<String> monthKeys(String endMonthYyyyMm, int lastNMonths) {
        String end = normalizeMonth(endMonthYyyyMm);
        int count = normalizeLastN(lastNMonths);
        List<String> keys = new ArrayList<>();
        if (end == null) {
            return keys;
        }
        for (int i = count - 1; i >= 0; i--) {
            keys.add(addMonths(end, -i));
        }
        return keys;
    }

    public static SpendAnalysisResult analyze(List<Envelope> envelopes, SpendAnalysisQuery query) {
        SpendAnalysisQuery safeQuery = query == null
                ? new SpendAnalysisQuery(null, DEFAULT_LAST_N, Collections.emptyList(), false)
                : query;
        List<String> months = monthKeys(safeQuery.endMonthYyyyMm, safeQuery.lastNMonths);
        List<Envelope> selected = selectedPonds(envelopes, safeQuery.pondNames);
        List<MonthSpend> monthSpends = new ArrayList<>();
        List<OverBudgetRow> overBudget = new ArrayList<>();
        List<PondSpend> byPond = new ArrayList<>();
        List<SnapshotRow> thisMonth = new ArrayList<>();
        boolean hasSpendInRange = false;

        for (String month : months) {
            double totalSpend = 0d;
            double totalLimit = 0d;
            for (Envelope envelope : selected) {
                double spend = monthSpend(envelope, month, safeQuery.includeTransfers);
                double limit = MoneyMath.roundToCents(safe(envelope.getLimit()));
                totalSpend = MoneyMath.roundToCents(totalSpend + spend);
                totalLimit = MoneyMath.roundToCents(totalLimit + limit);
                if (spend > limit) {
                    overBudget.add(new OverBudgetRow(
                            month,
                            envelope.getName(),
                            spend,
                            limit,
                            MoneyMath.roundToCents(spend - limit)));
                }
            }
            if (totalSpend != 0d) {
                hasSpendInRange = true;
            }
            monthSpends.add(new MonthSpend(month, totalSpend, totalLimit));
        }

        String endMonth = months.isEmpty() ? null : months.get(months.size() - 1);
        for (Envelope envelope : selected) {
            double rangeSpend = 0d;
            for (String month : months) {
                rangeSpend = MoneyMath.roundToCents(
                        rangeSpend + monthSpend(envelope, month, safeQuery.includeTransfers));
            }
            byPond.add(new PondSpend(envelope.getName(), rangeSpend));
            if (endMonth != null) {
                double spend = monthSpend(envelope, endMonth, safeQuery.includeTransfers);
                double limit = MoneyMath.roundToCents(safe(envelope.getLimit()));
                thisMonth.add(new SnapshotRow(
                        envelope.getName(),
                        spend,
                        limit,
                        MoneyMath.roundToCents(safe(envelope.getRemaining())),
                        spend > limit));
            }
        }

        Collections.sort(overBudget, OVER_BUDGET_ORDER);
        Collections.sort(byPond, POND_SPEND_ORDER);
        return new SpendAnalysisResult(monthSpends, overBudget, byPond, thisMonth, hasSpendInRange);
    }

    public static double barScaleMax(List<Double> amounts) {
        double max = 0d;
        if (amounts != null) {
            for (Double amount : amounts) {
                if (amount != null && amount > max) {
                    max = amount;
                }
            }
        }
        return Math.max(MoneyMath.roundToCents(max), MIN_BAR_SCALE);
    }

    public static String shortMonthLabel(String yyyyMm) {
        return formatMonth(yyyyMm, "MMM");
    }

    public static String fullMonthLabel(String yyyyMm) {
        return formatMonth(yyyyMm, "MMMM yyyy");
    }

    public static String normalizeMonth(String month) {
        if (month == null) {
            return null;
        }
        String trimmed = month.trim();
        if (!MONTH_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed;
    }

    static double monthSpend(Envelope envelope, String month, boolean includeTransfers) {
        if (envelope == null || month == null) {
            return 0d;
        }
        double spent = 0d;
        List<Transaction> transactions = envelope.getTransactions();
        if (transactions == null) {
            return 0d;
        }
        for (Transaction transaction : transactions) {
            if (transaction == null) {
                continue;
            }
            if (!month.equals(transaction.getMonth())) {
                continue;
            }
            if (!countsAsSpend(transaction, includeTransfers)) {
                continue;
            }
            spent += safe(transaction.getAmount());
        }
        return MoneyMath.roundToCents(spent);
    }

    private static List<Envelope> selectedPonds(List<Envelope> envelopes, List<String> pondNames) {
        List<Envelope> selected = new ArrayList<>();
        if (envelopes == null) {
            return selected;
        }
        Set<String> filter = trimmedNameSet(pondNames);
        boolean allPonds = filter.isEmpty();
        for (Envelope envelope : envelopes) {
            if (envelope == null || envelope.getName() == null) {
                continue;
            }
            if (allPonds || filter.contains(envelope.getName().trim())) {
                selected.add(envelope);
            }
        }
        return selected;
    }

    private static Set<String> trimmedNameSet(List<String> pondNames) {
        Set<String> names = new HashSet<>();
        if (pondNames == null) {
            return names;
        }
        for (String name : pondNames) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        return names;
    }

    private static String addMonths(String yyyyMm, int delta) {
        int year = Integer.parseInt(yyyyMm.substring(0, 4));
        int monthIndex = Integer.parseInt(yyyyMm.substring(5, 7)) - 1;
        int absolute = year * 12 + monthIndex + delta;
        int resultYear = Math.floorDiv(absolute, 12);
        int resultMonth = Math.floorMod(absolute, 12) + 1;
        return String.format(Locale.US, "%04d-%02d", resultYear, resultMonth);
    }

    private static String formatMonth(String yyyyMm, String pattern) {
        String month = normalizeMonth(yyyyMm);
        if (month == null) {
            return yyyyMm == null ? "" : yyyyMm;
        }
        try {
            Date date = new SimpleDateFormat("yyyy-MM", Locale.US).parse(month);
            return new SimpleDateFormat(pattern, Locale.getDefault()).format(date);
        } catch (ParseException e) {
            return month;
        }
    }

    private static double safe(Double value) {
        if (value == null || Double.isNaN(value) || Double.isInfinite(value)) {
            return 0d;
        }
        return value;
    }

    private static final Comparator<OverBudgetRow> OVER_BUDGET_ORDER =
            new Comparator<OverBudgetRow>() {
                @Override
                public int compare(OverBudgetRow left, OverBudgetRow right) {
                    int byOver = Double.compare(right.overBy, left.overBy);
                    if (byOver != 0) {
                        return byOver;
                    }
                    int byMonth = right.month.compareTo(left.month);
                    if (byMonth != 0) {
                        return byMonth;
                    }
                    return left.pondName.compareToIgnoreCase(right.pondName);
                }
            };

    private static final Comparator<PondSpend> POND_SPEND_ORDER =
            new Comparator<PondSpend>() {
                @Override
                public int compare(PondSpend left, PondSpend right) {
                    int bySpend = Double.compare(right.spend, left.spend);
                    if (bySpend != 0) {
                        return bySpend;
                    }
                    return left.pondName.compareToIgnoreCase(right.pondName);
                }
            };

    public static final class SpendAnalysisQuery {
        public final String endMonthYyyyMm;
        public final int lastNMonths;
        public final List<String> pondNames;
        public final boolean includeTransfers;

        public SpendAnalysisQuery(String endMonthYyyyMm, int lastNMonths,
                                  List<String> pondNames, boolean includeTransfers) {
            this.endMonthYyyyMm = endMonthYyyyMm;
            this.lastNMonths = lastNMonths;
            this.pondNames = pondNames == null ? Collections.emptyList() : pondNames;
            this.includeTransfers = includeTransfers;
        }
    }

    public static final class SpendAnalysisResult {
        public final List<MonthSpend> months;
        public final List<OverBudgetRow> overBudget;
        public final List<PondSpend> byPond;
        public final List<SnapshotRow> thisMonth;
        public final boolean hasSpendInRange;

        public SpendAnalysisResult(List<MonthSpend> months,
                                   List<OverBudgetRow> overBudget,
                                   List<PondSpend> byPond,
                                   List<SnapshotRow> thisMonth,
                                   boolean hasSpendInRange) {
            this.months = months;
            this.overBudget = overBudget;
            this.byPond = byPond;
            this.thisMonth = thisMonth;
            this.hasSpendInRange = hasSpendInRange;
        }
    }

    public static final class MonthSpend {
        public final String month;
        public final double totalSpend;
        public final double totalLimit;

        public MonthSpend(String month, double totalSpend, double totalLimit) {
            this.month = month;
            this.totalSpend = totalSpend;
            this.totalLimit = totalLimit;
        }
    }

    public static final class OverBudgetRow {
        public final String month;
        public final String pondName;
        public final double spend;
        public final double limit;
        public final double overBy;

        public OverBudgetRow(String month, String pondName, double spend, double limit, double overBy) {
            this.month = month;
            this.pondName = pondName;
            this.spend = spend;
            this.limit = limit;
            this.overBy = overBy;
        }
    }

    public static final class PondSpend {
        public final String pondName;
        public final double spend;

        public PondSpend(String pondName, double spend) {
            this.pondName = pondName;
            this.spend = spend;
        }
    }

    public static final class SnapshotRow {
        public final String pondName;
        public final double spend;
        public final double limit;
        public final double remaining;
        public final boolean overBudget;

        public SnapshotRow(String pondName, double spend, double limit, double remaining, boolean overBudget) {
            this.pondName = pondName;
            this.spend = spend;
            this.limit = limit;
            this.remaining = remaining;
            this.overBudget = overBudget;
        }
    }
}
