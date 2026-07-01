package com.example.timbya.memory;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MemoryEntry.class}, version = 1, exportSchema = false)
public abstract class MemoryDatabase extends RoomDatabase {

    private static volatile MemoryDatabase instance;

    public abstract MemoryDao memoryDao();

    public static MemoryDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (MemoryDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    MemoryDatabase.class,
                                    "timbya_memory.db")
                            .build();
                }
            }
        }
        return instance;
    }
}