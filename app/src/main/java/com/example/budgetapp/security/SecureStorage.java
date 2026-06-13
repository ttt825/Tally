package com.example.budgetapp.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 安全存储类
 * 使用 SHA-256 哈希存储密码
 */
public class SecureStorage {

    private static final String PREFS_NAME = "secure_prefs";
    private static final String PREF_SALT = "password_salt";
    private static final String PREF_HASH = "password_hash";

    private static final int SALT_LENGTH = 16;

    private final SharedPreferences prefs;

    public SecureStorage(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 保存密码（SHA-256 哈希 + 随机盐）
     */
    public boolean savePassword(String password) {
        try {
            if (password == null || password.isEmpty()) {
                clearPassword();
                return true;
            }

            byte[] salt = new byte[SALT_LENGTH];
            new SecureRandom().nextBytes(salt);

            byte[] hash = hash(password, salt);

            prefs.edit()
                    .putString(PREF_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(PREF_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .apply();

            return true;
        } catch (Exception e) {
            Log.e("Tally", "savePassword failed", e);
            return false;
        }
    }

    /**
     * 验证密码是否正确
     */
    public boolean verifyPassword(String inputPassword) {
        try {
            String saltStr = prefs.getString(PREF_SALT, null);
            String hashStr = prefs.getString(PREF_HASH, null);

            if (saltStr == null || hashStr == null) {
                return false;
            }

            byte[] salt = Base64.decode(saltStr, Base64.NO_WRAP);
            byte[] expectedHash = Base64.decode(hashStr, Base64.NO_WRAP);
            byte[] inputHash = hash(inputPassword, salt);

            return MessageDigest.isEqual(expectedHash, inputHash);
        } catch (Exception e) {
            Log.e("Tally", "verifyPassword failed", e);
            return false;
        }
    }

    /**
     * 检查是否已设置密码
     */
    public boolean hasPassword() {
        return prefs.contains(PREF_HASH);
    }

    /**
     * 清除保存的密码
     */
    public void clearPassword() {
        prefs.edit()
                .remove(PREF_SALT)
                .remove(PREF_HASH)
                .apply();
    }

    private byte[] hash(String password, byte[] salt) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        return digest.digest(password.getBytes(StandardCharsets.UTF_8));
    }

    // ================= 兼容旧接口 =================

}