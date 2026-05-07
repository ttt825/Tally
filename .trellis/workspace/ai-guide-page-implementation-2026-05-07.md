# AI 记账配置指南页面实施 - 2026-05-07

## 任务概述
在"关于"页面中新增"AI 记账配置指南"入口，提供详细的 AI 记账配置教程。

## 实施内容

### 1. 页面创建
- **布局文件**: `app/src/main/res/layout/activity_ai_guide.xml`
- **Activity 类**: `app/src/main/java/com/example/budgetapp/ui/AiGuideActivity.java`
- **注册**: 已在 `AndroidManifest.xml` 中注册

### 2. 页面结构

#### 功能简介
- 介绍 AI 记账的三种方式：文本、截图、语音
- 说明配置后可实现的功能

#### 配置步骤（以 LongCat 为例）
**步骤 1：获取接口地址**
- 访问 LongCat 平台
- 找到接口文档中的接口端点
- 复制 OpenAI 格式的接入端点
- 可点击链接：
  - `https://longcat.chat/platform/usage`
  - `https://api.longcat.chat/openai`

**步骤 2：创建 API Key**
- 访问 API Keys 页面
- 创建新密钥
- 复制生成的 Key
- 可点击链接：
  - `https://longcat.chat/platform/api_keys`

**步骤 3：选择模型**
- 查看支持的模型列表
- 选择合适的模型（文本/视觉/音频）
- LongCat 推荐：`LongCat-Flash-Chat-2602-Exps`

#### 应用内配置
8 步详细配置流程：
1. 进入设置 → AI 记账配置
2. 填写接口地址
3. 填写 API Key
4. 填写文本模型
5. 填写视觉模型（可选）
6. 测试连接
7. 保存配置
8. 开启启用开关

#### 其他平台配置
- 说明支持其他兼容 OpenAI 格式的平台
- 配置方法与 LongCat 相同
- 不列举具体平台详情
- 提示优先选择国内服务商

#### 注意事项
- 模型能力说明
- 费用说明
- 网络要求
- 隐私安全
- 首次使用建议

#### 常见问题
- Q: 测试连接失败怎么办？
- Q: 如何知道模型是否支持视觉输入？
- Q: AI 识别不准确怎么办？
- Q: 可以同时配置多个平台吗？
  - A: **可以，但需要分别配置。应用支持配置多个 AI 服务，您可以根据需要切换使用不同的平台。**

### 3. 功能特性

#### 可点击链接
实现了 URL 点击跳转功能：
- 点击后使用浏览器打开链接
- 如果无法打开浏览器，自动复制到剪贴板
- 链接带下划线，视觉上可识别

#### 示例文本可复制
实现了示例文本长按复制功能：
- **步骤 1 示例**：`https://api.longcat.chat/openai` - 长按复制接口地址
- **步骤 2 URL**：`https://longcat.chat/platform/api_keys` - 长按复制链接
- **步骤 2 示例**：`ak-xxxxxxxxxxxxxxxxxxxxxxxx` - 长按复制 API Key 格式
- **步骤 3 模型**：`LongCat-Flash-Chat-2602-Exps` - 长按复制模型名称
- 复制成功后显示 Toast 提示

#### 使用提醒
在页面顶部添加橙色背景的使用提醒框：
- 提示用户点击蓝色链接可直接跳转
- 提示用户长按灰色示例框可快速复制
- 使用醒目的橙色配色（`#FFF3E0` 背景 + `#FF6F00` 标题）

#### 跳转入口
在 AI 记账配置页面添加指南入口：
- 位置：默认连接信息标题右侧
- 显示："配置指南 ›"
- 颜色：使用主题蓝色 (`@color/app_blue`)
- 点击后跳转到 AiGuideActivity
- 更加简洁，节省页面空间

#### 夜间模式适配
完整适配夜间模式，所有颜色使用颜色资源：
- **提醒框背景**：
  - 日间：`#FFF3E0`（浅橙色）
  - 夜间：`#3E2723`（深棕色）
- **提醒标题**：
  - 日间：`#FF6F00`（橙色）
  - 夜间：`#FFD54F`（黄色）
- **提醒文字**：
  - 日间：`#E65100`（深橙色）
  - 夜间：`#FFB74D`（浅橙色）
- **示例框背景**：
  - 日间：`#F5F5F5`（浅灰色）
  - 夜间：`#2C2C2C`（深灰色）
- **链接颜色**：
  - 日间：`#327ffc`（蓝色）
  - 夜间：`#FFD700`（金黄色）

#### 沉浸式设计
- 全局沉浸式布局
- 自动处理系统栏 Insets
- 支持自定义背景模式

#### 视觉设计
- 使用 CardView 包裹内容
- 步骤卡片使用 `@drawable/bg_input_field` 背景
- 代码示例使用灰色背景 + 等宽字体
- 重要提示使用红色文字（`@color/income_red`）
- 分隔线使用半透明灰色

### 4. 入口位置
- **位置**: 设置 → 关于 → AI 记账配置指南
- **顺序**: 位于"隐私政策"和"捐赠支持"之间
- **修改文件**: `app/src/main/res/layout/activity_about.xml`

## 技术实现

