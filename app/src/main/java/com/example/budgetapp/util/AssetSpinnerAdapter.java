package com.example.budgetapp.util;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.budgetapp.R;
import com.example.budgetapp.database.AssetAccount;

/**
 * 通用资产 Spinner 适配器，支持显示 SVG 图标和自定义颜色。
 * 用于手动记账、自动记账、磁贴记账、AI记账等所有资产选择场景。
 */
public class AssetSpinnerAdapter extends ArrayAdapter<AssetAccount> {

    private final LayoutInflater inflater;

    public AssetSpinnerAdapter(@NonNull Context context) {
        super(context, R.layout.item_spinner_asset);
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        return createView(position, convertView, parent);
    }

    private View createView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = convertView;
        ViewHolder holder;

        if (view == null) {
            view = inflater.inflate(R.layout.item_spinner_asset, parent, false);
            holder = new ViewHolder();
            holder.ivIcon = view.findViewById(R.id.iv_asset_icon);
            holder.tvName = view.findViewById(R.id.tv_asset_name);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }

        AssetAccount asset = getItem(position);
        if (asset != null) {
            holder.tvName.setText(asset.name);

            // 绑定 SVG 图标
            if (holder.ivIcon != null) {
                AssetIconHelper.bindSvgIcon(holder.ivIcon, asset.svgIcon);
            }

            // 应用颜色
            applyTextColor(holder.tvName, asset);
        }

        return view;
    }

    private void applyTextColor(TextView tv, AssetAccount asset) {
        Context context = tv.getContext();
        if (asset.colorType == 1) {
            tv.setTextColor(ContextCompat.getColor(context, R.color.income_red));
        } else if (asset.colorType == 2) {
            tv.setTextColor(ContextCompat.getColor(context, R.color.expense_green));
        } else if (asset.colorType == 3 && asset.customColorHex != null && !asset.customColorHex.isEmpty()) {
            try {
                tv.setTextColor(Color.parseColor(asset.customColorHex));
            } catch (Exception e) {
                tv.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            }
        } else {
            try {
                tv.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            } catch (Exception e) {
                tv.setTextColor(Color.BLACK);
            }
        }
    }

    private static class ViewHolder {
        ImageView ivIcon;
        TextView tvName;
    }
}
