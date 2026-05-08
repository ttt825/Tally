# -*- coding: utf-8 -*-
import re

# 读取文件
with open('app/src/main/java/com/example/budgetapp/ai/AiAccountingClient.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 定义新的 buildSystemPrompt 方法（使用原始字符串）
new_method = r'''    public String buildSystemPrompt(Context context) {
        StringBuilder builder = new StringBuilder();
        
        // Part 1: 静态规则（用户可编辑部分）
        String staticPrompt;
        if (PromptManager.hasCustomPrompt(context)) {
            // 使用自定义提示词
            staticPrompt = PromptManager.getCustomPrompt(context);
        } else {
            // 使用默认静态模板
            staticPrompt = PromptTemplate.buildEditableTemplate();
        }
        builder.append(staticPrompt);
        
        // Part 2: 动态内容（自动生成，对所有提示词都添加）
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String currentTime = sdf.format(new java.util.Date());

        // 添加当前时间
        builder.append("\n\n当前系统时间是：").append(currentTime).append("。");

        // 添加分类信息
        builder.append("\n\n支出分类与二级分类：");
        appendCategories(builder, buildPromptCategories(context, false), context);

        builder.append("\n\n收入分类与二级分类：");
        appendCategories(builder, buildPromptCategories(context, true), context);

        // 添加资产账户信息
        builder.append("\n\n可用资产账户：");
        appendAssets(builder, context);

        // 添加 JSON 示例
        builder.append("\n\n只返回 JSON 数组。示例：");
        builder.append("[");
        builder.append("{\"type\":0,\"amount\":5.00,\"category\":\"购物\",\"subCategory\":\"\",\"note\":\"美宜佳\",\"asset\":\"\",\"time\":\"")
                .append(currentTime)
                .append("\"},");
        builder.append("{\"type\":0,\"amount\":19.90,\"category\":\"购物\",\"subCategory\":\"\",\"note\":\"美宜佳\",\"asset\":\"\",\"time\":\"")
                .append(currentTime)
                .append("\"}");
        builder.append("]");

        return builder.toString();
    }'''

# 使用正则表达式替换整个方法
# 匹配从 "public String buildSystemPrompt(Context context) {" 到下一个方法开始之前
pattern = r'public String buildSystemPrompt\(Context context\) \{.*?^\s{4}\}'
replacement = new_method

content = re.sub(pattern, replacement, content, flags=re.DOTALL | re.MULTILINE)

# 写回文件
with open('app/src/main/java/com/example/budgetapp/ai/AiAccountingClient.java', 'w', encoding='utf-8') as f:
    f.write(content)

print("修复完成！")
