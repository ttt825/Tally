
# 需求文档 - 资产图标功能（图标库管理方案）

## 简介

为 Budget App 的资产模块添加自定义图标功能。用户可以在设置页面管理图标库（添加、编辑、删除 SVG 图标），然后在添加或编辑资产时从图标库中选择图标。应用使用 AndroidSVG 库解析 SVG 字符串并显示在资产列表中，提升视觉识别度和用户体验。

## 术语表

- **Asset_Icon_System**: 资产图标管理系统，负责图标库的管理、图标的选择、解析和显示
- **Icon_Library**: 图标库，存储用户添加的所有自定义图标
- **AssetIcon**: 图标实体类，存储单个图标的 ID、名称、SVG 数据和创建时间
- **AssetIconDao**: 图标数据访问对象，提供图标的增删改查操作
- **AssetIconManagementActivity**: 图标库管理页面，用户在此添加、编辑、删除图标
- **Icon_Grid**: 图标网格布局，在图标库管理页面和图标选择对话框中展示图标
- **Icon_Add_Dialog**: 添加/编辑图标对话框，提供 SVG 输入框、名称输入框和实时预览
- **Icon_Selector_Dialog**: 图标选择对话框，在添加/编辑资产时弹出，显示图标库中的所有图标
- **AndroidSVG_Library**: AndroidSVG 库 (com.caverock:androidsvg:1.4)，用于解析 SVG 字符串
- **SVG_Parser**: SVG 解析器，将 SVG XML 字符串转换为 Drawable 对象
- **AssetAccount**: 资产账户实体类，存储资产的所有信息
- **iconId**: 资产账户关联的图标 ID 字段（整型，0 表示未设置图标）
- **Default_Placeholder**: 默认占位符图标 (ic_default_placeholder.xml)，当资产未设置图标或图标已被删除时显示
- **Icon_Display_Size**: 图标显示尺寸，在资产列表中显示的图标大小（24dp × 24dp）
- **Icon_Preview_Size**: 图标预览尺寸，在图标库管理和添加对话框中的预览大小（64dp × 64dp）
- **Icon_Grid_Size**: 图标网格项尺寸，在图标库管理页面中的图标卡片大小（80dp × 80dp）
- **SVG_Size_Limit**: SVG 大小限制，< 50KB 以避免性能问题
- **Icon_Name_Length**: 图标名称长度限制，1-20 个字符
- **SVG_Cache**: SVG 解析结果缓存（LruCache），避免重复解析同一图标
- **PictureDrawable**: Android 图形类，用于渲染 SVG 解析后的 Picture 对象

## 需求

### 需求 1: 图标库数据存储

**用户故事:** 作为开发者，我希望创建独立的图标库表来存储用户添加的所有图标，以便实现图标的集中管理和复用。

#### 验收标准

1. THE Asset_Icon_System SHALL 创建 AssetIcon 实体类，包含以下字段：
   - id (int, 主键, 自增)
   - name (String, 图标名称)
   - svgData (String, SVG XML 字符串)
   - createdTime (long, 创建时间戳)
2. THE Asset_Icon_System SHALL 创建 AssetIconDao 接口，提供以下方法：
   - insertIcon(AssetIcon icon) - 插入新图标
   - updateIcon(AssetIcon icon) - 更新图标
   - deleteIcon(AssetIcon icon) - 删除图标
   - getIconById(int id) - 根据 ID 查询图标
   - getAllIcons() - 查询所有图标（返回 LiveData<List<AssetIcon>>）
   - getIconCount() - 查询图标总数
3. THE Asset_Icon_System SHALL 在 AppDatabase 中注册 AssetIcon 实体类
4. THE Asset_Icon_System SHALL 支持数据库迁移以添加 asset_icons 表
5. THE svgData SHALL 允许存储最大 100KB 的文本数据
6. THE name SHALL 限制长度为 1-20 个字符

### 需求 2: 资产账户图标关联

**用户故事:** 作为开发者，我希望在 AssetAccount 表中添加 iconId 字段来关联图标库中的图标，以便每个资产都能引用一个图标。

#### 验收标准

