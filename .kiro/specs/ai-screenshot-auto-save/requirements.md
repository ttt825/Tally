# Requirements Document

## Introduction

本需求文档定义了"AI记账截图自动保存"功能的需求规格。该功能允许用户在使用AI截图记账时，自动将截图保存到账单的照片备注中，方便用户后续查看原始账单凭证。该功能需要与现有的照片备份系统集成，确保用户已配置照片存储路径后才能使用。

## Glossary

- **AI_Screenshot_Auto_Save_System**: AI记账截图自动保存系统，负责在截图记账时自动保存截图到账单照片备注的功能模块
- **Photo_Backup_System**: 照片备份系统，负责管理账单照片存储路径和照片备份功能的现有系统
- **AI_Setting_Activity**: AI记账配置页面，用户配置AI记账相关设置的界面
- **Photo_Backup_Settings_Activity**: 照片备注设置页面，用户配置照片备份路径和开关的界面
- **Screenshot_Accounting**: 截图记账，用户通过上传截图使用AI识别并创建账单的功能
- **Transaction**: 账单交易记录，包含日期、金额、分类、备注等信息的数据实体
- **Photo_Path**: 照片路径，存储在Transaction实体中的photoPath字段，指向账单关联的照片文件

## Requirements

### Requirement 1: 添加截图自动保存开关

**User Story:** 作为用户，我希望在AI记账配置页面看到"保存账单截图"开关，以便控制是否在截图记账时自动保存截图。

#### Acceptance Criteria

1. THE AI_Setting_Activity SHALL display a "保存账单截图" switch control below the "启用AI记账" switch
2. THE AI_Setting_Activity SHALL display descriptive text "开启后，截图记账时自动将截图保存到账单照片备注" below the switch title
3. THE AI_Screenshot_Auto_Save_System SHALL persist the switch state in SharedPreferences with key "ai_screenshot_auto_save"
4. WHEN the user toggles the switch, THE AI_Screenshot_Auto_Save_System SHALL save the new state immediately
5. WHEN the AI_Setting_Activity is opened, THE AI_Screenshot_Auto_Save_System SHALL load and display the previously saved switch state

### Requirement 2: 照片备注前置条件验证

**User Story:** 作为用户，当我尝试开启"保存账单截图"功能但未配置照片备注时，我希望系统引导我去配置照片备注，以确保截图能够正确保存。

#### Acceptance Criteria

1. WHEN the user attempts to enable the "保存账单截图" switch, THE AI_Screenshot_Auto_Save_System SHALL check if photo backup is enabled
2. IF photo backup is not enabled, THEN THE AI_Screenshot_Auto_Save_System SHALL display a dialog with message "需要先开启照片备注功能"
3. THE dialog SHALL provide two action buttons: "去设置" and "取消"
4. WHEN the user clicks "去设置", THE AI_Screenshot_Auto_Save_System SHALL navigate to Photo_Backup_Settings_Activity
5. WHEN the user clicks "取消", THE AI_Screenshot_Auto_Save_System SHALL keep the switch in disabled state
6. WHEN the user attempts to enable the switch, THE AI_Screenshot_Auto_Save_System SHALL check if photo backup path is configured
7. IF photo backup path is empty or invalid, THEN THE AI_Screenshot_Auto_Save_System SHALL display a dialog with message "需要先设置照片存储路径"
8. IF both photo backup is enabled and path is configured, THEN THE AI_Screenshot_Auto_Save_System SHALL allow the switch to be enabled

### Requirement 3: 截图自动保存到账单

**User Story:** 作为用户，当我使用截图记账并开启了"保存账单截图"功能时，我希望截图自动保存到生成的账单中，以便后续查看原始凭证。

#### Acceptance Criteria

1. WHEN a user uploads a screenshot in Screenshot_Accounting, THE AI_Screenshot_Auto_Save_System SHALL check if "ai_screenshot_auto_save" is enabled
2. IF "ai_screenshot_auto_save" is enabled, THEN THE AI_Screenshot_Auto_Save_System SHALL save the screenshot to the configured photo backup path
3. THE AI_Screenshot_Auto_Save_System SHALL generate a unique filename using pattern "screenshot_{timestamp}_{random}.jpg"
4. THE AI_Screenshot_Auto_Save_System SHALL save the screenshot file to the photo backup directory with JPEG format at 85% quality
5. WHEN the user saves a Transaction from the screenshot, THE AI_Screenshot_Auto_Save_System SHALL set the Transaction's Photo_Path field to the saved screenshot file path
6. IF the screenshot save operation fails, THEN THE AI_Screenshot_Auto_Save_System SHALL log the error and continue without blocking transaction creation
7. THE AI_Screenshot_Auto_Save_System SHALL preserve the original screenshot resolution when saving (no additional scaling beyond existing compression)

