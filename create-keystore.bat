@echo off
chcp 65001 >nul
echo ========================================
echo   创建 Android 应用签名密钥
echo ========================================
echo.
echo 重要提示：
echo - 请妥善保管密钥库文件和密码
echo - 如果丢失将无法更新已发布的应用
echo.
echo 密钥库文件将创建在：
echo d:\budgetapp\budgetapp-release.keystore
echo.
echo 密钥别名：budgetapp
echo.
pause
echo.

"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore "d:\budgetapp\budgetapp-release.keystore" -alias budgetapp -keyalg RSA -keysize 2048 -validity 10000

echo.
echo ========================================
if exist "d:\budgetapp\budgetapp-release.keystore" (
    echo ✓ 密钥库创建成功！
    echo.
    echo 现在查看证书信息...
    echo.
    pause
    echo.
    "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore "d:\budgetapp\budgetapp-release.keystore" -alias budgetapp
) else (
    echo ✗ 密钥库创建失败
)
echo.
echo ========================================
pause
