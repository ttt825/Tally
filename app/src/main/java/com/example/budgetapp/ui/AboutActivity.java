package com.example.budgetapp.ui;

import android.util.Log;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.budgetapp.R;
import com.example.budgetapp.database.TransactionStats;
import com.example.budgetapp.viewmodel.TransactionViewModel;
import com.example.budgetapp.utils.ThreadPoolManager;
import java.util.Calendar;

public class AboutActivity extends AppCompatActivity {

    private TransactionViewModel financeViewModel;
    private TextView tvStatsInfo;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_about);

        tvStatsInfo = findViewById(R.id.tv_stats_info);

        TextView tvAppVersion = findViewById(R.id.tv_app_version);
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            tvAppVersion.setText("当前版本 v" + version);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("Tally", "Error", e);
            tvAppVersion.setText("当前版本 未知");
        }

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

        financeViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        loadStatistics();

        findViewById(R.id.btn_user_notice).setOnClickListener(v -> 
            startActivity(new Intent(this, UserNoticeActivity.class)));
        findViewById(R.id.btn_donate).setOnClickListener(v -> 
            startActivity(new Intent(this, DonateActivity.class)));
    }

    private void loadStatistics() {
        ThreadPoolManager.getInstance().executeBackground(() -> {
            TransactionStats stats = financeViewModel.getTransactionStatsSync();
            runOnUiThread(() -> updateStatistics(stats));
        });
    }

    private void updateStatistics(TransactionStats stats) {
        if (stats == null || stats.count == 0) {
            tvStatsInfo.setText("开始记下你的第一笔账单吧");
            return;
        }

        long days = calculateDays(stats.earliestDate);
        String info = String.format("已坚持记账 %d 天，共记账 %d 条", days, stats.count);
        tvStatsInfo.setText(info);
    }

    private long calculateDays(long firstTimestamp) {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar startDate = Calendar.getInstance();
        startDate.setTimeInMillis(firstTimestamp);
        startDate.set(Calendar.HOUR_OF_DAY, 0);
        startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);

        long diff = today.getTimeInMillis() - startDate.getTimeInMillis();
        return (diff / (1000 * 60 * 60 * 24)) + 1;
    }
}