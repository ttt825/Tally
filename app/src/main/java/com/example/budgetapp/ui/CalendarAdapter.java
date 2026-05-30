// src/main/java/com/example/budgetapp/ui/CalendarAdapter.java
package com.example.budgetapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;


import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.nlf.calendar.Lunar;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.ViewHolder> {

    private List<LocalDate> days = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private LocalDate selectedDate;
    private final OnDateClickListener listener;
    private int filterMode = 0;
    private YearMonth currentMonth;

    // 预计算缓存
    private List<DayCache> dayCacheList = new ArrayList<>();

    // 缓存的主题颜色
    private int cachedColorPrimaryText = Color.GRAY;
    private int cachedColorSecondaryText = Color.GRAY;
    private int cachedThemeColor = Color.GRAY;
    private int cachedIncomeRed = Color.GRAY;
    private int cachedExpenseGreen = Color.GRAY;
    private int cachedLunarTextColor = Color.GRAY;
    private boolean themeColorsCached = false;

    // 缓存的自定义背景开关
    private boolean customBgEnabled = false;

    /**
     * 内部类：存储每日的预计算结果
     */
    private static class DayCache {
        LocalDate date;
        double dailySum;
        double dailyHours;
        // 【新增】分别存储收入和支出
        double dailyIncome;
        double dailyExpense;
        String netText;
        int netColor;
        boolean hasData;
    }

    public interface OnDateClickListener {
        void onDateClick(LocalDate date);
    }

    public CalendarAdapter(OnDateClickListener listener) {
        this.listener = listener;
    }

    public void setFilterMode(int mode) {
        this.filterMode = mode;
        notifyDataSetChanged();
    }

    public void setCurrentMonth(YearMonth month) {
        this.currentMonth = month;
    }

    /**
     * 缓存主题颜色，避免在 onBindViewHolder 中重复解析
     */
    public void cacheThemeColors(Context context) {
        cachedColorPrimaryText = getThemeColor(context, android.R.attr.textColorPrimary);
        cachedColorSecondaryText = getThemeColor(context, android.R.attr.textColorSecondary);
        cachedThemeColor = context.getColor(R.color.app_yellow);
        cachedIncomeRed = context.getColor(R.color.income_red);
        cachedExpenseGreen = context.getColor(R.color.expense_green);
        cachedLunarTextColor = context.getColor(R.color.calendar_lunar_text);
        themeColorsCached = true;
    }

    /**
     * 设置自定义背景是否开启，由外部传入避免在 onBindViewHolder 中读取 SharedPreferences
     */
    public void setCustomBgEnabled(boolean enabled) {
        this.customBgEnabled = enabled;
    }

    public void updateData(List<LocalDate> days, List<Transaction> transactions) {
        this.days = days;
        this.transactions = transactions;
        buildDayCache();
        notifyDataSetChanged();
    }

    /**
     * 【新增】强制重新缓存主题颜色并刷新日历
     * 用于 Tab 切换后恢复正确的颜色状态
     */
    public void refreshThemeColors(Context context) {
        themeColorsCached = false;
        cacheThemeColors(context);
        // 颜色缓存更新后，需要重新计算每日的颜色
        buildDayCache();
        notifyDataSetChanged();
    }

    public void setSelectedDate(LocalDate date) {
        this.selectedDate = date;
        notifyDataSetChanged();
    }

    private int getThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                return context.getColor(typedValue.resourceId);
            }
            return typedValue.data;
        }
        return Color.GRAY;
    }

    /**
     * 预计算每日统计数据并缓存到 dayCacheList 中
     */
    private void buildDayCache() {
        dayCacheList = new ArrayList<>(days.size());
        for (int i = 0; i < days.size(); i++) {
            LocalDate date = days.get(i);
            DayCache cache = new DayCache();
            cache.date = date;

            if (date == null) {
                dayCacheList.add(cache);
                continue;
            }

            double dailySum = 0;
            double dailyHours = 0;
            // 【新增】分别统计收入和支出
            double dailyIncome = 0;
            double dailyExpense = 0;
            long start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

            for (Transaction t : transactions) {
                if (t.date >= start && t.date < end) {
                    // 【新增】统计支出
                    if (t.type == 0) {
                        dailyExpense += t.amount;
                    }

                    switch (filterMode) {
                        case 0: // 结余
                            if (t.type == 1) {
                                if (!"加班".equals(t.category)) {
                                    dailySum += t.amount;
                                    dailyIncome += t.amount;
                                }
                            } else if (t.type == 0) {
                                dailySum -= t.amount;
                            }
                            break;
                        case 1: // 收入
                            if (t.type == 1 && !"加班".equals(t.category)) {
                                dailySum += t.amount;
                                dailyIncome += t.amount;
                            }
                            break;
                        case 2: // 支出
                            if (t.type == 0) dailySum += t.amount;
                            break;
                        case 3: // 加班工资
                            if (t.type == 1 && "加班".equals(t.category)) dailySum += t.amount;
                            break;
                        case 4: // 加班工时
                            if (t.type == 1 && "加班".equals(t.category)) {
                                if (t.note != null) {
                                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("时长:\\s*([0-9.]+)\\s*小时").matcher(t.note);
                                    if (m.find()) {
                                        try {
                                            dailyHours += Double.parseDouble(m.group(1));
                                        } catch (NumberFormatException ignored) {}
                                    }
                                }
                                dailySum += t.amount;
                            }
                            break;
                    }
                }
            }

            cache.dailySum = dailySum;
            cache.dailyHours = dailyHours;
            cache.dailyIncome = dailyIncome;
            cache.dailyExpense = dailyExpense;

            // 判断是否有收支数据
            if (dailySum != 0 || dailyHours > 0 || (filterMode == 0 && (dailyIncome > 0 || dailyExpense > 0))) {
                cache.hasData = true;
                if (filterMode == 4) {
                    cache.netText = String.format("%.1fh", dailyHours);
                    cache.netColor = Color.parseColor("#2196F3");
                } else if (filterMode == 0) {
                    // 【修改】结余模式：分别显示收入（红色）和支出（绿色）
                    // 两者都有时显示差额（收入-支出）
                    boolean hasIncome = dailyIncome > 0;
                    boolean hasExpense = dailyExpense > 0;
                    if (hasIncome && hasExpense) {
                        // 两者都有：显示差额
                        double balance = dailyIncome - dailyExpense;
                        cache.netText = String.format("%.2f", balance);
                        cache.netColor = balance > 0 ? cachedIncomeRed : cachedExpenseGreen;
                    } else if (hasIncome) {
                        // 只有收入：红色
                        cache.netText = String.format("%.2f", dailyIncome);
                        cache.netColor = cachedIncomeRed;
                    } else {
                        // 只有支出：绿色
                        cache.netText = String.format("%.2f", dailyExpense);
                        cache.netColor = cachedExpenseGreen;
                    }
                } else {
                    cache.netText = String.format("%.2f", dailySum);
                    if (filterMode == 2) {
                        cache.netColor = cachedExpenseGreen;
                    } else if (filterMode == 3) {
                        cache.netColor = Color.parseColor("#FF9800");
                    } else {
                        cache.netColor = dailySum > 0 ? cachedIncomeRed : cachedExpenseGreen;
                    }
                }
            } else {
                cache.hasData = false;
                // 无收支数据时，农历文本和颜色仍需在 onBindViewHolder 中计算（需要 Context）
                cache.netText = null;
                cache.netColor = 0;
            }

            dayCacheList.add(cache);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DayCache cache = dayCacheList.get(position);
        LocalDate date = cache.date;
        if (date == null) {
            holder.tvDay.setText("");
            holder.tvNet.setText("");
            holder.itemView.setBackgroundResource(0);
            holder.itemView.setSelected(false);
            return;
        }

        Context context = holder.itemView.getContext();

        // 确保主题颜色已缓存
        if (!themeColorsCached) {
            cacheThemeColors(context);
        }

        holder.tvDay.setText(String.valueOf(date.getDayOfMonth()));

        boolean isCurrentMonth = currentMonth != null &&
                date.getYear() == currentMonth.getYear() &&
                date.getMonth() == currentMonth.getMonth();

        // 1. 计算默认字体颜色
        int defaultDayColor;
        if (isCurrentMonth) {
            holder.tvDay.setAlpha(1.0f);
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            boolean isWeekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            defaultDayColor = isWeekend ? cachedThemeColor : cachedColorPrimaryText;
        } else {
            holder.tvDay.setAlpha(0.3f);
            defaultDayColor = cachedColorSecondaryText;
        }

        // 2. 从缓存获取统计数据
        int defaultNetColor = 0;
        String netText;

        if (cache.hasData) {
            netText = cache.netText;
            defaultNetColor = cache.netColor;
        } else {
            // === 无收支数据，显示农历和节日（需要 Context，在 onBindViewHolder 中计算）===
            try {
                java.util.Date utilDate = java.util.Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
                Lunar lunar = new Lunar(utilDate);
                String lunarDay = lunar.getDayInChinese();

                java.util.List<String> festivals = lunar.getFestivals();
                if (festivals != null && !festivals.isEmpty()) {
                    netText = festivals.get(0);
                } else {
                    netText = lunarDay;
                }
            } catch (Exception e) {
                netText = String.valueOf(date.getDayOfMonth());
            }
            defaultNetColor = cachedLunarTextColor;
        }

        // 3. 样式应用逻辑核心
        boolean isToday = date.equals(LocalDate.now());
        boolean isSelected = date.equals(selectedDate);

        // --- 在判断背景色之前，先保存 View 原本的 Padding ---
        int padLeft = holder.itemView.getPaddingLeft();
        int padTop = holder.itemView.getPaddingTop();
        int padRight = holder.itemView.getPaddingRight();
        int padBottom = holder.itemView.getPaddingBottom();

        // --- 样式优先级：选中 > 今天 > 普通 ---
        if (isSelected) {
            holder.itemView.setBackgroundResource(R.drawable.bg_calendar_today);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(Color.parseColor("#2196F3"));

            holder.tvDay.setTextColor(Color.WHITE);
            holder.tvDay.setAlpha(1.0f);
            holder.itemView.setSelected(true);

        } else if (isToday) {
            holder.itemView.setBackgroundResource(R.drawable.bg_selected_date);
            Drawable bg = holder.itemView.getBackground();
            if (bg != null) bg.setTint(Color.parseColor("#2196F3"));

            holder.tvDay.setTextColor(defaultDayColor);
            holder.tvDay.setAlpha(1.0f);
            holder.itemView.setSelected(false);

        } else {
            // ====== [普通状态] ======
            if (customBgEnabled) {
                android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);

                int alpha = isCurrentMonth ? 230 : 153;

                int surfaceColor = androidx.core.content.ContextCompat.getColor(context, R.color.white);
                int translucentGray = androidx.core.graphics.ColorUtils.setAlphaComponent(surfaceColor, alpha);
                shape.setColor(translucentGray);

                float radius = android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 12, context.getResources().getDisplayMetrics());
                shape.setCornerRadius(radius);

                int inset = (int) android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics());

                android.graphics.drawable.InsetDrawable insetDrawable =
                        new android.graphics.drawable.InsetDrawable(shape, inset, inset, inset, inset);

                holder.itemView.setBackground(insetDrawable);
            } else {
                holder.itemView.setBackgroundResource(0);
            }

            holder.tvDay.setTextColor(defaultDayColor);
            holder.itemView.setSelected(false);
        }

        // --- 背景设置完毕后，强行恢复原本的 Padding ---
        holder.itemView.setPadding(padLeft, padTop, padRight, padBottom);

        // 设置下方金额文字
        holder.tvNet.setText(netText);
        if (!netText.isEmpty()) {
            if (isSelected) {
                holder.tvNet.setTextColor(Color.WHITE);
            } else {
                holder.tvNet.setTextColor(defaultNetColor);
            }
            holder.tvNet.setAlpha(isCurrentMonth ? 1.0f : 0.3f);
        } else {
            holder.tvNet.setText("");
        }

        holder.itemView.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            listener.onDateClick(date);
        });
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDay, tvNet;

        ViewHolder(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tv_day);
            tvNet = v.findViewById(R.id.tv_net_amount);
        }
    }
}