### Requirement 4: 多账单截图处理

**User Story:** 作为用户，当一张截图识别出多个账单时，我希望每个账单都能关联到同一张截图，以便查看完整的原始凭证。

#### Acceptance Criteria

1. WHEN a screenshot generates multiple Transaction drafts, THE AI_Screenshot_Auto_Save_System SHALL save the screenshot file only once
2. THE AI_Screenshot_Auto_Save_System SHALL reuse the same screenshot file path for all Transaction records generated from the same screenshot
3. WHEN the user saves multiple transactions from one screenshot, THE AI_Screenshot_Auto_Save_System SHALL set the same Photo_Path value for all saved transactions
4. THE AI_Screenshot_Auto_Save_System SHALL not create duplicate screenshot files for transactions from the same source image

### Requirement 5: 照片备份路径变更处理

**User Story:** 作为用户，当我更改照片备份路径后，我希望新的截图保存到新路径，而已保存的截图路径保持不变，以确保历史数据的完整性。

#### Acceptance Criteria

1. WHEN the photo backup path is changed in Photo_Backup_Settings_Activity, THE AI_Screenshot_Auto_Save_System SHALL use the new path for subsequent screenshot saves
2. THE AI_Screenshot_Auto_Save_System SHALL not modify or move existing screenshot files when the backup path changes
3. THE AI_Screenshot_Auto_Save_System SHALL preserve existing Transaction Photo_Path references when the backup path changes
4. WHEN saving a new screenshot, THE AI_Screenshot_Auto_Save_System SHALL always use the current photo backup path from SharedPreferences

### Requirement 6: 功能状态指示

**User Story:** 作为用户，我希望在"保存账单截图"开关旁看到清晰的状态提示，以便了解该功能是否可用以及需要满足什么条件。

#### Acceptance Criteria

1. WHEN photo backup is disabled, THE AI_Setting_Activity SHALL display a warning hint "需要先开启照片备注" below the switch
2. WHEN photo backup is enabled but path is not configured, THE AI_Setting_Activity SHALL display a warning hint "需要先设置照片存储路径" below the switch
3. WHEN both photo backup is enabled and path is configured, THE AI_Setting_Activity SHALL display a success hint "功能已就绪" in green color below the switch
4. THE AI_Setting_Activity SHALL update the hint text dynamically when the user returns from Photo_Backup_Settings_Activity
5. THE hint text SHALL use color #FF9800 for warning states and #4CAF50 for success state

### Requirement 7: 权限和错误处理

**User Story:** 作为用户，当截图保存过程中出现错误时，我希望系统能够妥善处理，不影响正常的记账流程。

#### Acceptance Criteria

1. IF the photo backup directory is not accessible, THEN THE AI_Screenshot_Auto_Save_System SHALL log the error and allow transaction creation without photo
2. IF the screenshot file write operation fails, THEN THE AI_Screenshot_Auto_Save_System SHALL log the error and set Photo_Path to empty string
3. THE AI_Screenshot_Auto_Save_System SHALL not display error dialogs during screenshot save failures to avoid interrupting user workflow
4. WHEN a screenshot save fails, THE AI_Screenshot_Auto_Save_System SHALL log the error with details including file path and exception message
5. THE AI_Screenshot_Auto_Save_System SHALL verify write permissions to the photo backup directory before attempting to save
6. IF write permissions are insufficient, THEN THE AI_Screenshot_Auto_Save_System SHALL silently skip the save operation and continue

### Requirement 8: 配置持久化和加载

**User Story:** 作为开发者，我需要确保"保存账单截图"功能的配置能够正确持久化和加载，以保证功能的稳定性。

#### Acceptance Criteria

1. THE AI_Screenshot_Auto_Save_System SHALL store the feature enable state in SharedPreferences with name "app_prefs"
2. THE AI_Screenshot_Auto_Save_System SHALL use key "ai_screenshot_auto_save" with boolean type for the enable state
3. THE AI_Screenshot_Auto_Save_System SHALL default to false when the key does not exist
4. WHEN loading configuration, THE AI_Screenshot_Auto_Save_System SHALL read from SharedPreferences on the main thread
5. WHEN saving configuration, THE AI_Screenshot_Auto_Save_System SHALL use SharedPreferences.Editor.apply() for asynchronous persistence
6. THE AI_Screenshot_Auto_Save_System SHALL reload the configuration when AI_Setting_Activity resumes from background