### 可点击链接实现
```java
private void setupClickableUrl(int textViewId, String url) {
    TextView textView = findViewById(textViewId);
    String text = textView.getText().toString();
    SpannableString spannableString = new SpannableString(text);
    
    int startIndex = text.indexOf(url);
    if (startIndex != -1) {
        int endIndex = startIndex + url.length();
        
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                openUrl(url);
            }
            
            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
            }
        };
        
        spannableString.setSpan(clickableSpan, startIndex, endIndex, 
                               Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(spannableString);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}

private void openUrl(String url) {
    try {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    } catch (Exception e) {
        // 失败时复制到剪贴板
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("URL", url);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "已复制链接到剪贴板", Toast.LENGTH_SHORT).show();
    }
}
```

### 示例文本可复制实现
```java
private void setupCopyableText(int textViewId, String textToCopy) {
    TextView textView = findViewById(textViewId);
    if (textView == null) return;
    
    textView.setTextIsSelectable(true);
    textView.setOnLongClickListener(v -> {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("示例文本", textToCopy);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "已复制：" + textToCopy, Toast.LENGTH_SHORT).show();
        return true;
    });
}
```

## 修改记录

### 2026-05-07
1. **初始创建**
   - 创建布局文件和 Activity 类
   - 实现基本内容结构
   - 添加可点击链接功能

2. **内容优化**
   - 简化"其他平台配置"部分，不列举具体平台
   - 说明配置方法与 LongCat 相同

3. **多平台配置说明修正**
   - 更正"常见问题"中的多平台配置说明
   - 从"仅支持配置一个平台"改为"可以，但需要分别配置"
   - 补充说明：应用支持配置多个 AI 服务，可以切换使用

4. **示例文本可复制功能**
   - 为示例文本添加长按复制功能
   - 包括接口地址、API Key 格式、模型名称等
   - 复制成功后显示 Toast 提示

5. **使用提醒和跳转入口**
   - 在指南页面顶部添加使用提醒（橙色背景）
   - 说明点击蓝色链接可跳转、长按灰色框可复制
   - 在 AI 配置页面添加"AI 记账配置指南"入口
   - 点击可跳转到详细配置教程

6. **夜间模式适配**
   - 为 AI 指南页面添加夜间模式颜色资源
   - 提醒框：日间橙色 (`#FFF3E0`) → 夜间深棕色 (`#3E2723`)
   - 提醒标题：日间橙色 (`#FF6F00`) → 夜间黄色 (`#FFD54F`)
   - 提醒文字：日间深橙 (`#E65100`) → 夜间浅橙 (`#FFB74D`)
   - 示例框背景：日间浅灰 (`#F5F5F5`) → 夜间深灰 (`#2C2C2C`)
   - 链接颜色：日间蓝色 (`#327ffc`) → 夜间金黄 (`#FFD700`)

7. **跳转入口位置优化**
   - 将"AI 记账配置指南"入口从独立卡片改为标题右侧链接
   - 位置：默认连接信息标题右边
   - 显示为："配置指南 ›"
   - 节省页面空间，更加简洁

8. **复制功能优化和样式统一**
   - 为所有可操作的示例框右侧添加操作提示文字
   - 统一样式：示例框 + 右侧提示文字的横向布局
   - 操作提示：
     - **点击跳转**：用于链接（步骤 1、步骤 2 的访问链接）
     - **长按复制**：用于示例内容（接口地址、模型名称）
   - 可操作内容：
     - 步骤 1：LongCat 平台链接 → 点击跳转 🔗
     - 步骤 1：接口地址示例 → 长按复制 📋
     - 步骤 2：API Keys 页面链接 → 点击跳转 🔗（仅点击，不可长按复制）
     - 步骤 3：模型名称 → 长按复制 📋
   - 不可操作内容：
     - 步骤 2：API Key 占位符 ❌（无实际意义）
   - 提示文字使用次要文本颜色 (`@color/text_secondary`)

## 验收标准
- [x] 页面布局完整，内容清晰易读
- [x] 可点击链接正常工作
- [x] 示例文本可长按复制
- [x] 使用提醒显示在页面顶部
- [x] AI 配置页面有跳转入口（标题右侧）
- [x] 夜间模式完整适配（提醒框、链接、示例框）
- [x] 编译测试通过
- [x] 入口位置正确（关于页面 + AI 配置页面）
- [x] 沉浸式布局正常
- [x] 多平台配置说明准确

## 相关文件
- `app/src/main/res/layout/activity_ai_guide.xml`
- `app/src/main/java/com/example/budgetapp/ui/AiGuideActivity.java`
- `app/src/main/res/layout/activity_about.xml`
- `app/src/main/java/com/example/budgetapp/ui/AboutActivity.java`
- `app/src/main/res/layout/activity_ai_setting.xml` (新增跳转入口)
- `app/src/main/java/com/example/budgetapp/ui/AiSettingActivity.java` (新增跳转逻辑)
- `app/src/main/res/values/colors.xml` (新增 AI 指南颜色资源)
- `app/src/main/res/values-night/colors.xml` (新增夜间模式颜色资源)
- `app/src/main/AndroidManifest.xml`

## 编译状态
✅ BUILD SUCCESSFUL (2026-05-07)
- 38 actionable tasks: 8 executed, 30 up-to-date
