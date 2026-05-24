# 📦 Android 应用签名配置资源

欢迎！这里包含了为 Budget App 配置正式签名所需的所有资源。

---

## 🚀 快速开始（推荐）

**如果你是第一次配置，从这里开始：**

1. 📖 阅读 **[QUICK_START.md](QUICK_START.md)** - 5 分钟快速配置指南
2. ✅ 使用 **[SIGNING_CHECKLIST.md](SIGNING_CHECKLIST.md)** - 确保不遗漏任何步骤

---

## 📚 完整文档

### 核心文档

| 文档 | 说明 | 适合人群 |
|------|------|----------|
| **[QUICK_START.md](QUICK_START.md)** | 5 分钟快速配置 | 所有人 ⭐ |
| **[SIGNING_SETUP.md](SIGNING_SETUP.md)** | 完整详细的配置指南 | 需要深入了解 |
| **[SIGNING_CHECKLIST.md](SIGNING_CHECKLIST.md)** | 配置检查清单 | 确保完整性 |
| **[create-keystore.md](create-keystore.md)** | 创建密钥库详细说明 | 手动配置 |

### 工作日志

| 文档 | 说明 |
|------|------|
| **[.trellis/workspace/app-signing-setup-2026-05-20.md](.trellis/workspace/app-signing-setup-2026-05-20.md)** | 配置过程记录和技术细节 |

---

## 🛠️ 工具和脚本

### 自动化脚本

| 文件 | 说明 | 使用方法 |
|------|------|----------|
| **[create-keystore.bat](create-keystore.bat)** | 自动创建密钥库 | 双击运行 |

### 配置模板

| 文件 | 说明 | 使用方法 |
|------|------|----------|
| **[keystore.properties.template](keystore.properties.template)** | 密钥配置模板 | 复制为 `keystore.properties` 并填写 |
| **[build.gradle.kts.signing-example](build.gradle.kts.signing-example)** | Gradle 配置示例 | 参考修改 `app/build.gradle.kts` |

---

## 📋 配置流程概览

```
1. 创建密钥库
   ↓
   运行 create-keystore.bat
   或手动执行 keytool 命令
   ↓
   生成 budgetapp-release.keystore

2. 配置密钥信息
   ↓
   复制 keystore.properties.template
   重命名为 keystore.properties
   ↓
   填写密码和配置

3. 配置 Gradle
   ↓
   修改 app/build.gradle.kts
   添加签名配置
   ↓
   重新构建项目

4. 获取证书指纹
   ↓
   运行 keytool -list 命令
   ↓
   记录 MD5, SHA1, SHA256

5. 构建发布版本
   ↓
   Build → Generate Signed Bundle/APK
   或 gradlew assembleRelease
   ↓
   生成已签名的 APK/AAB

6. 备份和发布
   ↓
   备份密钥库文件
   ↓
   发布到应用商店
```

---

## ⚡ 快速命令参考

### 创建密钥库
```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp -keyalg RSA -keysize 2048 -validity 10000
```

### 查看证书信息
```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp
```

### 构建 Release 版本
```bash
# APK
gradlew assembleRelease

# Android App Bundle (推荐)
gradlew bundleRelease
```

---

## 🔒 安全提醒

### ⚠️ 重要警告

1. **密钥库文件和密码一旦丢失，无法恢复！**
   - 将无法更新已发布的应用
   - 必须创建新应用（新包名）

2. **不要将密钥文件提交到 Git**
   - 已在 `.gitignore` 中配置
   - 提交前务必检查

3. **使用强密码**
   - 至少 12 位
   - 包含大小写字母、数字、特殊字符

### ✅ 安全最佳实践

- ✅ 备份密钥库到多个安全位置
- ✅ 使用密码管理器存储密码
- ✅ 限制密钥访问权限
- ✅ 定期验证备份完整性
- ✅ 记录证书指纹信息

---

## 📊 配置状态

### 当前状态
- ⏳ 待创建密钥库
- ⏳ 待配置 Gradle
- ⏳ 待获取证书指纹

### 完成后
- ✅ 密钥库已创建
- ✅ Gradle 已配置
- ✅ 证书指纹已记录
- ✅ Release 版本可构建

---

## 🎯 目标

配置完成后，你将能够：

1. ✅ 构建正式签名的 Release APK
2. ✅ 发布到 Google Play 或其他应用商店
3. ✅ 配置需要证书指纹的第三方服务
4. ✅ 更新已发布的应用（使用相同签名）

---

## 📞 需要帮助？

### 常见问题

查看 **[SIGNING_CHECKLIST.md](SIGNING_CHECKLIST.md)** 底部的"常见问题快速解决"部分。

### 详细文档

查看 **[SIGNING_SETUP.md](SIGNING_SETUP.md)** 的"常见问题"章节。

---

## 📝 相关文件

### 生成的文件（不要提交到 Git）
- `budgetapp-release.keystore` - 密钥库文件
- `keystore.properties` - 密钥配置文件

### 配置文件（需要修改）
- `app/build.gradle.kts` - Gradle 构建配置

### 输出文件
- `app/build/outputs/apk/release/app-release.apk` - 签名的 APK
- `app/build/outputs/bundle/release/app-release.aab` - 签名的 AAB

---

## 🔄 版本历史

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-05-20 | 1.0 | 初始版本，创建所有配置文档和脚本 |

---

## 📖 参考资料

- [Android 官方文档 - 为应用签名](https://developer.android.com/studio/publish/app-signing)
- [Google Play 应用签名](https://support.google.com/googleplay/android-developer/answer/9842756)
- [密钥库最佳实践](https://developer.android.com/studio/publish/app-signing#secure-key)

---

**准备好了吗？从 [QUICK_START.md](QUICK_START.md) 开始吧！** 🚀
