# Requirements Document

## Introduction

本文档定义了"版本检查功能"的需求规范。该功能将在"关于页面"(AboutActivity)中添加"最新版本"显示,通过连接阿里云轻量应用服务器(tallyapp.top)获取最新版本信息,并在"当前版本"上方显示,帮助用户了解应用更新状态。

## Glossary

- **Version_Check_System**: 版本检查系统,负责从远程服务器获取最新版本信息并与当前版本比对
- **Aliyun_Server**: 阿里云轻量应用服务器,位于 tallyapp.top 域名,提供版本信息 API 接口
- **About_Activity**: 关于页面,显示应用信息、版本号和统计数据的 Activity
- **Version_Info_File**: 版本信息文件,存储在对象存储服务中的 JSON 格式文件,包含最新版本号、更新日期、下载链接等信息
- **Current_Version**: 当前版本,应用当前安装的版本号,从 PackageInfo 获取
- **Latest_Version**: 最新版本,从对象存储服务获取的最新可用版本号
- **Version_Comparator**: 版本比较器,用于比较两个版本号的大小关系

## Requirements

### Requirement 1: 获取最新版本信息

**User Story:** 作为用户,我希望应用能自动从服务器获取最新版本信息,以便了解是否有新版本可用。

#### Acceptance Criteria

1. WHEN About_Activity 启动时, THE Version_Check_System SHALL 向 Aliyun_Server (tallyapp.top) 发起 HTTPS 请求获取 Version_Info_File
2. THE Version_Check_System SHALL 在后台线程执行网络请求,不阻塞主线程
3. WHEN 网络请求成功且返回有效 JSON 数据时, THE Version_Check_System SHALL 解析 Version_Info_File 并提取 Latest_Version、更新日期、下载链接和更新说明
4. IF 网络请求失败(超时、无网络、服务器错误)时, THEN THE Version_Check_System SHALL 静默处理错误并显示默认提示文本
5. THE Version_Check_System SHALL 设置网络请求超时时间为 10 秒

### Requirement 2: 显示最新版本信息

**User Story:** 作为用户,我希望在关于页面看到最新版本号,以便快速了解应用更新状态。

#### Acceptance Criteria

