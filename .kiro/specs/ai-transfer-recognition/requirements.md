# Requirements Document

## Introduction

本文档定义 AI 记账转账识别功能的需求。该功能扩展现有 AI 记账模块，使其能够识别用户输入中的转账操作（资产间转移），并调用资产模块的 `transferAsset` 功能执行转账，而不是错误地将转账识别为普通的收入或支出交易。

## Glossary

- **AI_Accounting_Module**: AI 记账模块，负责解析用户自然语言输入并生成交易草稿
- **Transfer_Operation**: 转账操作，指资产账户之间的资金转移，包括提现、还款、账户互转等
- **TransactionDraft**: 交易草稿对象，AI 解析结果的数据结构
- **Asset_Module**: 资产模块，管理用户的资产账户（银行卡、电子钱包、信用卡等）
- **Source_Asset**: 转出资产，转账操作中资金的来源账户
- **Target_Asset**: 转入资产，转账操作中资金的目标账户
- **Transfer_Discount**: 转账优惠，转账过程中的手续费减免或优惠金额
- **FinanceViewModel**: 财务视图模型，提供 `transferAsset()` 方法执行资产转移
- **Transaction_Type**: 交易类型，0=支出，1=收入，2=转账，3=负债借入，4=借出
- **AI_Category_Rules**: AI 分类规则，基于关键词自动分类的规则系统

## Requirements

### Requirement 1: 识别转账关键词

**User Story:** 作为用户，我希望 AI 能够识别转账相关的关键词，以便系统能够正确判断我的输入是转账操作而不是普通收支。

#### Acceptance Criteria

1. WHEN 用户输入包含"转账"关键词, THE AI_Accounting_Module SHALL 将交易类型识别为转账
2. WHEN 用户输入包含"转入"关键词, THE AI_Accounting_Module SHALL 将交易类型识别为转账
3. WHEN 用户输入包含"转出"关键词, THE AI_Accounting_Module SHALL 将交易类型识别为转账
4. WHEN 用户输入包含"提现"关键词, THE AI_Accounting_Module SHALL 将交易类型识别为转账
5. WHEN 用户输入包含"还款"关键词, THE AI_Accounting_Module SHALL 将交易类型识别为转账
6. WHEN 用户输入包含"还信用卡"关键词, THE AI_Accounting_Module SHALL 将交易类型识别为转账
7. WHEN 用户输入包含"还花呗"关键词, THE AI_Accounting_Module SHALL 将交易类型识别为转账
8. WHEN 用户输入包含"还借呗"关键词, THE AI_Accounting_Module SHALL 将交易类型识别为转账

### Requirement 2: 解析转账双方资产

**User Story:** 作为用户，我希望 AI 能够从我的输入中识别转出和转入的资产账户，以便系统能够正确执行资产间的转移操作。

#### Acceptance Criteria

1. WHEN 用户输入描述转账操作, THE AI_Accounting_Module SHALL 识别 Source_Asset 名称
2. WHEN 用户输入描述转账操作, THE AI_Accounting_Module SHALL 识别 Target_Asset 名称
3. WHEN 用户输入"A转B到C"格式, THE AI_Accounting_Module SHALL 将A识别为 Source_Asset 且将C识别为 Target_Asset
4. WHEN 用户输入"A还B"格式, THE AI_Accounting_Module SHALL 将A识别为 Source_Asset 且将B识别为 Target_Asset
5. WHEN 用户输入"A提现到B"格式, THE AI_Accounting_Module SHALL 将A识别为 Source_Asset 且将B识别为 Target_Asset
6. WHEN Source_Asset 或 Target_Asset 无法识别, THE AI_Accounting_Module SHALL 在 JSON 输出中包含可识别的资产信息

### Requirement 3: 生成转账类型的交易草稿

**User Story:** 作为开发者，我希望 AI 模块能够生成包含转账信息的交易草稿，以便后续处理逻辑能够正确执行转账操作。

#### Acceptance Criteria

