package com.example.budgetapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.example.budgetapp.database.AppDatabase;
import com.example.budgetapp.security.SecureStorage;
import com.example.budgetapp.ui.AuthActivity;
import com.example.budgetapp.utils.ThreadPoolManager;

public class MyApplication extends Application {

    public static volatile boolean isUnlocked = false;
    private boolean downgradeWarningShown = false;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    isUnlocked = false;
                }
            }
        }, filter);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                if (!(activity instanceof AuthActivity)) {
                    SecureStorage secureStorage = new SecureStorage(activity);
                    if (secureStorage.hasPassword() && !isUnlocked) {
                        Intent intent = new Intent(activity, AuthActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION); 
                        activity.startActivity(intent);
                    }
                }

                if (AppDatabase.downgradeDetected && !downgradeWarningShown) {
                    downgradeWarningShown = true;
                    new AlertDialog.Builder(activity)
                            .setTitle("数据库降级警告")
                            .setMessage("检测到应用版本降级，数据库已被重建，历史数据可能丢失。请尽快通过备份恢复数据。")
                            .setPositiveButton("我知道了", null)
                            .setCancelable(false)
                            .show();
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {}
            @Override
            public void onActivityStopped(Activity activity) {}
            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        ThreadPoolManager.getInstance().shutdown();
        AppDatabase.databaseWriteExecutor.shutdown();
    }
}