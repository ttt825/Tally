# Implementation Plan: AI记账截图自动保存功能

## Overview

本实现计划将"AI记账截图自动保存"功能集成到现有的Android预算管理应用中。该功能允许用户在使用AI截图记账时，自动将截图保存到账单的照片备注中，方便后续查看原始凭证。

实现策略：
1. 创建 `ScreenshotAutoSaveManager` 核心管理类
2. 在 `AiSettingActivity` 中添加UI控件和前置条件验证
3. 在 `AiChatActivity` 中集成截图保存逻辑
4. 修改 `TransactionDraft` 添加 `photoPath` 字段支持
5. 创建必要的UI布局文件（对话框）

## Tasks

- [x] 1. 创建 ScreenshotAutoSaveManager 核心管理类
  - 创建 `app/src/main/java/com/example/budgetapp/util/ScreenshotAutoSaveManager.java`
  - 实现配置读写方法（`isEnabled()`, `setEnabled()`）
  - 实现前置条件验证方法（`validatePrerequisites()`）
  - 实现截图保存方法（`saveScreenshot()`）
  - 实现唯一文件名生成方法（`generateUniqueFileName()`）
  - 实现 `ValidationResult` 内部类
  - 使用 SharedPreferences 存储配置（key: `ai_screenshot_auto_save`）
  - 使用 DocumentFile API 保存截图到照片备份目录
  - 文件格式：JPEG，质量85%，命名规则：`screenshot_{timestamp}_{random}.jpg`
  - 错误处理：所有失败场景静默处理，记录日志但不阻塞流程
  - _Requirements: 1.3, 1.4, 1.5, 2.1, 2.6, 2.8, 3.1, 3.2, 3.3, 3.4, 3.6, 7.1, 7.2, 7.5, 7.6, 8.1, 8.2, 8.3, 8.5_

- [ ]* 1.1 为 ScreenshotAutoSaveManager 编写单元测试
  - 测试配置持久化和加载
  - 测试前置条件验证逻辑
  - 测试文件名生成的唯一性和格式
  - 测试错误处理场景（目录不可访问、权限不足）
  - _Requirements: 1.3, 1.5, 2.1, 2.6, 3.3, 7.1, 7.2, 7.5_

- [x] 2. 修改 TransactionDraft 添加 photoPath 字段
  - 在 `app/src/main/java/com/example/budgetapp/ai/TransactionDraft.java` 中添加 `public String photoPath` 字段
  - 修改 `toTransaction()` 方法，将 `photoPath` 赋值给 `transaction.photoPath`
  - 确保字段初始化为 null 或空字符串
  - _Requirements: 3.5_

- [x] 3. 创建前置条件验证对话框布局
  - 创建 `app/src/main/res/layout/dialog_prerequisite_check.xml`
  - 包含标题 TextView（"提示"）
  - 包含消息 TextView（`tv_message`，显示错误信息）
  - 包含两个按钮：`btn_cancel`（"取消"）和 `btn_go_settings`（"去设置"）
  - 使用 Material Design 风格，圆角卡片背景
  - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.7_

- [x] 4. 修改 AiSettingActivity 添加截图自动保存开关
  - [x] 4.1 修改 activity_ai_setting.xml 布局文件
    - 在"启用AI记账"开关下方添加新的开关区域
    - 添加 SwitchCompat（`switchScreenshotAutoSave`）："保存账单截图"
    - 添加描述 TextView（`tvScreenshotAutoSaveDesc`）："开启后，截图记账时自动将截图保存到账单照片备注"
    - 添加状态提示 TextView（`tvScreenshotAutoSaveHint`），动态显示功能状态
    - _Requirements: 1.1, 1.2, 6.1, 6.2, 6.3, 6.5_

  - [x] 4.2 在 AiSettingActivity.java 中实现开关逻辑
    - 添加 `ScreenshotAutoSaveManager` 成员变量
    - 在 `onCreate()` 中初始化管理器和UI控件
    - 实现 `initScreenshotAutoSaveSwitch()` 方法：加载开关状态，设置监听器
    - 实现 `showPrerequisiteDialog()` 方法：显示前置条件验证对话框
    - 实现 `updateScreenshotAutoSaveHint()` 方法：动态更新状态提示文本和颜色
    - 在 `onResume()` 中调用 `updateScreenshotAutoSaveHint()` 刷新状态
    - 开关切换时验证前置条件，失败则显示对话框并保持禁用状态
    - 对话框"去设置"按钮导航到照片备注设置页面
    - _Requirements: 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 6.1, 6.2, 6.3, 6.4, 6.5, 8.4, 8.6_