1. WHEN AI 识别为转账操作, THE AI_Accounting_Module SHALL 生成 Transaction_Type 为2的 TransactionDraft
2. WHEN 生成转账类型草稿, THE TransactionDraft SHALL 包含 targetAssetId 字段
3. WHEN 生成转账类型草稿, THE TransactionDraft SHALL 包含 discount 字段
4. WHEN 生成转账类型草稿, THE TransactionDraft SHALL 将 category 设置为"资产互转"
5. WHEN 生成转账类型草稿, THE TransactionDraft SHALL 包含转账金额 amount
6. WHEN 用户输入包含优惠信息, THE TransactionDraft SHALL 在 discount 字段记录优惠金额
7. WHEN 用户输入不包含优惠信息, THE TransactionDraft SHALL 将 discount 字段设置为0

### Requirement 4: 转账 JSON 输出格式

**User Story:** 作为开发者，我希望 AI 输出的 JSON 格式能够清晰表达转账信息，以便解析器能够正确提取转账相关字段。

#### Acceptance Criteria

1. WHEN AI 输出转账 JSON, THE JSON SHALL 包含 type 字段且值为2
2. WHEN AI 输出转账 JSON, THE JSON SHALL 包含 asset 字段表示 Source_Asset 名称
3. WHEN AI 输出转账 JSON, THE JSON SHALL 包含 targetAsset 字段表示 Target_Asset 名称
4. WHEN AI 输出转账 JSON, THE JSON SHALL 包含 amount 字段表示转账金额
5. WHEN AI 输出转账 JSON, THE JSON SHALL 包含 discount 字段表示优惠金额
6. WHEN AI 输出转账 JSON, THE JSON SHALL 包含 category 字段且值为"资产互转"
7. WHEN AI 输出转账 JSON, THE JSON SHALL 包含 note 字段记录转账备注
8. WHEN AI 输出转账 JSON, THE JSON SHALL 包含 time 字段记录转账时间

### Requirement 5: 解析转账 JSON 字段

**User Story:** 作为开发者，我希望 JSON 解析器能够正确提取转账相关字段，以便将 AI 输出转换为可执行的转账操作。

#### Acceptance Criteria

1. WHEN 解析器接收到 type=2 的 JSON, THE TransactionDraftMapper SHALL 识别为转账类型
2. WHEN 解析器识别到"转账"关键词, THE TransactionDraftMapper SHALL 将 type 规范化为2
3. WHEN 解析器识别到"transfer"关键词, THE TransactionDraftMapper SHALL 将 type 规范化为2
4. WHEN 解析 targetAsset 字段, THE TransactionDraftMapper SHALL 匹配资产账户并设置 targetAssetId
5. WHEN 解析 discount 字段, THE TransactionDraftMapper SHALL 提取优惠金额到 TransactionDraft
6. WHEN 解析转账类型 JSON, THE TransactionDraftMapper SHALL 固定 category 为"资产互转"
7. WHEN 解析转账类型 JSON, THE TransactionDraftMapper SHALL 跳过 AI_Category_Rules 应用

### Requirement 6: 执行转账操作

**User Story:** 作为用户，我希望确认转账信息后系统能够正确执行资产转移，以便我的资产账户余额得到准确更新。

#### Acceptance Criteria

1. WHEN 用户确认转账草稿, THE System SHALL 验证 Source_Asset 是否存在
2. WHEN 用户确认转账草稿, THE System SHALL 验证 Target_Asset 是否存在
3. WHEN Source_Asset 不存在, THE System SHALL 显示错误提示
4. WHEN Target_Asset 不存在, THE System SHALL 显示错误提示
5. WHEN 双方资产均存在, THE System SHALL 调用 FinanceViewModel.transferAsset() 方法
6. WHEN 调用 transferAsset(), THE System SHALL 传递 Source_Asset 对象
7. WHEN 调用 transferAsset(), THE System SHALL 传递 Target_Asset 对象
8. WHEN 调用 transferAsset(), THE System SHALL 传递转账金额 amount
9. WHEN 调用 transferAsset(), THE System SHALL 传递优惠金额 discount
10. WHEN 调用 transferAsset(), THE System SHALL 传递备注信息 note

### Requirement 7: 转账摘要显示

**User Story:** 作为用户，我希望在确认转账前能够看到清晰的转账摘要信息，以便我能够核对转账详情。

#### Acceptance Criteria

