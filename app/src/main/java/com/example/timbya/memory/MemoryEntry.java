package com.example.timbya.memory;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "memory_entries",
        indices = { @Index(value = {"category", "keyName"}, unique = true) }
)
public class MemoryEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull public String category;   // NAME, INTEREST, DISLIKE, PROJECT, GOAL, HABIT, FACT
    @NonNull public String keyName;    // dedup key within a category
    @NonNull public String value;
    public long updatedAt;

    public MemoryEntry(@NonNull String category, @NonNull String keyName,
                       @NonNull String value, long updatedAt) {
        this.category = category;
        this.keyName = keyName;
        this.value = value;
        this.updatedAt = updatedAt;
    }
}