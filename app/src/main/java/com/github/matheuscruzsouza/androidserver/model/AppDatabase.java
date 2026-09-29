package com.github.matheuscruzsouza.androidserver.model;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import java.util.concurrent.Executors;

@Database(entities = {User.class, UserFile.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract UserDao userDao();
    public abstract UserFileDao userFileDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "nanospring_db")
                            .fallbackToDestructiveMigration()
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(SupportSQLiteDatabase db) {
                                    super.onCreate(db);
                                    seedDefaultUser();
                                }

                                @Override
                                public void onOpen(SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                    seedDefaultUser();
                                }

                                private void seedDefaultUser() {
                                    Executors.newSingleThreadExecutor().execute(() -> {
                                        if (INSTANCE != null) {
                                            try {
                                                if (INSTANCE.userDao().getAllUsers().isEmpty()) {
                                                    INSTANCE.userDao().insert(new User("matheuscruzsouza", "Matheus Cruz de Souza"));
                                                }
                                            } catch (Exception ignored) {}
                                        }
                                    });
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