1. THE AssetAccount SHALL 添加 iconId 字段（int 类型，默认值为 0）
2. WHEN iconId 为 0 时，THE Asset_Icon_System SHALL 显示 Default_Placeholder
3. WHEN iconId 大于 0 时，THE Asset_Icon_System SHALL 从 AssetIcon 表中查询对应的图标
4. WHEN 查询到的图标不存在时（图标已被删除），THE Asset_Icon_System SHALL 显示 Default_Placeholder
5. THE Asset_Icon_System SHALL 支持数据库迁移以添加 iconId 字段到 AssetAccount 表
6. THE Asset_Icon_System SHALL 在数据库迁移时将所有现有资产的 iconId 设置为 0

### 需求 3: 设置页面集成

**用户故事:** 作为用户，我希望在设置页面看到"资产图标管理"菜单项，以便进入图标库管理页面。

#### 验收标准

1. THE Asset_Icon_System SHALL 在 activity_settings.xml 中添加"资产图标管理"菜单项
2. THE "资产图标管理"菜单项 SHALL 位于"自动资产设置"菜单项之后
3. THE "资产图标管理"菜单项 SHALL 使用与其他菜单项一致的样式（padding: 20dp, textSize: 16sp）
4. THE "资产图标管理"菜单项 SHALL 在上下添加分隔线（1dp, #1F888888）
5. WHEN 用户点击"资产图标管理"菜单项时，THE Asset_Icon_System SHALL 启动 AssetIconManagementActivity
6. THE Asset_Icon_System SHALL 在 AndroidManifest.xml 中注册 AssetIconManagementActivity


### 需求 4: 图标库管理页面 UI

**用户故事:** 作为用户，我希望看到一个清晰的图标库管理页面，显示所有已添加的图标，以便管理我的图标库。

#### 验收标准

1. THE AssetIconManagementActivity SHALL 使用 Toolbar 显示标题"资产图标管理"
2. THE AssetIconManagementActivity SHALL 使用 RecyclerView 以网格布局（GridLayoutManager, 3-4 列）展示图标
3. THE Icon_Grid SHALL 为每个图标显示：
   - 图标预览（Icon_Preview_Size: 64dp × 64dp）
   - 图标名称（TextView, textSize: 14sp）
4. THE Icon_Grid SHALL 使用 CardView 包裹每个图标项，圆角 12dp，elevation 2dp
5. THE Icon_Grid SHALL 在图标项之间保持 8-12dp 的间距
6. THE AssetIconManagementActivity SHALL 显示 FloatingActionButton（FAB）用于添加新图标
7. THE FAB SHALL 位于屏幕右下角，使用"+"图标
8. WHEN 图标库为空时，THE AssetIconManagementActivity SHALL 显示空状态提示："还没有添加图标，点击右下角 + 按钮添加"
9. THE AssetIconManagementActivity SHALL 支持滚动查看所有图标
10. THE AssetIconManagementActivity SHALL 使用 ViewModel 和 LiveData 观察图标数据变化

### 需求 5: 添加图标功能

**用户故事:** 作为用户，我希望能够添加新图标到图标库，通过粘贴 SVG 代码和输入图标名称，以便扩展我的图标库。

#### 验收标准

1. WHEN 用户点击 FAB 时，THE Asset_Icon_System SHALL 弹出 Icon_Add_Dialog
2. THE Icon_Add_Dialog SHALL 显示标题"添加图标"
3. THE Icon_Add_Dialog SHALL 包含以下元素：
   - 多行文本输入框（EditText, inputType="textMultiLine", hint="粘贴 SVG XML 代码"）
   - "从剪贴板粘贴"快捷按钮
   - 图标名称输入框（EditText, hint="图标名称（如：支付宝）", maxLength=20）
   - 实时预览区域（ImageView, Icon_Preview_Size: 64dp × 64dp, 标签"预览"）
   - "保存"和"取消"按钮
