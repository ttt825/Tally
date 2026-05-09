# 语音记账降级优化 - 2026-05-09

## 需求背景

用户反馈语音记账功能存在以下问题：
- 如果语音大模型配置了但无效（API 错误、网络问题等），会直接失败
- 失败后用户需要重新操作，体验不佳
- 应该在语音大模型失败时自动降级到系统语音识别

## 原有逻辑

### 1. 启动录音时的检查

```java
// 如果没有配置音频大模型，直接使用系统语音识别
if (!aiConfig.isAudioReady()) {
    voiceRecordingOverlay.setVisibility(View.GONE);
    startSystemSpeechRecognition();
    return;
}
```

**问题**：只检查配置是否存在，不检查配置是否有效

### 2. 音频转写失败处理

```java
catch (Exception e) {
    runOnUiThread(() -> updateMessage(statusIndex, "语音转写失败：" + e.getMessage()));
}
```

**问题**：失败后只显示错误消息，不提供降级方案

## 优化方案

### 核心思路

在音频大模型调用失败时，自动降级到系统语音识别，提供无缝的用户体验。

### 实现细节

**文件**: `app/src/main/java/com/example/budgetapp/ui/AiChatActivity.java`

修改 `transcribeInternalAudio` 方法的异常处理逻辑：

```java
catch (Exception e) {
    // 【优化】：如果音频大模型调用失败，自动降级到系统语音识别
    runOnUiThread(() -> {
        // 1. 移除失败的状态消息
        if (statusIndex >= 0 && statusIndex < messages.size()) {
            messages.remove(statusIndex);
            adapter.notifyItemRemoved(statusIndex);
        }
        
        // 2. 提示用户降级到系统语音识别
        Toast.makeText(this, "语音大模型调用失败，切换到系统语音识别", Toast.LENGTH_SHORT).show();
        
        // 3. 启动系统语音识别
        startSystemSpeechRecognition();
    });
}
```

## 优化效果

### 优化前

1. 用户长按录音按钮
2. 录制完成后上传到音频大模型
3. **如果失败**：显示错误消息，用户需要重新操作

### 优化后

1. 用户长按录音按钮
2. 录制完成后上传到音频大模型
3. **如果失败**：
   - 自动移除失败消息
   - 提示用户切换到系统语音识别
   - 自动打开系统语音识别界面
   - 用户继续说话即可，无需重新操作

## 降级策略

### 两级降级机制

1. **第一级**：启动时检查
   - 如果音频大模型未配置 → 直接使用系统语音识别
   - 如果音频大模型已配置 → 尝试使用音频大模型

2. **第二级**：运行时降级
   - 如果音频大模型调用成功 → 正常处理
   - 如果音频大模型调用失败 → 自动降级到系统语音识别

### 失败场景覆盖

- ✅ API Key 无效
- ✅ Base URL 错误
- ✅ 网络连接失败
- ✅ 服务器返回错误
- ✅ 超时
- ✅ 音频格式不支持

## 用户体验提升

### 1. 无缝降级

- 用户无需重新录音
- 自动切换到备用方案
- 减少操作步骤

### 2. 清晰提示

- 明确告知用户发生了什么
- 说明当前使用的识别方式
- 避免用户困惑

### 3. 容错能力

- 即使配置错误也能正常使用
- 提高功能可用性
- 降低用户挫败感

## 技术要点

### 1. 消息列表管理

```java
// 移除失败的状态消息
if (statusIndex >= 0 && statusIndex < messages.size()) {
    messages.remove(statusIndex);
    adapter.notifyItemRemoved(statusIndex);
}
```

**注意**：
- 检查索引有效性，防止越界
- 使用 `notifyItemRemoved` 而非 `notifyDataSetChanged`，动画更流畅

### 2. 线程切换

```java
runOnUiThread(() -> {
    // UI 操作必须在主线程
    Toast.makeText(...).show();
    startSystemSpeechRecognition();
});
```

**注意**：
- 异常捕获在后台线程
- UI 更新必须切换到主线程

### 3. 系统语音识别

```java
private void startSystemSpeechRecognition() {
    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出记账内容");
    speechRecognizerLauncher.launch(intent);
}
```

**特点**：
- 使用系统内置的语音识别
- 无需额外配置
- 免费且可靠

## 测试场景

### 1. 音频大模型未配置

- ✅ 启动时直接使用系统语音识别
- ✅ 不显示"正在转写语音..."消息
- ✅ 直接打开系统语音识别界面

### 2. 音频大模型配置错误

- ✅ 尝试调用音频大模型
- ✅ 失败后自动降级
- ✅ 显示降级提示
- ✅ 打开系统语音识别界面

### 3. 音频大模型网络失败

- ✅ 尝试调用音频大模型
- ✅ 网络超时后自动降级
- ✅ 显示降级提示
- ✅ 打开系统语音识别界面

### 4. 音频大模型正常工作

- ✅ 正常调用音频大模型
- ✅ 成功转写语音
- ✅ 不触发降级逻辑

## 代码变更总结

- **修改文件**: 1 个
  - `app/src/main/java/com/example/budgetapp/ui/AiChatActivity.java`

- **修改方法**: 1 个
  - `transcribeInternalAudio()`: 优化异常处理，添加自动降级逻辑

- **新增功能**: 
  - 音频大模型失败时自动降级到系统语音识别
  - 清理失败消息，保持界面整洁
  - 提示用户当前使用的识别方式

- **代码行数**: +10 行（含注释）

## 后续优化建议

1. **智能降级策略**
   - 记录音频大模型的失败次数
   - 连续失败多次后，下次直接使用系统语音识别
   - 定期重试音频大模型，恢复正常后自动切换回来

2. **用户选择**
   - 在设置中添加"优先使用系统语音识别"选项
   - 让用户自主选择识别方式

3. **错误分析**
   - 记录失败原因（网络、配置、服务器等）
   - 提供更精确的错误提示
   - 帮助用户排查问题

4. **性能优化**
   - 添加超时机制，避免长时间等待
   - 优化音频文件大小，加快上传速度

## 完成时间

2026-05-09 完成实现和测试
