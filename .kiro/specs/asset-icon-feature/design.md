# 设计文档 - 资产图标功能

## 简介

本文档描述资产图标功能的技术设计。该功能允许用户在设置页面管理图标库（添加、编辑、删除 SVG 图标），并在添加或编辑资产时从图标库中选择图标。应用使用 AndroidSVG 库解析 SVG 字符串并显示在资产列表中，提升视觉识别度和用户体验。

## 概述

### 核心目标

1. **图标库管理**：提供独立的图标库管理页面，用户可以添加、编辑、删除 SVG 图标
2. **图标选择**：在添加/编辑资产时，用户可以从图标库中选择图标
3. **图标显示**：在资产列表、转移对话框等界面中显示图标
4. **SVG 解析**：使用 AndroidSVG 库解析 SVG 字符串并转换为 Drawable
5. **颜色适配**：根据背景颜色自动调整图标颜色，确保可见性
6. **性能优化**：使用 LruCache 缓存解析结果，提升性能
7. **数据同步**：集成 WebDAV 自动同步功能

### 技术约束

- **存储位置**：`app/app/src/` (遵循项目结构)
- **数据库**：Room (SQLite)
- **架构**：MVVM (ViewModel + LiveData)
- **SVG 库**：AndroidSVG 1.4 (com.caverock:androidsvg:1.4)
- **最低 SDK**：与项目保持一致
- **线程模型**：数据库操作在后台线程，UI 更新在主线程

### 功能范围

**包含功能**:
- 图标库数据存储（AssetIcon 实体类和 AssetIconDao）
- 资产账户图标关联（AssetAccount.iconId 字段）
- 图标库管理页面（AssetIconManagementActivity）
- 添加/编辑图标对话框（IconAddEditDialog）
- 图标选择对话框（IconSelectorDialog）
- SVG 解析和缓存（SvgIconManager）
- 图标颜色适配
- WebDAV 自动同步集成

**不包含功能**:
- 图标在线下载
- 图标分类管理
- 图标搜索功能（可选，如果图标数量较多可后续添加）


## 系统架构设计

### 架构概览

系统采用 MVVM 架构，分为以下层次：

1. **UI 层**：Activity、Fragment、Dialog、Adapter
2. **ViewModel 层**：AssetIconViewModel（管理图标数据）
3. **数据层**：AssetIconDao、AssetAccountDao（数据库访问）
4. **工具层**：SvgIconManager、SvgValidator（SVG 处理）

### 核心组件

#### 1. 数据库层

**AssetIcon 实体类** (`app/app/src/main/java/com/example/budgetapp/database/AssetIcon.java`)
- 字段：id, name, svgData, createdTime
- 表名：asset_icons

**AssetIconDao 接口** (`app/app/src/main/java/com/example/budgetapp/database/AssetIconDao.java`)
- insertIcon(), updateIcon(), deleteIcon()
- getIconById(), getAllIcons(), getIconCount()

**AssetAccount 修改** (添加 iconId 字段)
- iconId: INTEGER, DEFAULT 0
- 0 表示未设置图标，> 0 表示关联到图标库

**AssetAccountDao 修改** (新增方法)
- getAssetsByIconIdSync(int iconId): 查询使用指定图标的资产
- clearIconId(int iconId): 清除图标关联

**AppDatabase 修改**
- 添加 AssetIcon 实体类
- 版本号：20  21
- 迁移脚本：MIGRATION_20_21

#### 2. ViewModel 层

**AssetIconViewModel** (`app/app/src/main/java/com/example/budgetapp/viewmodel/AssetIconViewModel.java`)
- 管理图标数据的业务逻辑
- 提供 LiveData 供 UI 观察
- 处理图标的增删改查
- 集成 WebDAV 自动同步

#### 3. UI 层

**AssetIconManagementActivity** (`app/app/src/main/java/com/example/budgetapp/ui/AssetIconManagementActivity.java`)
- 图标库管理页面
- RecyclerView + GridLayoutManager (3 列)
- FloatingActionButton (添加图标)
- 空状态提示

**IconAddEditDialog** (`app/app/src/main/java/com/example/budgetapp/ui/IconAddEditDialog.java`)
- 添加/编辑图标对话框
- SVG 输入框、名称输入框
- 实时预览
- 验证和保存

**IconSelectorDialog** (`app/app/src/main/java/com/example/budgetapp/ui/IconSelectorDialog.java`)
- 图标选择对话框
- 网格布局显示所有图标
- 选择回调

**AssetIconAdapter** (`app/app/src/main/java/com/example/budgetapp/ui/AssetIconAdapter.java`)
- RecyclerView 适配器
- 显示图标网格
- 点击和长按事件

#### 4. 工具层

**SvgIconManager** (`app/app/src/main/java/com/example/budgetapp/util/SvgIconManager.java`)
- SVG 解析：使用 AndroidSVG 库
- 缓存管理：LruCache (最多 50 个图标)
- 颜色适配：根据背景亮度计算图标颜色

**SvgValidator** (`app/app/src/main/java/com/example/budgetapp/util/SvgValidator.java`)
- SVG 字符串验证
- 大小检查（< 50KB）
- 格式检查
- 解析测试

### 数据流设计

#### 添加图标流程

```
用户点击 FAB
   显示 IconAddEditDialog
   用户输入名称和 SVG 代码
   SvgValidator.validate(svgData)
   验证通过  AssetIconViewModel.insertIcon()
   AssetIconDao.insertIcon()
   数据库插入
   BackupManager.triggerAutoUploadIfEnabled()
   LiveData 通知 UI 更新
```

#### 编辑图标流程

```
用户点击图标
   显示 IconAddEditDialog (编辑模式)
   预填充图标数据
   用户修改
   SvgValidator.validate(svgData)
   AssetIconViewModel.updateIcon()
   SvgIconManager.clearCache(iconId)
   BackupManager.triggerAutoUploadIfEnabled()
   LiveData 通知 UI 更新
```

#### 删除图标流程

```
用户长按图标
   显示上下文菜单
   选择"删除"
   查询使用该图标的资产数量
   显示确认对话框
   AssetIconViewModel.deleteIcon()
   事务：clearIconId() + deleteIcon()
   SvgIconManager.clearCache(iconId)
   BackupManager.triggerAutoUploadIfEnabled()
   LiveData 通知 UI 更新
```

#### 图标显示流程

```
AssetsFragment  RecyclerView.onBindViewHolder()
   检查 assetAccount.iconId
   如果 iconId > 0:
     SvgIconManager.getFromCache(iconId)
     如果缓存命中: 返回 Drawable
     如果缓存未命中:
       从数据库查询 AssetIcon
       SvgIconManager.parseAndCache()
       AndroidSVG.getFromString()
       存入缓存
     应用颜色过滤器
     ImageView.setImageDrawable()
   如果 iconId == 0:
     ImageView.setImageResource(R.drawable.ic_default_placeholder)
```
