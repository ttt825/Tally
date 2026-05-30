package com.example.budgetapp.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 日期工具类
 * 统一处理所有日期格式化、计算等操作
 */
public class DateUtils {

    // ================= 日期格式定义 =================
    public static final String FORMAT_DATE = "yyyy年MM月dd日";
    public static final String FORMAT_DATE_TIME = "yyyy-MM-dd HH:mm:ss";
    public static final String FORMAT_DATE_SHORT = "yyyy-MM-dd";
    public static final String FORMAT_MONTH = "yyyy年MM月";
    public static final String FORMAT_TIME = "HH:mm";

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(FORMAT_DATE, Locale.CHINA));
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(FORMAT_DATE_TIME, Locale.CHINA));
    private static final ThreadLocal<SimpleDateFormat> DATE_SHORT_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(FORMAT_DATE_SHORT, Locale.CHINA));
    private static final ThreadLocal<SimpleDateFormat> MONTH_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(FORMAT_MONTH, Locale.CHINA));
    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT =
            ThreadLocal.withInitial(() -> new SimpleDateFormat(FORMAT_TIME, Locale.CHINA));

    // ================= 格式化方法 =================

    /**
     * 格式化日期（长格式）
     */
    public static String formatDate(long timestamp) {
        return DATE_FORMAT.get().format(new Date(timestamp));
    }

    /**
     * 格式化日期时间
     */
    public static String formatDateTime(long timestamp) {
        return DATE_TIME_FORMAT.get().format(new Date(timestamp));
    }

    /**
     * 格式化日期（短格式）
     */
    public static String formatDateShort(long timestamp) {
        return DATE_SHORT_FORMAT.get().format(new Date(timestamp));
    }

    /**
     * 格式化月份
     */
    public static String formatMonth(long timestamp) {
        return MONTH_FORMAT.get().format(new Date(timestamp));
    }

    /**
     * 格式化时间
     */
    public static String formatTime(long timestamp) {
        return TIME_FORMAT.get().format(new Date(timestamp));
    }

    /**
     * 格式化日期（自定义格式）
     */
    public static String format(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.CHINA);
        return sdf.format(new Date(timestamp));
    }

    // ================= 时间计算 =================

    /**
     * 获取某天的开始时间戳
     */
    public static long getDayStart(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 获取某天的结束时间戳
     */
    public static long getDayEnd(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day, 23, 59, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    /**
     * 获取某月的开始时间戳
     */
    public static long getMonthStart(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 获取某月的结束时间戳
     */
    public static long getMonthEnd(int year, int month) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MONTH, 1);
        cal.add(Calendar.MILLISECOND, -1);
        return cal.getTimeInMillis();
    }

    /**
     * 获取某年的开始时间戳
     */
    public static long getYearStart(int year) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 获取某年的结束时间戳
     */
    public static long getYearEnd(int year) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    /**
     * 获取今天的开始时间戳
     */
    public static long getTodayStart() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 获取今天的结束时间戳
     */
    public static long getTodayEnd() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    /**
     * 获取本周的开始时间戳（周一）
     */
    public static long getWeekStart() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * 获取本周的结束时间戳（周日）
     */
    public static long getWeekEnd() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }

    // ================= 时间判断 =================

    /**
     * 判断是否为今天
     */
    public static boolean isToday(long timestamp) {
        long todayStart = getTodayStart();
        long todayEnd = getTodayEnd();
        return timestamp >= todayStart && timestamp <= todayEnd;
    }

    /**
     * 判断是否为同一个月
     */
    public static boolean isSameMonth(long timestamp1, long timestamp2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTimeInMillis(timestamp1);
        cal2.setTimeInMillis(timestamp2);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH);
    }

    /**
     * 获取年份
     */
    public static int getYear(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.YEAR);
    }

    /**
     * 获取月份（1-12）
     */
    public static int getMonth(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.MONTH) + 1;
    }

    /**
     * 获取日期（1-31）
     */
    public static int getDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return cal.get(Calendar.DAY_OF_MONTH);
    }
}
