# 创建 Android 应用签名密钥指南

## 重要提示
⚠️ **密钥库文件和密码必须妥善保管！**
- 如果丢失，将无法更新已发布的应用
- 建议备份到安全的地方（如加密的云存储）

## 步骤 1: 创建密钥库文件

在命令行中执行以下命令（请根据实际情况修改信息）：

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp -keyalg RSA -keysize 2048 -validity 10000
```

### 命令参数说明：
- `-keystore`: 密钥库文件路径
- `-alias`: 密钥别名（建议使用应用名称）
- `-keyalg RSA`: 使用 RSA 算法
- `-keysize 2048`: 密钥长度 2048 位
- `-validity 10000`: 有效期 10000 天（约 27 年）

### 执行后会提示输入以下信息：

1. **密钥库口令**（Keystore Password）
   - 请设置一个强密码（至少 6 位）
   - 例如：`MySecurePassword123!`

2. **密钥口令**（Key Password）
   - 可以与密钥库口令相同（直接按回车）
   - 或设置不同的密码

3. **个人信息**（可以根据实际情况填写）：
   - 姓名（CN）：`Your Name` 或 `Your Company`
   - 组织单位（OU）：`Development` 或 `Android Team`
   - 组织（O）：`Your Company Name`
   - 城市（L）：`Beijing`
   - 省份（ST）：`Beijing`
   - 国家代码（C）：`CN`

### 示例输入：
```
输入密钥库口令: MySecurePassword123!
再次输入新口令: MySecurePassword123!
您的名字与姓氏是什么?
  [Unknown]:  Budget App Developer
您的组织单位名称是什么?
  [Unknown]:  Development
您的组织名称是什么?
  [Unknown]:  My Company
您所在的城市或区域名称是什么?
  [Unknown]:  Beijing
您所在的省/市/自治区名称是什么?
  [Unknown]:  Beijing
该单位的双字母国家/地区代码是什么?
  [Unknown]:  CN
CN=Budget App Developer, OU=Development, O=My Company, L=Beijing, ST=Beijing, C=CN是否正确?
  [否]:  y

输入 <budgetapp> 的密钥口令
        (如果和密钥库口令相同, 按回车):  [直接按回车]
```

## 步骤 2: 记录密钥信息

创建完成后，请将以下信息记录在安全的地方：

```
密钥库文件路径: d:\budgetapp\budgetapp-release.keystore
密钥库口令: [你设置的密码]
密钥别名: budgetapp
密钥口令: [你设置的密码，如果与密钥库口令相同则相同]
```

## 步骤 3: 获取证书指纹

创建完成后，执行以下命令查看证书信息：

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp
```

输入密钥库口令后，会显示：
- **MD5 指纹**
- **SHA1 指纹**
- **SHA256 指纹**

这些指纹用于配置第三方服务（如 Google Maps API、Firebase 等）。

## 步骤 4: 配置 Gradle

创建密钥库后，我会帮你配置 `build.gradle.kts` 文件。

## 安全建议

1. **不要将密钥库文件提交到 Git**
   - 已在 `.gitignore` 中添加 `*.keystore`
   
2. **不要在代码中硬编码密码**
   - 使用环境变量或 `keystore.properties` 文件
   
3. **定期备份密钥库文件**
   - 备份到多个安全位置
   
4. **妥善保管密码**
   - 使用密码管理器存储

---

准备好后，请在命令行中执行步骤 1 的命令。
