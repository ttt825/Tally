package com.example.budgetapp.database;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Transaction.class}, version = 20, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract TransactionDao transactionDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    public static volatile boolean downgradeDetected = false;

    // ... 保持之前的 MIGRATION
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("ALTER TABLE transactions ADD COLUMN remark TEXT DEFAULT ''"); }
    };
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { /* Asset table removed - no migration needed */ }
    };
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { /* Asset column removed - no migration needed */ }
    };
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("ALTER TABLE transactions ADD COLUMN currencySymbol TEXT DEFAULT '¥'"); }
    };
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { /* Asset column removed - no migration needed */ }
    };
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) { database.execSQL("ALTER TABLE transactions ADD COLUMN subCategory TEXT DEFAULT ''"); }
    };

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN photoPath TEXT DEFAULT ''");
        }
    };

    // 【新增 2】版本 9 -> 10 迁移：资产表已删除，迁移逻辑移除
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };

    // 2. 新增 10 -> 11 的迁移逻辑：资产表已删除，迁移逻辑移除
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };

    // 2. 【新增】11 -> 12 的迁移逻辑：资产表已删除，迁移逻辑移除
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };

    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };

    static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };

    static final Migration MIGRATION_14_15 = new Migration(14, 15) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };

    static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_category` ON `transactions` (`category`)");
        }
    };

    // 2. 【新增】16 -> 17 的迁移逻辑：资产表已删除，迁移逻辑移除
    static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };

    // ========== 新增：17 -> 18 的迁移逻辑 (资产表已删除) ==========
    static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };
    // ================================================================

    static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            /* Asset table removed - no migration needed */
        }
    };

    static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 为旧表增加新列，默认值给空字符串以避免旧数据为 null 导致的异常
            database.execSQL("ALTER TABLE transactions ADD COLUMN targetObject TEXT DEFAULT ''");
        }
    };

    private static final int CURRENT_VERSION = 20;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    checkDowngrade(context);

                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "budget_database")
                            .addMigrations(
                                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                                    MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                                    MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static void checkDowngrade(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("db_meta", Context.MODE_PRIVATE);
            int lastVersion = prefs.getInt("last_db_version", CURRENT_VERSION);
            if (lastVersion > CURRENT_VERSION) {
                downgradeDetected = true;
            }
            prefs.edit().putInt("last_db_version", CURRENT_VERSION).apply();
        } catch (Exception ignored) {}
    }
}