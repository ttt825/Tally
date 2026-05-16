package com.example.budgetapp.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.budgetapp.R;
import com.example.budgetapp.database.Transaction;
import com.example.budgetapp.viewmodel.FinanceViewModel;
import java.util.Calendar;
import java.util.List;

public class AboutActivity extends AppCompatActivity {

    private FinanceViewModel financeViewModel;
    private TextView tvStatsInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_about);

        tvStatsInfo = findViewById(R.id.tv_stats_info);

        // 新增：获取并显示当前应用的版本号
        TextView tvAppVersion = findViewById(R.id.tv_app_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tvAppVersion.setText("当前版本 v" + version);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            tvAppVersion.setText("当前版本 未知");
        }

        // 新增：从服务器获取最新版本号
        TextView tvLatestVersion = findViewById(R.id.tv_latest_version);
        checkLatestVersion(tvLatestVersion);

        // 处理沉浸式内边距，与其他界面保持统一
        View rootView = findViewById(R.id.about_root);
        final int originalPaddingLeft = rootView.getPaddingLeft();
        final int originalPaddingTop = rootView.getPaddingTop();
        final int originalPaddingRight = rootView.getPaddingRight();
        final int originalPaddingBottom = rootView.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    originalPaddingLeft + insets.left,
                    originalPaddingTop + insets.top,
                    originalPaddingRight + insets.right,
                    originalPaddingBottom + insets.bottom
            );
            return WindowInsetsCompat.CONSUMED;
        });

        // 初始化 ViewModel 并观察数据
        financeViewModel = new ViewModelProvider(this).get(FinanceViewModel.class);
        financeViewModel.getAllTransactions().observe(this, this::updateStatistics);

        // 按钮跳转逻辑
        findViewById(R.id.btn_user_notice).setOnClickListener(v -> 
            startActivity(new Intent(this, UserNoticeActivity.class)));
        findViewById(R.id.btn_privacy_policy).setOnClickListener(v -> 
            startActivity(new Intent(this, PrivacyPolicyActivity.class)));
        findViewById(R.id.btn_ai_guide).setOnClickListener(v -> 
            startActivity(new Intent(this, AiGuideActivity.class)));
        findViewById(R.id.btn_donate).setOnClickListener(v -> 
            startActivity(new Intent(this, DonateActivity.class)));
    }

    private void updateStatistics(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            tvStatsInfo.setText("开始记下你的第一笔账单吧");
            return;
        }

        int count = transactions.size();
        long earliestDate = Long.MAX_VALUE;

        // 寻找最早的一笔账单时间
        for (Transaction t : transactions) {
            if (t.date < earliestDate) {
                earliestDate = t.date;
            }
        }

        // 计算天数
        long days = calculateDays(earliestDate);
        
        String info = String.format("已坚持记账 %d 天，共记账 %d 条", days, count);
        tvStatsInfo.setText(info);
    }

    private long calculateDays(long firstTimestamp) {
        // 获取今天凌晨 0 点的时间戳
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        // 获取开始记账那天凌晨 0 点的时间戳
        Calendar startDate = Calendar.getInstance();
        startDate.setTimeInMillis(firstTimestamp);
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);

        // 计算差值并转为天数（+1 表示包括今天）
        long diff = today.getTimeInMillis() - startDate.getTimeInMillis();
        return (diff / (1000 * 60 * 60 * 24)) + 1;
    }

    /**
     * 从服务器获取最新版本号
     * 请求地址: https://tallyapp.top/version.json
     * 预期返回格式: {"version": "1.2.0"}
     */
    private void checkLatestVersion(TextView tvLatestVersion) {
        new Thread(() -> {
            try {
                // 优先尝试域名，失败后回退到 IP 直连
                java.net.HttpURLConnection conn = null;
                int responseCode = -1;
                try {
                    java.net.URL url = new java.net.URL("https://tallyapp.top/version.json");
                    conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    responseCode = conn.getResponseCode();
                } catch (Exception domainEx) {
                    // 域名不通，回退到 IP 直连
                    try {
                        java.net.URL fallbackUrl = new java.net.URL("http://47.97.78.35/version.json");
                        conn = (java.net.HttpURLConnection) fallbackUrl.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        responseCode = conn.getResponseCode();
                    } catch (Exception ipEx) {
                        throw ipEx;
                    }
                }
                android.util.Log.d("AboutActivity", "Version check response: " + responseCode);

                if (responseCode == 200) {
                    java.io.InputStream is = conn.getInputStream();
                    java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                    String latestVersion = json.optString("version", "");

                    runOnUiThread(() -> {
                        if (!latestVersion.isEmpty()) {
                            tvLatestVersion.setText("最新版本 v" + latestVersion);
                        } else {
                            tvLatestVersion.setText("最新版本 获取失败");
                        }
                    });
                } else {
                    android.util.Log.e("AboutActivity", "Version check failed with code: " + responseCode);
                    runOnUiThread(() -> tvLatestVersion.setText("最新版本 获取失败"));
                }
                conn.disconnect();
            } catch (Exception e) {
                android.util.Log.e("AboutActivity", "Version check error: " + e.getMessage(), e);
                runOnUiThread(() -> tvLatestVersion.setText("最新版本 获取失败"));
            }
        }).start();
    }
}