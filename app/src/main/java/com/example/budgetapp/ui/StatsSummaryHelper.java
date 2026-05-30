package com.example.budgetapp.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatsSummaryHelper {

    public static void updateSummarySection(Context context,
                                            Map<String, Double> pieMap, double totalAmount,
                                            Map<String, Double> incomePieMap, double totalIncomeAmount,
                                            int currentMode, LocalDate selectedDate,
                                            List<Transaction> allTransactions,
                                            TextView tvSummaryTitle, TextView tvSummaryContent,
                                            View layoutSummary,
                                            View dividerOvertime, TextView tvOvertimeContent,
                                            View dividerIncome, TextView tvIncomeSummaryTitle,
                                            TextView tvIncomeSummaryContent) {
        String scopeStr;
        if (currentMode == 0) scopeStr = "本年";
        else if (currentMode == 1) scopeStr = "本月";
        else scopeStr = "本周";
        tvSummaryTitle.setText(scopeStr + "消费");

        LocalDate startOfPeriod;
        LocalDate endOfPeriod;
        if (currentMode == 0) {
            startOfPeriod = selectedDate.with(java.time.temporal.TemporalAdjusters.firstDayOfYear());
            endOfPeriod = selectedDate.with(java.time.temporal.TemporalAdjusters.lastDayOfYear());
        } else if (currentMode == 1) {
            startOfPeriod = selectedDate.with(java.time.temporal.TemporalAdjusters.firstDayOfMonth());
            endOfPeriod = selectedDate.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        } else {
            startOfPeriod = selectedDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            endOfPeriod = selectedDate.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        }

        boolean hasOvertime = checkHasOvertime(startOfPeriod, endOfPeriod, allTransactions);
        boolean hasExpense = !pieMap.isEmpty() && totalAmount > 0;
        boolean hasIncome = !incomePieMap.isEmpty() && totalIncomeAmount > 0;

        if (!hasExpense && !hasOvertime && !hasIncome) {
            if (layoutSummary != null) layoutSummary.setVisibility(View.GONE);
            return;
        }

        if (layoutSummary != null) layoutSummary.setVisibility(View.VISIBLE);

        if (dividerOvertime != null) dividerOvertime.setVisibility(View.GONE);
        if (tvOvertimeContent != null) tvOvertimeContent.setVisibility(View.GONE);

        if (!hasExpense) {
            tvSummaryContent.setText("暂无消费记录");
        } else {
            List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(pieMap.entrySet());
            Collections.sort(sortedEntries, (e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            String[] prefixes = {"最多是", "其次是", "然后是"};

            int yellowColor = ContextCompat.getColor(context, R.color.app_yellow);
            int greenColor = ContextCompat.getColor(context, R.color.expense_green);
            int redColor = ContextCompat.getColor(context, R.color.income_red);

            int count = Math.min(sortedEntries.size(), 3);
            for (int i = 0; i < count; i++) {
                if (i > 0) ssb.append("\n");

                Map.Entry<String, Double> e = sortedEntries.get(i);
                double percent = (e.getValue() / totalAmount) * 100;

                ssb.append(prefixes[i]);

                String category = e.getKey();
                int startCat = ssb.length();
                ssb.append(category);
                ssb.setSpan(new ForegroundColorSpan(yellowColor), startCat, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                ssb.append(", ");
                ssb.append("占比");

                String percentStr = String.format(Locale.CHINA, "%.1f%%", percent);
                int startPer = ssb.length();
                ssb.append(percentStr);
                ssb.setSpan(new ForegroundColorSpan(greenColor), startPer, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                ssb.append(", ");
                ssb.append("消费");

                String amountStr = String.format(Locale.CHINA, "%.2f", e.getValue());
                int startAmt = ssb.length();
                ssb.append(amountStr);
                ssb.setSpan(new ForegroundColorSpan(redColor), startAmt, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                ssb.append("元");
            }

            ssb.append("\n");

            long days = 1;
            LocalDate today = LocalDate.now();

            if (!today.isBefore(startOfPeriod) && !today.isAfter(endOfPeriod)) {
                days = ChronoUnit.DAYS.between(startOfPeriod, today) + 1;
            } else {
                days = ChronoUnit.DAYS.between(startOfPeriod, endOfPeriod) + 1;
            }
            if (days < 1) days = 1;
            double dailyAvg = totalAmount / days;

            ssb.append("共计消费");
            String totalStr = String.format(Locale.CHINA, "%.2f", totalAmount);
            int startTotal = ssb.length();
            ssb.append(totalStr);
            ssb.setSpan(new ForegroundColorSpan(redColor), startTotal, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            ssb.append("元, ");
            ssb.append("日均消费");

            String avgStr = String.format(Locale.CHINA, "%.2f", dailyAvg);
            int startAvg = ssb.length();
            ssb.append(avgStr);
            ssb.setSpan(new ForegroundColorSpan(redColor), startAvg, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append("元");

            tvSummaryContent.setText(ssb);
        }

        if (hasIncome) {
            if (dividerIncome != null) dividerIncome.setVisibility(View.VISIBLE);
            if (tvIncomeSummaryTitle != null) {
                tvIncomeSummaryTitle.setVisibility(View.VISIBLE);
                tvIncomeSummaryTitle.setText(scopeStr + "收入");
            }
            if (tvIncomeSummaryContent != null) {
                tvIncomeSummaryContent.setVisibility(View.VISIBLE);

                List<Map.Entry<String, Double>> sortedIncome = new ArrayList<>(incomePieMap.entrySet());
                Collections.sort(sortedIncome, (e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

                SpannableStringBuilder incomeSsb = new SpannableStringBuilder();
                String[] prefixes = {"最多是", "其次是", "然后是"};

                int yellowColor = ContextCompat.getColor(context, R.color.app_yellow);
                int greenColor = ContextCompat.getColor(context, R.color.expense_green);
                int redColor = ContextCompat.getColor(context, R.color.income_red);

                int count = Math.min(sortedIncome.size(), 3);
                for (int i = 0; i < count; i++) {
                    if (i > 0) incomeSsb.append("\n");

                    Map.Entry<String, Double> e = sortedIncome.get(i);
                    double percent = (e.getValue() / totalIncomeAmount) * 100;

                    incomeSsb.append(prefixes[i]);

                    int startCat = incomeSsb.length();
                    incomeSsb.append(e.getKey());
                    incomeSsb.setSpan(new ForegroundColorSpan(yellowColor), startCat, incomeSsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    incomeSsb.append(", 占比");

                    String percentStr = String.format(Locale.CHINA, "%.1f%%", percent);
                    int startPer = incomeSsb.length();
                    incomeSsb.append(percentStr);
                    incomeSsb.setSpan(new ForegroundColorSpan(greenColor), startPer, incomeSsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    incomeSsb.append(", 收入");

                    String amountStr = String.format(Locale.CHINA, "%.2f", e.getValue());
                    int startAmt = incomeSsb.length();
                    incomeSsb.append(amountStr);
                    incomeSsb.setSpan(new ForegroundColorSpan(redColor), startAmt, incomeSsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    incomeSsb.append("元");
                }

                incomeSsb.append("\n共计收入");
                String totalIncStr = String.format(Locale.CHINA, "%.2f", totalIncomeAmount);
                int startTotal = incomeSsb.length();
                incomeSsb.append(totalIncStr);
                incomeSsb.setSpan(new ForegroundColorSpan(redColor), startTotal, incomeSsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                incomeSsb.append("元");

                if (currentMode == 0 || currentMode == 1) {
                    double balance = totalIncomeAmount - totalAmount;
                    String balanceLabel = (currentMode == 0) ? ", 本年结余" : ", 本月结余";
                    incomeSsb.append(balanceLabel);

                    String balanceStr = String.format(Locale.CHINA, "%.2f", balance);
                    int startBalance = incomeSsb.length();
                    incomeSsb.append(balanceStr);

                    int balanceColor = balance >= 0 ? redColor : greenColor;
                    incomeSsb.setSpan(new ForegroundColorSpan(balanceColor), startBalance, incomeSsb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    incomeSsb.append("元");
                }

                tvIncomeSummaryContent.setText(incomeSsb);
            }
        } else {
            if (dividerIncome != null) dividerIncome.setVisibility(View.GONE);
            if (tvIncomeSummaryTitle != null) tvIncomeSummaryTitle.setVisibility(View.GONE);
            if (tvIncomeSummaryContent != null) tvIncomeSummaryContent.setVisibility(View.GONE);
        }

        if (hasOvertime) {
            calculateAndShowOvertime(context, startOfPeriod, endOfPeriod, scopeStr,
                    allTransactions, currentMode,
                    dividerOvertime, tvOvertimeContent);
        }
    }

    private static boolean checkHasOvertime(LocalDate start, LocalDate end, List<Transaction> allTransactions) {
        long startMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        for (Transaction t : allTransactions) {
            if (t.date >= startMillis && t.date < endMillis && t.type == 1 && "加班".equals(t.category)) {
                return true;
            }
        }
        return false;
    }

    private static void calculateAndShowOvertime(Context context,
                                                  LocalDate start, LocalDate end, String scopeStr,
                                                  List<Transaction> allTransactions, int currentMode,
                                                  View dividerOvertime, TextView tvOvertimeContent) {
        long startMillis = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMillis = end.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

        double totalOvertimeHours = 0;
        double weekdayOvertimeHours = 0;
        double holidayOvertimeHours = 0;
        double totalOvertimeIncome = 0;

        Pattern pattern = Pattern.compile("时长:\\s*([0-9.]+)\\s*小时");

        for (Transaction t : allTransactions) {
            if (t.date >= startMillis && t.date < endMillis && t.type == 1 && "加班".equals(t.category)) {
                totalOvertimeIncome += t.amount;

                if (t.note != null) {
                    Matcher matcher = pattern.matcher(t.note);
                    if (matcher.find()) {
                        try {
                            double hours = Double.parseDouble(matcher.group(1));
                            totalOvertimeHours += hours;

                            LocalDate transDate = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate();
                            DayOfWeek dayOfWeek = transDate.getDayOfWeek();
                            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                                holidayOvertimeHours += hours;
                            } else {
                                weekdayOvertimeHours += hours;
                            }
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        int redColor = ContextCompat.getColor(context, R.color.income_red);
        int primaryColor = ContextCompat.getColor(context, R.color.text_primary);

        String title = scopeStr + "加班";
        int startTitle = ssb.length();
        ssb.append(title);
        ssb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), startTitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new AbsoluteSizeSpan(16, true), startTitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ForegroundColorSpan(primaryColor), startTitle, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("\n");

        ssb.append("共计");
        String totalHoursStr = String.format(Locale.CHINA, "%.1f", totalOvertimeHours);
        int startTotalH = ssb.length();
        ssb.append(totalHoursStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startTotalH, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("小时, 共计获得");
        String amountStr = String.format(Locale.CHINA, "%.2f", totalOvertimeIncome);
        int startAmount = ssb.length();
        ssb.append(amountStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startAmount, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("元\n");

        ssb.append("工作日加班");
        String weekdayHoursStr = String.format(Locale.CHINA, "%.1f", weekdayOvertimeHours);
        int startWeekday = ssb.length();
        ssb.append(weekdayHoursStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startWeekday, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("小时, 节假日加班");
        String holidayHoursStr = String.format(Locale.CHINA, "%.1f", holidayOvertimeHours);
        int startHoliday = ssb.length();
        ssb.append(holidayHoursStr);
        ssb.setSpan(new ForegroundColorSpan(redColor), startHoliday, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.append("小时");

        tvOvertimeContent.setText(ssb);

        if (dividerOvertime != null) dividerOvertime.setVisibility(View.VISIBLE);
        if (tvOvertimeContent != null) tvOvertimeContent.setVisibility(View.VISIBLE);
    }
}