4. WHEN 用户点击"从剪贴板粘贴"按钮时，THE Icon_Add_Dialog SHALL 自动填充剪贴板中的文本内容到 SVG 输入框
5. WHEN 用户输入或修改 SVG 文本时，THE Icon_Add_Dialog SHALL 在 500ms 延迟后自动更新预览
6. WHEN SVG 解析成功时，THE Icon_Add_Dialog SHALL 在预览区域显示图标
7. WHEN SVG 解析失败时，THE Icon_Add_Dialog SHALL 在预览区域显示错误图标和提示文本
8. WHEN 用户点击"保存"时，THE Asset_Icon_System SHALL 验证输入并保存图标到数据库
9. WHEN 用户点击"取消"时，THE Icon_Add_Dialog SHALL 关闭对话框并放弃更改
10. THE Icon_Add_Dialog SHALL 使用 AlertDialog 或 BottomSheetDialog 展示，圆角和透明背景

### 需求 6: 编辑图标功能

**用户故事:** 作为用户，我希望能够编辑已有图标的名称和 SVG 代码，以便修正错误或更新图标。

#### 验收标准

1. WHEN 用户点击图标库中的某个图标时，THE Asset_Icon_System SHALL 弹出 Icon_Add_Dialog（编辑模式）
2. THE Icon_Add_Dialog（编辑模式）SHALL 显示标题"编辑图标"
3. THE Icon_Add_Dialog（编辑模式）SHALL 预填充该图标的名称和 SVG 代码
4. THE Icon_Add_Dialog（编辑模式）SHALL 在预览区域显示当前图标
5. WHEN 用户修改名称或 SVG 代码并点击"保存"时，THE Asset_Icon_System SHALL 更新数据库中的图标
6. THE Asset_Icon_System SHALL 立即在图标库管理页面反映图标变更
7. THE Asset_Icon_System SHALL 同时更新所有使用该图标的资产的显示（通过 LiveData 自动刷新）

### 需求 7: 删除图标功能

**用户故事:** 作为用户，我希望能够删除不需要的图标，并在删除前看到警告提示，以便安全地管理图标库。

#### 验收标准

1. WHEN 用户长按图标库中的某个图标时，THE Asset_Icon_System SHALL 显示上下文菜单或对话框，包含"编辑"和"删除"选项
2. WHEN 用户选择"删除"时，THE Asset_Icon_System SHALL 查询使用该图标的资产数量
3. WHEN 有资产正在使用该图标时，THE Asset_Icon_System SHALL 显示警告对话框："该图标正被 X 个资产使用，删除后这些资产将显示默认图标"
4. WHEN 没有资产使用该图标时，THE Asset_Icon_System SHALL 显示确认对话框："确定要删除该图标吗？"
5. WHEN 用户确认删除时，THE Asset_Icon_System SHALL 从数据库中删除该图标
6. WHEN 用户确认删除时，THE Asset_Icon_System SHALL 将所有使用该图标的资产的 iconId 设置为 0
7. THE Asset_Icon_System SHALL 立即在图标库管理页面移除该图标
8. THE Asset_Icon_System SHALL 同时更新所有受影响资产的显示（显示 Default_Placeholder）

### 需求 8: SVG 验证和错误处理

**用户故事:** 作为用户，当我粘贴的 SVG 代码无效或不支持时，我希望看到清晰的错误提示，以便了解问题并修正。

#### 验收标准

1. WHEN 用户输入的 SVG 字符串不是有效的 XML 格式时，THE Asset_Icon_System SHALL 显示错误提示"SVG 格式无效，请检查 XML 语法"
2. WHEN SVG 字符串大小超过 SVG_Size_Limit（50KB）时，THE Asset_Icon_System SHALL 显示警告"SVG 文件过大（> 50KB），可能影响性能"
3. WHEN SVG 字符串为空时，THE Asset_Icon_System SHALL 显示错误提示"请输入 SVG 代码"
4. WHEN 图标名称为空时，THE Asset_Icon_System SHALL 显示错误提示"请输入图标名称"
5. WHEN 图标名称超过 Icon_Name_Length（20 字符）时，THE Asset_Icon_System SHALL 显示错误提示"图标名称不能超过 20 个字符"
6. WHEN SVG 解析失败时，THE Asset_Icon_System SHALL 在预览区域显示错误图标（ic_error.xml）和提示文本"SVG 解析失败"
7. WHEN 存在验证错误时，THE Asset_Icon_System SHALL 禁用"保存"按钮，防止保存无效数据
8. THE Asset_Icon_System SHALL 使用 Toast 或 Snackbar 显示错误和警告信息
9. THE Asset_Icon_System SHALL 记录 SVG 解析错误到日志（Log.e）以便调试

