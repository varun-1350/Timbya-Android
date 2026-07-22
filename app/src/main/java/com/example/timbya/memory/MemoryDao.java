package com.example.timbya.memory;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MemoryEntry entry);

    @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC")
    List<MemoryEntry> getAll();

    /* @Query("SELECT * FROM memory_entries ORDER BY updatedAt DESC LIMIT :limit")
    List<MemoryEntry> getRecent(int limit); */

    @Query("DELETE FROM memory_entries WHERE category = :category AND keyName = :keyName")
    void delete(String category, String keyName);

    @Query("DELETE FROM memory_entries WHERE keyName = :keyName")
    void deleteByKeyName(String keyName);

    @Query("DELETE FROM memory_entries")
    void clearAll();

    /* @Query("DELETE FROM memory_entries")
    void clearAll(); */
}