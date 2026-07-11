package com.example.budgetapp.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.budgetapp.R;

import java.util.ArrayList;
import java.util.List;

public class OpenSourceLicensesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_open_source_licenses);

        View rootView = findViewById(R.id.open_source_root);
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

        RecyclerView recyclerView = findViewById(R.id.recycler_open_source);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new LicenseAdapter(buildLicenseList()));
    }

    private List<LicenseItem> buildLicenseList() {
        List<LicenseItem> list = new ArrayList<>();
        list.add(new LicenseItem("AndroidX AppCompat", "提供向后兼容的 UI 组件与主题支持。", "https://github.com/androidx/androidx"));
        list.add(new LicenseItem("Material Components for Android", "实现 Material Design 风格的控件与样式。", "https://github.com/material-components/material-components-android"));
        list.add(new LicenseItem("AndroidX ConstraintLayout", "灵活高效的约束布局系统。", "https://github.com/androidx/constraintlayout"));
        list.add(new LicenseItem("AndroidX Navigation", "Fragment 页面导航与路由管理。", "https://github.com/androidx/androidx"));
        list.add(new LicenseItem("AndroidX Room", "本地 SQLite 数据库 ORM 与访问框架。", "https://github.com/androidx/androidx"));
        list.add(new LicenseItem("AndroidX Lifecycle", "生命周期感知的 ViewModel 与 LiveData。", "https://github.com/androidx/androidx"));
        list.add(new LicenseItem("MPAndroidChart", "账单统计图表绘制库。", "https://github.com/PhilJay/MPAndroidChart"));
        list.add(new LicenseItem("Gson", "JSON 数据序列化与反序列化。", "https://github.com/google/gson"));
        list.add(new LicenseItem("AndroidX CardView", "圆角卡片容器视图。", "https://github.com/androidx/androidx"));
        list.add(new LicenseItem("AndroidX DocumentFile", "SAF 文档树文件访问支持。", "https://github.com/androidx/androidx"));
        list.add(new LicenseItem("AndroidX Biometric", "指纹识别等生物识别认证。", "https://github.com/androidx/androidx"));
        list.add(new LicenseItem("Google Flexbox Layout", "弹性盒子布局，支持流式标签排布。", "https://github.com/google/flexbox-layout"));
        list.add(new LicenseItem("lunar (6tail)", "农历、节气与节假日计算。", "https://github.com/6tail/lunar-java"));
        list.add(new LicenseItem("SmoothBottomBar", "底部导航栏动效组件。", "https://github.com/ibrahimsn98/SmoothBottomBar"));
        list.add(new LicenseItem("BlurView", "高斯模糊背景效果视图。", "https://github.com/Dimezis/BlurView"));
        list.add(new LicenseItem("AndroidLiquidGlassView", "Android 液态玻璃效果视图组件，支持折射、色散与高光，为 Tab 栏提供液态玻璃质感。版本：1.0.1。", "https://github.com/QmDeve/AndroidLiquidGlassView"));
        return list;
    }

    private static class LicenseItem {
        final String name;
        final String description;
        final String url;

        LicenseItem(String name, String description, String url) {
            this.name = name;
            this.description = description;
            this.url = url;
        }
    }

    private static class LicenseAdapter extends RecyclerView.Adapter<LicenseAdapter.ViewHolder> {

        private final List<LicenseItem> items;

        LicenseAdapter(List<LicenseItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_open_source_license, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LicenseItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvDesc.setText(item.description);
            holder.tvUrl.setText(item.url);
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
                v.getContext().startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView tvName;
            final TextView tvDesc;
            final TextView tvUrl;

            ViewHolder(View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_license_name);
                tvDesc = itemView.findViewById(R.id.tv_license_desc);
                tvUrl = itemView.findViewById(R.id.tv_license_url);
            }
        }
    }
}