### 需求 9: 资产添加/编辑时的图标选择

**用户故事:** 作为用户，我希望在添加或编辑资产时能够从图标库中选择图标，以便为资产设置视觉标识。

#### 验收标准

1. THE Asset_Icon_System SHALL 在添加/编辑资产对话框（dialog_add_asset.xml）中添加图标选择按钮
2. THE 图标选择按钮 SHALL 显示当前选择的图标（如果已选择）或默认图标（如果未选择）
3. THE 图标选择按钮 SHALL 位于资产名称输入框上方或左侧
4. WHEN 用户点击图标选择按钮时，THE Asset_Icon_System SHALL 弹出 Icon_Selector_Dialog
5. THE Icon_Selector_Dialog SHALL 显示标题"选择图标"
6. THE Icon_Selector_Dialog SHALL 以网格布局（3-4 列）展示图标库中的所有图标
7. WHEN 图标库为空时，THE Icon_Selector_Dialog SHALL 显示提示："图标库为空，请先在设置中添加图标"
8. WHEN 图标库为空时，THE Icon_Selector_Dialog SHALL 提供"前往添加"按钮，点击后跳转到 AssetIconManagementActivity
9. WHEN 用户选择某个图标时，THE Icon_Selector_Dialog SHALL 高亮显示该图标（蓝色边框或背景）
10. WHEN 用户点击"确定"时，THE Asset_Icon_System SHALL 保存选择的图标 ID 到 AssetAccount.iconId
11. WHEN 用户点击"取消"时，THE Icon_Selector_Dialog SHALL 关闭对话框并放弃更改
12. THE Asset_Icon_System SHALL 立即在图标选择按钮上显示新选择的图标


### 需求 10: SVG 解析和转换

**用户故事:** 作为开发者，我希望使用 AndroidSVG 库解析用户粘贴的 SVG 字符串并转换为 Drawable，以便在应用中显示自定义图标。

#### 验收标准

1. THE Asset_Icon_System SHALL 使用 AndroidSVG 库（com.caverock:androidsvg:1.4）解析 SVG 字符串
2. WHEN 解析 SVG 字符串时，THE SVG_Parser SHALL 调用 SVG.getFromString(svgData) 方法
3. WHEN SVG 解析成功时，THE SVG_Parser SHALL 调用 svg.renderToPicture() 生成 Picture 对象
4. THE SVG_Parser SHALL 将 Picture 对象包装为 PictureDrawable 用于 ImageView 显示
5. WHEN SVG 解析失败时，THE SVG_Parser SHALL 抛出异常并由错误处理模块捕获
6. THE SVG_Parser SHALL 支持标准 SVG 元素（path, rect, circle, ellipse, line, polyline, polygon）
7. THE SVG_Parser SHALL 在后台线程执行解析操作，避免阻塞 UI 线程
8. THE SVG_Parser SHALL 设置 SVG 渲染尺寸为目标显示尺寸（Icon_Display_Size 或 Icon_Preview_Size）

### 需求 11: 图标显示逻辑

**用户故事:** 作为用户，我希望在资产列表中看到图标正确显示，以便快速识别不同的资产账户。

#### 验收标准

1. WHEN 加载资产图标时，THE Asset_Icon_System SHALL 首先检查 AssetAccount.iconId 是否大于 0
2. WHEN iconId 大于 0 时，THE Asset_Icon_System SHALL 从数据库查询 AssetIcon
3. WHEN 查询到 AssetIcon 时，THE Asset_Icon_System SHALL 使用 SVG_Parser 解析 svgData 并显示
4. WHEN 查询不到 AssetIcon 时（图标已被删除），THE Asset_Icon_System SHALL 显示 Default_Placeholder
5. WHEN iconId 为 0 时，THE Asset_Icon_System SHALL 显示 Default_Placeholder
6. THE Asset_Icon_System SHALL 在资产列表项（item_asset_detail.xml）的资产名称左侧显示图标
7. THE Icon_Display_Size SHALL 为 24dp × 24dp
8. THE Asset_Icon_System SHALL 在图标和资产名称之间保持 8dp 的间距
9. THE Asset_Icon_System SHALL 保持图标的原始宽高比，不拉伸变形
10. THE Asset_Icon_System SHALL 在理财卡片（item_asset_investment.xml）中同样显示图标

