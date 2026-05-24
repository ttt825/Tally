# Android 应用签名配置

**日期**: 2026-05-20  
**状态**: 📝 待执行

## 背景

用户询问应用的公钥和证书 MD5 指纹，经检查发现项目尚未配置正式的签名密钥。目前只有 debug 版本的签名。

## 当前状态

- ❌ 未配置 Release 签名
- ❌ 未创建密钥库文件
- ❌ `build.gradle.kts` 中无签名配置
- ✅ 有 debug APK（使用默认 debug 签名）

## 已创建的文件

### 1. 文档文件

| 文件名 | 说明 |
|--------|------|
| `QUICK_START.md` | 快速开始指南（5 分钟配置） |
| `SIGNING_SETUP.md` | 完整的签名配置文档 |
| `create-keystore.md` | 创建密钥库的详细说明 |

### 2. 脚本文件

| 文件名 | 说明 |
|--------|------|
| `create-keystore.bat` | 自动化创建密钥库的批处理脚本 |

### 3. 配置模板

| 文件名 | 说明 |
|--------|------|
| `keystore.properties.template` | 密钥配置文件模板 |
| `build.gradle.kts.signing-example` | 配置了签名的 Gradle 示例 |

### 4. 安全配置

- ✅ 更新了 `.gitignore`，添加了密钥文件排除规则：
  ```
  *.keystore
  *.jks
  keystore.properties
  ```

## 配置步骤概览

### 步骤 1: 创建密钥库

运行 `create-keystore.bat` 或手动执行 keytool 命令：

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp -keyalg RSA -keysize 2048 -validity 10000
```

**需要输入的信息**:
- 密钥库口令（强密码）
- 密钥口令（可与密钥库口令相同）
- 个人/组织信息（CN, OU, O, L, ST, C）

**输出**: `budgetapp-release.keystore` 文件

### 步骤 2: 配置密钥信息

1. 复制 `keystore.properties.template` 为 `keystore.properties`
2. 填写实际的密码和配置信息

### 步骤 3: 配置 Gradle

修改 `app/build.gradle.kts`，添加：

1. **导入和读取配置**（文件开头）:
```kotlin
import java.util.Properties
import java.io.FileInputStream

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
```

2. **签名配置**（android 块中）:
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

3. **应用签名**（buildTypes 中）:
```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
        // ... 其他配置
    }
}
```

### 步骤 4: 获取证书指纹

执行命令查看证书信息：

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore d:\budgetapp\budgetapp-release.keystore -alias budgetapp
```

**输出包含**:
- MD5 指纹
- SHA1 指纹
- SHA256 指纹

这些指纹用于配置：
- Google Maps API
- Firebase
- 第三方 SDK
- OAuth 认证

### 步骤 5: 构建发布版本

**Android Studio**:
- `Build` → `Generate Signed Bundle / APK`

**命令行**:
```bash
gradlew assembleRelease  # APK
gradlew bundleRelease    # AAB
```

## 密钥参数说明

| 参数 | 值 | 说明 |
|------|-----|------|
| 密钥库文件 | `budgetapp-release.keystore` | 存储密钥的文件 |
| 密钥别名 | `budgetapp` | 密钥的标识符 |
| 算法 | RSA | 加密算法 |
| 密钥长度 | 2048 位 | 密钥强度 |
| 有效期 | 10000 天 | 约 27 年 |

## 安全最佳实践

### ✅ 应该做的

1. **备份密钥库文件**
   - 复制到多个安全位置
   - 使用加密存储
   - 定期验证备份完整性

2. **使用强密码**
   - 至少 12 位
   - 包含大小写字母、数字、特殊字符
   - 使用密码管理器存储

3. **限制访问**
   - 只有必要人员可访问
   - 不通过不安全渠道分享

4. **版本控制**
   - 确保密钥文件在 `.gitignore` 中
   - 定期检查是否误提交

### ❌ 不应该做的

1. ❌ 将密钥库文件提交到 Git
2. ❌ 在代码中硬编码密码
3. ❌ 使用弱密码
4. ❌ 忘记备份密钥库
5. ❌ 分享密钥给不相关人员

## 重要警告

⚠️ **密钥库文件和密码一旦丢失，将无法恢复！**

后果：
- 无法更新已发布的应用
- 只能使用新密钥发布新应用（不同包名）
- 用户需要卸载旧版本重新安装

## 证书指纹用途

### SHA1 指纹
- Google Maps API
- Firebase Authentication
- Google Sign-In
- 大多数 Google 服务

### SHA256 指纹
- Google Play App Signing
- 新版 Google 服务
- 更高安全性要求的服务

### MD5 指纹
- 某些旧版第三方 SDK
- 向后兼容

## 构建类型对比

| 特性 | Debug | Release |
|------|-------|---------|
| 签名 | 自动（debug.keystore） | 手动配置 |
| 混淆 | 关闭 | 可选 |
| 调试 | 可调试 | 不可调试 |
| 性能 | 较慢 | 优化 |
| 用途 | 开发测试 | 正式发布 |

## 下一步行动

1. ✅ 阅读 `QUICK_START.md`
2. ⏳ 运行 `create-keystore.bat` 创建密钥库
3. ⏳ 配置 `keystore.properties`
4. ⏳ 修改 `app/build.gradle.kts`
5. ⏳ 构建并测试 Release 版本
6. ⏳ 记录证书指纹
7. ⏳ 备份密钥库文件

## 参考资料

- [Android 官方文档 - 为应用签名](https://developer.android.com/studio/publish/app-signing)
- [Google Play 应用签名](https://support.google.com/googleplay/android-developer/answer/9842756)
- [密钥库最佳实践](https://developer.android.com/studio/publish/app-signing#secure-key)

---

**创建者**: Kiro AI Assistant  
**最后更新**: 2026-05-20
