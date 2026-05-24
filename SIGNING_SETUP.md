# Android 应用签名配置完整指南

## 📋 目录
1. [创建签名密钥](#1-创建签名密钥)
2. [配置密钥信息](#2-配置密钥信息)
3. [配置 Gradle 构建](#3-配置-gradle-构建)
4. [获取证书指纹](#4-获取证书指纹)
5. [构建发布版本](#5-构建发布版本)

---

## 1. 创建签名密钥

### 方法 A：使用自动化脚本（推荐）

双击运行 `create-keystore.bat` 文件，按照提示操作。

### 方法 B：手动执行命令

打开命令行，执行：

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp -keyalg RSA -keysize 2048 -validity 10000
```

### 需要输入的信息：

| 项目 | 说明 | 示例 |
|------|------|------|
| 密钥库口令 | 至少 6 位的强密码 | `MyApp@2024!` |
| 密钥口令 | 可与密钥库口令相同 | 直接按回车 |
| 姓名 (CN) | 开发者或公司名称 | `Budget App Team` |
| 组织单位 (OU) | 部门名称 | `Development` |
| 组织 (O) | 公司名称 | `My Company` |
| 城市 (L) | 城市名称 | `Beijing` |
| 省份 (ST) | 省份名称 | `Beijing` |
| 国家代码 (C) | 两位国家代码 | `CN` |

---

## 2. 配置密钥信息

### 步骤 2.1：创建配置文件

复制 `keystore.properties.template` 并重命名为 `keystore.properties`：

```bash
copy keystore.properties.template keystore.properties
```

### 步骤 2.2：编辑配置文件

打开 `keystore.properties`，填写实际信息：

```properties
storeFile=budgetapp-release.keystore
storePassword=你的密钥库密码
keyAlias=budgetapp
keyPassword=你的密钥密码
```

**⚠️ 重要：** `keystore.properties` 文件已在 `.gitignore` 中，不会被提交到 Git。

---

## 3. 配置 Gradle 构建

### 步骤 3.1：修改 `app/build.gradle.kts`

在文件开头添加读取配置的代码：

```kotlin
import java.util.Properties
import java.io.FileInputStream

// 读取签名配置
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
```

### 步骤 3.2：配置签名

在 `android` 块中添加：

```kotlin
android {
    // ... 其他配置

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

---

## 4. 获取证书指纹

### 查看证书信息

执行以下命令：

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp
```

### 输出示例：

```
证书指纹:
         SHA1: A1:B2:C3:D4:E5:F6:...
         SHA256: 1A:2B:3C:4D:5E:6F:...
         MD5: 12:34:56:78:90:AB:...
```

### 用途：

- **SHA1 指纹**：用于 Google Maps API、Firebase 等
- **SHA256 指纹**：用于 Google Play App Signing
- **MD5 指纹**：用于某些旧版第三方 SDK

---

## 5. 构建发布版本

### 使用 Android Studio

1. 菜单：`Build` → `Generate Signed Bundle / APK`
2. 选择 `APK` 或 `Android App Bundle`
3. 选择密钥库文件：`budgetapp-release.keystore`
4. 输入密码和别名
5. 选择 `release` 构建类型
6. 点击 `Finish`

### 使用命令行

```bash
# 构建 Release APK
gradlew assembleRelease

# 构建 Android App Bundle (推荐用于 Google Play)
gradlew bundleRelease
```

输出位置：
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

---

## 📝 密钥信息记录表

请将以下信息记录在安全的地方（如密码管理器）：

```
应用名称: Budget App
包名: com.example.budgetapp

密钥库文件: d:\budgetapp\budgetapp-release.keystore
密钥库密码: ___________________
密钥别名: budgetapp
密钥密码: ___________________

创建日期: ___________________
有效期至: ___________________ (约 27 年)

SHA1 指纹: ___________________
SHA256 指纹: ___________________
MD5 指纹: ___________________
```

---

## 🔒 安全最佳实践

### ✅ 应该做的：

1. **备份密钥库文件**
   - 备份到多个安全位置（加密云存储、外部硬盘等）
   - 定期验证备份的完整性

2. **使用强密码**
   - 至少 12 位，包含大小写字母、数字和特殊字符
   - 使用密码管理器存储

3. **限制访问权限**
   - 只有必要的人员才能访问密钥
   - 使用加密存储

4. **版本控制**
   - 确保 `.gitignore` 包含密钥文件
   - 定期检查是否误提交

### ❌ 不应该做的：

1. ❌ 将密钥库文件提交到 Git
2. ❌ 在代码中硬编码密码
3. ❌ 通过不安全的渠道分享密钥
4. ❌ 使用弱密码或默认密码
5. ❌ 忘记备份密钥库文件

---

## 🆘 常见问题

### Q: 如果丢失了密钥库文件怎么办？

A: **无法恢复**。你将无法更新已发布的应用，只能：
- 使用新密钥发布新应用（不同的包名）
- 联系应用商店支持（可能需要验证身份）

### Q: 可以更改密钥库密码吗？

A: 可以，使用以下命令：

```bash
keytool -storepasswd -keystore budgetapp-release.keystore
```

### Q: 如何验证密钥库文件是否正确？

A: 使用 `-list` 命令查看：

```bash
keytool -list -v -keystore budgetapp-release.keystore
```

### Q: Debug 和 Release 使用不同的密钥吗？

A: 是的：
- **Debug**: 使用 Android 默认的 debug.keystore（自动生成）
- **Release**: 使用你创建的 budgetapp-release.keystore

---

## 📞 需要帮助？

如果遇到问题，请检查：
1. keytool 命令是否正确执行
2. 密钥库文件是否存在
3. `keystore.properties` 配置是否正确
4. Gradle 配置是否正确

---

**最后更新**: 2026-05-20