### 需求 12: 图标颜色适配

**用户故事:** 作为用户，我希望图标颜色能够根据背景颜色自动调整，以便在不同背景下都能清晰可见。

#### 验收标准

1. WHEN 资产使用自定义背景颜色（colorType = 1 或 2）时，THE Asset_Icon_System SHALL 将图标颜色设置为白色
2. WHEN 资产使用默认背景（colorType = 0）时，THE Asset_Icon_System SHALL 将图标颜色设置为主题色（text_primary）
3. WHEN 资产使用自定义 HEX 颜色（colorType = 3）时，THE Asset_Icon_System SHALL 根据背景亮度自动选择图标颜色（白色或深色）
4. THE Asset_Icon_System SHALL 使用 ImageView.setColorFilter() 或 DrawableCompat.setTint() 动态设置图标颜色
5. WHEN 资产被设置为默认支付项时，THE Asset_Icon_System SHALL 保持图标可见且颜色为白色
6. THE Asset_Icon_System SHALL 计算背景亮度使用公式：(0.299 * R + 0.587 * G + 0.114 * B)
7. WHEN 背景亮度 > 128 时，THE Asset_Icon_System SHALL 使用深色图标
8. WHEN 背景亮度 <= 128 时，THE Asset_Icon_System SHALL 使用白色图标

### 需求 13: SVG 缓存和性能优化

**用户故事:** 作为开发者，我希望缓存 SVG 解析结果以提升性能，避免重复解析相同的图标。

#### 验收标准

1. THE Asset_Icon_System SHALL 使用 LruCache 缓存 SVG 解析结果
2. THE SVG_Cache SHALL 使用图标 ID（iconId）作为缓存键
3. THE SVG_Cache SHALL 设置最大缓存大小为 50 个图标
4. WHEN 加载图标时，THE Asset_Icon_System SHALL 首先检查缓存是否存在
5. WHEN 缓存命中时，THE Asset_Icon_System SHALL 直接使用缓存的 Drawable 对象
6. WHEN 缓存未命中时，THE Asset_Icon_System SHALL 解析 SVG 并将结果存入缓存
7. THE Asset_Icon_System SHALL 在后台线程异步加载图标，避免阻塞 UI
8. THE Asset_Icon_System SHALL 使用缩略图尺寸（Icon_Display_Size）渲染 SVG 以提升性能
9. WHEN 图标被更新或删除时，THE Asset_Icon_System SHALL 清除该图标的缓存

### 需求 14: 图标在其他界面的显示

**用户故事:** 作为用户，我希望图标不仅在资产列表中显示，还能在资产转移对话框等其他界面中显示，以便保持一致的用户体验。

#### 验收标准

1. THE Asset_Icon_System SHALL 在资产转移对话框（dialog_transfer_asset.xml）的 Spinner 中显示图标
2. THE Asset_Icon_System SHALL 在 Spinner 的每个选项前显示对应资产的图标
3. THE Asset_Icon_System SHALL 在添加/编辑资产对话框的图标选择按钮上显示当前选择的图标
4. THE Asset_Icon_System SHALL 确保图标在所有界面中使用相同的显示逻辑和颜色适配规则
5. THE Asset_Icon_System SHALL 不影响现有的资产名称、金额、货币符号等显示

### 需求 15: 默认占位符图标

**用户故事:** 作为用户，当我不选择图标时，系统应该显示一个默认占位符图标，以便保持界面一致性。

#### 验收标准

1. THE Asset_Icon_System SHALL 创建 Default_Placeholder (ic_default_placeholder.xml) 作为默认图标
2. THE Default_Placeholder SHALL 使用通用的钱包或资产图标设计
3. THE Default_Placeholder SHALL 使用 VectorDrawable 格式
4. WHEN 用户创建新资产且未选择图标时，THE Asset_Icon_System SHALL 显示 Default_Placeholder
5. WHEN 资产关联的图标被删除时，THE Asset_Icon_System SHALL 显示 Default_Placeholder
6. THE Default_Placeholder SHALL 支持颜色适配（与自定义图标相同的颜色规则）

### 需求 16: 图标库管理页面的长按操作

