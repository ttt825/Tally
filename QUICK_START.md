# 🚀 快速开始：配置应用签名

## 📌 简化版步骤（5 分钟完成）

### 第 1 步：创建密钥库 ⏱️ 2 分钟

双击运行 `create-keystore.bat`，按提示输入：

1. **密钥库口令**：设置一个强密码（例如：`BudgetApp2024!`）
2. **密钥口令**：直接按回车（使用相同密码）
3. **个人信息**：可以简单填写或使用默认值

```
姓名: Budget App
组织单位: Dev
组织: MyCompany
城市: Beijing
省份: Beijing
国家: CN
```

✅ 完成后会生成：`budgetapp-release.keystore`

---

### 第 2 步：配置密钥信息 ⏱️ 1 分钟

1. 复制 `keystore.properties.template` 为 `keystore.properties`
2. 用记事本打开 `keystore.properties`
3. 填写你刚才设置的密码：

```properties
storeFile=budgetapp-release.keystore
storePassword=BudgetApp2024!
keyAlias=budgetapp
keyPassword=BudgetApp2024!
```

保存文件。

---

### 第 3 步：配置 Gradle ⏱️ 2 分钟

打开 `app/build.gradle.kts`，在文件开头（`plugins` 之前）添加：

```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
```

然后在 `android` 块中，`buildTypes` 之前添加：

```kotlin
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
```

修改 `buildTypes` 中的 `release` 块：

```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")  // 添加这一行
        isMinifyEnabled = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

保存文件。

---

### 第 4 步：验证配置 ⏱️ 30 秒

在 Android Studio 中：

1. 点击 `Build` → `Clean Project`
2. 点击 `Build` → `Rebuild Project`
3. 如果没有错误，配置成功！✅

---

## 🎯 完成！现在你可以：

### 构建发布版本

**方法 1：使用 Android Studio**
- `Build` → `Generate Signed Bundle / APK`
- 选择 `APK` 或 `Android App Bundle`
- 选择密钥库文件并输入密码
- 点击 `Finish`

**方法 2：使用命令行**
```bash
# 构建 APK
gradlew assembleRelease

# 构建 AAB（推荐用于 Google Play）
gradlew bundleRelease
```

### 查看证书指纹

运行以下命令（用于配置 Google 服务等）：

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp
```

记录输出的 **SHA1**、**SHA256** 和 **MD5** 指纹。

---

## ⚠️ 重要提醒

1. **备份密钥库文件**
   - 复制 `budgetapp-release.keystore` 到安全位置
   - 如果丢失将无法更新应用！

2. **保管密码**
   - 将密码记录在密码管理器中
   - 不要分享给他人

3. **不要提交到 Git**
   - `*.keystore` 和 `keystore.properties` 已在 `.gitignore` 中
   - 提交前检查一下

---

## 📚 需要更多帮助？

查看完整文档：`SIGNING_SETUP.md`

---

**配置完成后，你的应用就可以正式发布了！** 🎉
