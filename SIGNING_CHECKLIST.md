# ✅ 应用签名配置检查清单

## 📋 配置前检查

- [ ] 已安装 Android Studio
- [ ] 已安装 Java JDK
- [ ] 项目可以正常编译 Debug 版本
- [ ] 已阅读 `QUICK_START.md`

---

## 🔑 步骤 1: 创建密钥库

- [ ] 运行 `create-keystore.bat`
- [ ] 设置了强密码（至少 12 位）
- [ ] 记录了密钥库密码
- [ ] 记录了密钥密码
- [ ] 填写了个人/组织信息
- [ ] 确认生成了 `budgetapp-release.keystore` 文件

**密钥信息记录**:
```
密钥库密码: ___________________
密钥密码: ___________________
创建日期: ___________________
```

---

## 📝 步骤 2: 配置密钥信息

- [ ] 复制 `keystore.properties.template` 为 `keystore.properties`
- [ ] 编辑 `keystore.properties` 文件
- [ ] 填写了正确的密钥库密码
- [ ] 填写了正确的密钥密码
- [ ] 保存了文件
- [ ] 确认 `keystore.properties` 不在 Git 中（已在 .gitignore）

---

## ⚙️ 步骤 3: 配置 Gradle

- [ ] 打开 `app/build.gradle.kts`
- [ ] 在文件开头添加了导入语句
- [ ] 添加了读取配置的代码
- [ ] 在 `android` 块中添加了 `signingConfigs`
- [ ] 修改了 `buildTypes.release` 添加签名配置
- [ ] 保存了文件
- [ ] 执行了 `Build` → `Clean Project`
- [ ] 执行了 `Build` → `Rebuild Project`
- [ ] 没有编译错误

---

## 🔍 步骤 4: 获取证书指纹

- [ ] 运行了 keytool 命令查看证书
- [ ] 记录了 MD5 指纹
- [ ] 记录了 SHA1 指纹
- [ ] 记录了 SHA256 指纹

**证书指纹记录**:
```
MD5:    ___________________
SHA1:   ___________________
SHA256: ___________________
```

---

## 🏗️ 步骤 5: 构建测试

- [ ] 成功构建了 Release APK
- [ ] APK 文件大小正常
- [ ] 在设备上安装测试成功
- [ ] 应用功能正常运行

**构建输出位置**:
```
APK: app/build/outputs/apk/release/app-release.apk
AAB: app/build/outputs/bundle/release/app-release.aab
```

---

## 💾 步骤 6: 备份

- [ ] 备份了 `budgetapp-release.keystore` 文件
- [ ] 备份到了安全位置 1: ___________________
- [ ] 备份到了安全位置 2: ___________________
- [ ] 备份了 `keystore.properties` 文件
- [ ] 在密码管理器中保存了密码
- [ ] 记录了证书指纹信息

---

## 🔒 安全检查

- [ ] 密钥库文件不在 Git 仓库中
- [ ] `keystore.properties` 不在 Git 仓库中
- [ ] `.gitignore` 包含了 `*.keystore` 和 `keystore.properties`
- [ ] 没有在代码中硬编码密码
- [ ] 密码足够强（12+ 位，包含大小写、数字、特殊字符）
- [ ] 只有必要人员知道密码

---

## 📱 发布前检查

- [ ] 应用版本号已更新（versionCode 和 versionName）
- [ ] 测试了所有主要功能
- [ ] 检查了应用权限
- [ ] 准备了应用商店描述和截图
- [ ] 阅读了目标应用商店的发布指南

---

## 🎯 完成标志

当所有项目都打勾后，你的应用就可以正式发布了！🎉

---

## 📞 遇到问题？

### 常见问题快速解决

**Q: keytool 命令找不到？**
- 检查 Java 是否正确安装
- 使用完整路径：`"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"`

**Q: Gradle 编译错误？**
- 检查 `keystore.properties` 文件是否存在
- 检查文件路径是否正确
- 检查语法是否正确

**Q: 无法安装 Release APK？**
- 卸载旧版本后重新安装
- 检查签名是否正确
- 查看 logcat 日志

**Q: 忘记密码怎么办？**
- 密钥库密码无法恢复
- 需要创建新的密钥库
- 已发布的应用无法更新（需要新包名）

---

**最后更新**: 2026-05-20