- [ ]* 4.3 为 AiSettingActivity 编写 UI 测试
  - 测试开关显示和状态加载
  - 测试前置条件验证对话框显示
  - 测试状态提示文本和颜色更新
  - 测试导航到照片备注设置页面
  - _Requirements: 1.1, 1.2, 2.2, 2.3, 2.4, 6.1, 6.2, 6.3, 6.5_

- [x] 5. 修改 AiChatActivity 集成截图保存功能
  - [x] 5.1 添加截图保存核心逻辑
    - 添加 `ScreenshotAutoSaveManager` 和 `currentScreenshotPath` 成员变量
    - 在 `onCreate()` 中初始化 `ScreenshotAutoSaveManager`
    - 修改 `handleImageUri()` 方法：在处理截图后调用 `saveScreenshot()` 并缓存路径
    - 修改 `addDraftCardsReply()` 方法：为所有 draft 设置相同的 `photoPath`，完成后清空缓存
    - 修改 `DraftCardController.collectDraft()` 方法：从缓存读取 `photoPath` 并设置到 draft
    - _Requirements: 3.1, 3.2, 3.5, 4.1, 4.2, 4.3, 4.4_

  - [ ]* 5.2 为 AiChatActivity 编写集成测试
    - 测试截图上传后自动保存功能
    - 测试多账单场景下共享同一截图文件
    - 测试 photoPath 正确设置到 Transaction
    - 测试功能禁用时不保存截图
    - _Requirements: 3.1, 3.2, 3.5, 4.1, 4.2, 4.3, 4.4_

- [x] 6. Checkpoint - 功能验证和测试
  - 手动测试完整流程：开启功能 → 截图记账 → 验证截图保存 → 验证账单关联
  - 测试前置条件验证：未开启照片备注、未设置路径时的提示
  - 测试多账单场景：一张截图生成多个账单，验证共享同一文件
  - 测试错误处理：目录不可访问、权限不足时不影响记账
  - 测试路径变更：更改照片备份路径后新截图使用新路径
  - 运行所有单元测试和集成测试
  - 确保所有测试通过，询问用户是否有问题

- [ ]* 7. 编写属性测试（Property-Based Tests）
  - [ ]* 7.1 配置持久化 Round-Trip 属性测试
    - **Property 1: 配置持久化Round-Trip**
    - **Validates: Requirements 1.3, 1.5, 8.1, 8.2**
    - 使用 junit-quickcheck，100次迭代
    - 验证任意 boolean 值保存后加载返回相同值

  - [ ]* 7.2 文件名唯一性和格式属性测试
    - **Property 8: 文件名唯一性和格式**
    - **Validates: Requirements 3.3**
    - 验证生成的文件名符合 `screenshot_{timestamp}_{random}.jpg` 格式
    - 验证多次生成的文件名唯一性

  - [ ]* 7.3 多账单共享截图属性测试
    - **Property 13: 多账单共享截图文件**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4**
    - 验证一张截图生成多个 draft 时只创建一个文件
    - 验证所有 draft 的 photoPath 相同

  - [ ]* 7.4 错误处理不阻塞属性测试
    - **Property 11: 错误处理不阻塞交易创建**
    - **Validates: Requirements 3.6, 7.1, 7.2, 7.6**
    - 模拟各种错误场景（目录不可访问、写入失败）
    - 验证交易创建继续进行，photoPath 为空

## Notes

- 任务标记 `*` 为可选测试任务，可根据项目进度跳过以加快 MVP 交付
- 所有核心实现任务必须完成，确保功能正常工作
- 错误处理采用静默失败策略，不影响用户正常记账流程
- Transaction 实体已包含 `photoPath` 字段，无需修改数据库 schema
- 照片备份相关配置（`enable_photo_backup`, `photo_backup_uri`）已存在于 SharedPreferences
- 每个任务都引用了具体的需求编号，便于追溯验证
- Checkpoint 任务确保增量验证，及时发现问题