1. THE About_Activity SHALL 在"当前版本"TextView 上方添加"最新版本"TextView
2. WHEN Version_Check_System 成功获取 Latest_Version 时, THE About_Activity SHALL 显示"最新版本 v{version}"
3. WHILE 网络请求进行中时, THE About_Activity SHALL 显示"检查更新中..."
4. IF 网络请求失败时, THEN THE About_Activity SHALL 显示"最新版本 获取失败"
5. THE About_Activity SHALL 使用与"当前版本"相同的文本样式(字体大小 12sp、颜色 #888888、居中对齐)

### Requirement 3: 版本比对与更新提示

**User Story:** 作为用户,我希望应用能自动比较当前版本和最新版本,并在有新版本时提示我,以便及时更新应用。

#### Acceptance Criteria

1. WHEN Version_Check_System 成功获取 Latest_Version 时, THE Version_Comparator SHALL 比较 Current_Version 和 Latest_Version
2. IF Latest_Version 大于 Current_Version 时, THEN THE About_Activity SHALL 将"最新版本"文本颜色改为强调色(#FF6B6B)
3. IF Latest_Version 等于 Current_Version 时, THEN THE About_Activity SHALL 保持"最新版本"文本颜色为默认灰色(#888888)
4. THE Version_Comparator SHALL 使用语义化版本比较规则(major.minor.patch),例如 1.2.1 < 1.2.2 < 1.3.0 < 2.0.0
5. IF Latest_Version 大于 Current_Version 时, THEN THE About_Activity SHALL 在"最新版本"TextView 后添加"(有新版本)"提示文本

### Requirement 4: 版本信息文件格式

**User Story:** 作为开发者,我需要定义版本信息文件的标准格式,以便后端和客户端能正确交互。

#### Acceptance Criteria

1. THE Version_Info_File SHALL 使用 JSON 格式存储
2. THE Version_Info_File SHALL 包含以下必需字段: versionName (字符串)、versionCode (整数)、updateDate (字符串,ISO 8601 格式)
3. THE Version_Info_File SHALL 包含以下可选字段: downloadUrl (字符串)、updateNotes (字符串)、minSupportedVersion (字符串)
4. THE Version_Check_System SHALL 验证 JSON 数据完整性,确保必需字段存在且类型正确
5. IF Version_Info_File 格式无效或缺少必需字段时, THEN THE Version_Check_System SHALL 视为请求失败并显示"获取失败"

### Requirement 5: 点击更新引导

**User Story:** 作为用户,当有新版本可用时,我希望能点击版本信息跳转到下载页面,以便快速更新应用。

#### Acceptance Criteria

1. WHEN Latest_Version 大于 Current_Version 且 Version_Info_File 包含有效 downloadUrl 时, THE About_Activity SHALL 使"最新版本"TextView 可点击
2. WHEN 用户点击"最新版本"TextView 时, THE About_Activity SHALL 显示更新对话框,包含版本号、更新日期和更新说明
3. THE 更新对话框 SHALL 提供"立即更新"和"稍后提醒"两个按钮
4. WHEN 用户点击"立即更新"时, THE About_Activity SHALL 使用系统浏览器打开 downloadUrl
5. WHEN 用户点击"稍后提醒"时, THE About_Activity SHALL 关闭对话框

### Requirement 6: 网络权限与安全

**User Story:** 作为开发者,我需要确保版本检查功能具有必要的网络权限和安全措施,以保护用户隐私和数据安全。

#### Acceptance Criteria

1. THE Version_Check_System SHALL 使用 HTTPS 协议连接 Aliyun_Server,不使用 HTTP
2. THE Version_Check_System SHALL 验证服务器 SSL 证书有效性
3. THE Version_Check_System SHALL 仅发送必要的请求头信息,不包含用户隐私数据
4. THE Version_Check_System SHALL 在 AndroidManifest.xml 中声明 INTERNET 权限(如果尚未声明)
5. THE Version_Check_System SHALL 遵循 Android 网络安全配置(Network Security Config)

### Requirement 7: 错误处理与用户体验

**User Story:** 作为用户,当版本检查失败时,我希望应用能优雅地处理错误,不影响正常使用,以获得良好的用户体验。

#### Acceptance Criteria

1. IF 网络请求超时时, THEN THE Version_Check_System SHALL 记录日志并显示"获取失败"
2. IF 设备无网络连接时, THEN THE Version_Check_System SHALL 静默处理,不显示错误提示对话框
3. IF 服务器返回 404 或 500 错误时, THEN THE Version_Check_System SHALL 记录错误代码并显示"获取失败"
4. THE Version_Check_System SHALL 不因版本检查失败而阻止 About_Activity 的其他功能正常运行
5. THE Version_Check_System SHALL 使用 try-catch 捕获所有可能的异常(网络异常、JSON 解析异常、空指针异常)

### Requirement 8: 版本信息缓存

**User Story:** 作为用户,我希望应用能缓存版本信息,避免每次打开关于页面都发起网络请求,以节省流量和提升响应速度。

#### Acceptance Criteria

1. WHEN Version_Check_System 成功获取 Version_Info_File 时, THE Version_Check_System SHALL 将版本信息缓存到 SharedPreferences
2. THE Version_Check_System SHALL 缓存以下信息: Latest_Version、updateDate、downloadUrl、缓存时间戳
3. WHEN About_Activity 启动时, IF 缓存存在且缓存时间小于 24 小时, THEN THE Version_Check_System SHALL 优先显示缓存数据,然后在后台更新
4. WHEN About_Activity 启动时, IF 缓存不存在或缓存时间超过 24 小时, THEN THE Version_Check_System SHALL 立即发起网络请求
5. THE Version_Check_System SHALL 使用 SharedPreferences 键名前缀"version_check_"存储缓存数据

## Version Info File Example

```json
{
  "versionName": "1.2.0",
  "versionCode": 12,
  "updateDate": "2024-01-15T10:30:00Z",
  "downloadUrl": "https://tallyapp.top/download/budgetapp-v1.2.0.apk",
  "updateNotes": "1. 新增资产图标功能\n2. 优化 AI 记账体验\n3. 修复已知问题",
  "minSupportedVersion": "1.0.0"
}
```

## Technical Notes

- **对象存储 URL**: `https://tallyapp.top/version.json`
- **网络库**: 使用 Android 原生 HttpURLConnection 或 OkHttp(如果项目已集成)
- **JSON 解析**: 使用 org.json.JSONObject(Android 内置)或 Gson(如果项目已集成)
- **线程管理**: 使用 AsyncTask、ExecutorService 或 Kotlin Coroutines(如果适用)
- **UI 更新**: 使用 Handler 或 runOnUiThread 在主线程更新 UI