**用户故事:** 作为用户，我希望通过长按图标来快速访问编辑和删除功能，以便高效管理图标库。

#### 验收标准

1. WHEN 用户长按图标库中的某个图标时，THE Asset_Icon_System SHALL 显示上下文菜单或底部对话框
2. THE 上下文菜单 SHALL 包含"编辑"和"删除"两个选项
3. WHEN 用户选择"编辑"时，THE Asset_Icon_System SHALL 弹出 Icon_Add_Dialog（编辑模式）
4. WHEN 用户选择"删除"时，THE Asset_Icon_System SHALL 执行删除图标流程（需求 7）
5. THE 上下文菜单 SHALL 使用 Material Design 风格
6. THE Asset_Icon_System SHALL 在长按时提供触觉反馈（HapticFeedback）

### 需求 17: 依赖库集成

**用户故事:** 作为开发者，我希望正确集成 AndroidSVG 库，以便应用能够解析和渲染 SVG 图标。

#### 验收标准

1. THE Asset_Icon_System SHALL 在 app/build.gradle 中添加依赖 `implementation 'com.caverock:androidsvg:1.4'`
2. THE Asset_Icon_System SHALL 确保 AndroidSVG 库与项目的最低 SDK 版本兼容
3. THE Asset_Icon_System SHALL 在 ProGuard 规则中添加 AndroidSVG 的混淆保护规则（如果启用混淆）
4. THE Asset_Icon_System SHALL 验证 AndroidSVG 库在构建后能够正常工作

### 需求 18: 数据库迁移策略

**用户故事:** 作为开发者，我希望提供平滑的数据库迁移策略，以便用户从旧版本升级时不会丢失数据。

#### 验收标准

1. THE Asset_Icon_System SHALL 创建数据库迁移脚本，添加 asset_icons 表
2. THE Asset_Icon_System SHALL 创建数据库迁移脚本，在 asset_accounts 表中添加 iconId 字段（默认值 0）
3. THE 数据库迁移 SHALL 保留所有现有的资产数据
4. THE 数据库迁移 SHALL 在升级后自动执行
5. THE Asset_Icon_System SHALL 在迁移失败时记录错误日志并回滚
6. THE Asset_Icon_System SHALL 增加数据库版本号（version + 1）

### 需求 19: WebDAV 自动同步集成

**用户故事:** 作为用户，我希望图标库的变更能够自动触发 WebDAV 备份，以便我的图标数据能够同步到云端。

#### 验收标准

1. WHEN 用户添加新图标时，THE Asset_Icon_System SHALL 调用 BackupManager.triggerAutoUploadIfEnabled(context)
2. WHEN 用户更新图标时，THE Asset_Icon_System SHALL 调用 BackupManager.triggerAutoUploadIfEnabled(context)
3. WHEN 用户删除图标时，THE Asset_Icon_System SHALL 调用 BackupManager.triggerAutoUploadIfEnabled(context)
4. THE Asset_Icon_System SHALL 在后台线程执行自动同步，不阻塞 UI
5. THE Asset_Icon_System SHALL 遵循现有的 WebDAV 自动同步规范（参见 AGENTS.md）

### 需求 20: 用户体验优化

**用户故事:** 作为用户，我希望图标管理功能提供流畅的用户体验，包括加载动画、空状态提示和友好的错误提示。

#### 验收标准

1. WHEN 加载图标库时，THE AssetIconManagementActivity SHALL 显示加载动画（ProgressBar）
2. WHEN 图标库为空时，THE AssetIconManagementActivity SHALL 显示空状态插图和提示文本
3. WHEN 保存图标时，THE Icon_Add_Dialog SHALL 显示加载动画并禁用按钮
4. WHEN 删除图标时，THE Asset_Icon_System SHALL 显示确认对话框，避免误操作
5. THE Asset_Icon_System SHALL 在所有操作成功后显示 Toast 提示（如"图标已保存"、"图标已删除"）
6. THE Asset_Icon_System SHALL 在所有操作失败时显示友好的错误提示
7. THE Asset_Icon_System SHALL 使用 Material Design 动画和过渡效果
8. THE Asset_Icon_System SHALL 在图标选择对话框中支持搜索功能（可选，如果图标数量较多）