1. WHEN 显示转账摘要, THE UI SHALL 显示"转账"标签
2. WHEN 显示转账摘要, THE UI SHALL 显示转账金额
3. WHEN 显示转账摘要, THE UI SHALL 显示"从: [Source_Asset]"信息
4. WHEN 显示转账摘要, THE UI SHALL 显示"到: [Target_Asset]"信息
5. WHEN Transfer_Discount 大于0, THE UI SHALL 显示"优惠: ¥[discount]"信息
6. WHEN Transfer_Discount 大于0, THE UI SHALL 显示"实际扣款: ¥[amount - discount]"信息
7. WHEN 存在备注信息, THE UI SHALL 显示"备注: [note]"信息
8. WHEN Transfer_Discount 等于0, THE UI SHALL 不显示优惠相关信息

### Requirement 8: 转账与普通交易的区分

**User Story:** 作为用户，我希望系统能够明确区分转账和普通收支交易，以便转账不会影响我的预算统计和分类统计。

#### Acceptance Criteria

1. WHEN 交易类型为转账, THE System SHALL 不将转账计入预算统计
2. WHEN 交易类型为转账, THE System SHALL 固定分类为"资产互转"
3. WHEN 交易类型为转账, THE System SHALL 不应用用户自定义的 AI_Category_Rules
4. WHEN 交易类型为转账, THE System SHALL 在交易记录中标记 type=2
5. WHEN 交易类型为转账, THE System SHALL 在备注中记录转出和转入资产名称

### Requirement 9: 提示词模板更新

**User Story:** 作为开发者，我希望 AI 提示词模板包含转账识别规则和示例，以便 AI 能够准确理解和识别转账操作。

#### Acceptance Criteria

1. THE PromptTemplate SHALL 在 JSON 输出规则中说明 type=2 表示转账
2. THE PromptTemplate SHALL 在收支类型规则中列出转账识别关键词
3. THE PromptTemplate SHALL 包含转账识别规则说明
4. THE PromptTemplate SHALL 说明转账必须同时识别 Source_Asset 和 Target_Asset
5. THE PromptTemplate SHALL 提供常见转账表达示例
6. THE PromptTemplate SHALL 说明转账固定分类为"资产互转"
7. THE PromptTemplate SHALL 说明转账支持优惠金额字段
8. THE PromptTemplate SHALL 在 JSON 示例中包含至少一个转账示例

### Requirement 10: 转账优惠逻辑

**User Story:** 作为用户，我希望系统能够正确处理转账优惠，以便转出账户扣除优惠后的金额，而转入账户收到全额。

#### Acceptance Criteria

1. WHEN 存在 Transfer_Discount, THE System SHALL 计算实际扣款金额为 amount - discount
2. WHEN 执行转账, THE System SHALL 从 Source_Asset 扣除实际扣款金额
3. WHEN 执行转账, THE System SHALL 向 Target_Asset 增加全额 amount
4. WHEN 生成转账记录, THE System SHALL 在 Transaction.amount 字段记录实际扣款金额
5. WHEN 存在优惠, THE System SHALL 在备注中记录原始金额和优惠金额
6. WHEN Transfer_Discount 为0, THE System SHALL 按全额执行转账

### Requirement 11: 数据模型扩展

**User Story:** 作为开发者，我希望 TransactionDraft 数据模型能够支持转账所需的字段，以便存储和传递转账相关信息。

#### Acceptance Criteria

1. THE TransactionDraft SHALL 包含 targetAssetId 字段
2. THE TransactionDraft.targetAssetId SHALL 为 int 类型
3. THE TransactionDraft SHALL 包含 discount 字段
4. THE TransactionDraft.discount SHALL 为 double 类型
5. WHEN targetAssetId 未设置, THE TransactionDraft.targetAssetId SHALL 默认为0
6. WHEN discount 未设置, THE TransactionDraft.discount SHALL 默认为0

### Requirement 12: WebDAV 自动备份触发

**User Story:** 作为用户，我希望转账操作完成后能够自动触发云端备份，以便我的数据得到及时同步。

#### Acceptance Criteria

1. WHEN 转账操作成功完成, THE System SHALL 调用 BackupManager.triggerAutoUploadIfEnabled()
2. WHEN 自动备份开关启用, THE System SHALL 在后台执行 WebDAV 上传
3. WHEN 自动备份成功, THE System SHALL 更新备份时间戳
4. WHEN 自动备份失败, THE System SHALL 静默处理不打扰用户
