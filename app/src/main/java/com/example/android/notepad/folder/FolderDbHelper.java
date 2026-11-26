package com.example.android.notepad.folder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件夹用的独立数据库（不动老师的 NotePadProvider）。
 * 树形结构：parent_id = 0 是顶层；id = 1 固定是默认文件夹「未分类」。
 */
public class FolderDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "notepad_folder.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "folders";

    /** 默认文件夹「未分类」的 id，与 NotePad.Notes.DEFAULT_FOLDER_ID 保持一致 */
    public static final long DEFAULT_ID = 1;
    public static final String DEFAULT_NAME = "未分类";

    /** 新建笔记时通过 Intent 传的文件夹 id（编辑页读它决定初始归属） */
    public static final String EXTRA_FOLDER_ID = "folder_id";

    public FolderDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, "
                + "parent_id INTEGER NOT NULL DEFAULT 0, "
                + "created INTEGER NOT NULL)");
        // 造好默认文件夹：所有没分类的笔记都归这里
        ContentValues cv = new ContentValues();
        cv.put("_id", DEFAULT_ID);
        cv.put("name", DEFAULT_NAME);
        cv.put("parent_id", 0);
        cv.put("created", System.currentTimeMillis());
        db.insert(TABLE, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 只有 v1，无升级逻辑
    }

    /** 某层的所有子文件夹（parentId = 0 取顶层） */
    public List<Folder> listByParent(long parentId) {
        List<Folder> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE,
                new String[]{"_id", "name", "parent_id"},
                "parent_id = ?", new String[]{String.valueOf(parentId)},
                null, null, "_id ASC");
        while (c != null && c.moveToNext()) {
            list.add(new Folder(c.getLong(0), c.getString(1), c.getLong(2)));
        }
        if (c != null) c.close();
        return list;
    }

    public Folder find(long id) {
        Cursor c = getReadableDatabase().query(TABLE,
                new String[]{"_id", "name", "parent_id"},
                "_id = ?", new String[]{String.valueOf(id)},
                null, null, null);
        Folder f = null;
        if (c != null && c.moveToNext()) {
            f = new Folder(c.getLong(0), c.getString(1), c.getLong(2));
        }
        if (c != null) c.close();
        return f;
    }

    /** 新建文件夹，返回新 id */
    public long create(String name, long parentId) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("parent_id", parentId);
        cv.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert(TABLE, null, cv);
    }

    public void rename(long id, String name) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        getWritableDatabase().update(TABLE, cv, "_id = ?", new String[]{String.valueOf(id)});
    }

    /** 只删文件夹本身，不删笔记（笔记由调用方挪到「未分类」） */
    public void deleteSelf(long id) {
        getWritableDatabase().delete(TABLE, "_id = ?", new String[]{String.valueOf(id)});
    }

    /** 全部文件夹（用于把 folder_id 翻成名字） */
    public List<Folder> listAll() {
        List<Folder> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query(TABLE,
                new String[]{"_id", "name", "parent_id"},
                null, null, null, null, "_id ASC");
        while (c != null && c.moveToNext()) {
            list.add(new Folder(c.getLong(0), c.getString(1), c.getLong(2)));
        }
        if (c != null) c.close();
        return list;
    }

    /** 某个文件夹自己 + 它下面所有子孙（筛选笔记用：点进"学习"能看到子文件夹里的笔记） */
    public List<Long> selfAndDescendants(long id) {
        List<Long> out = new ArrayList<>();
        collectFilter(id, out);
        return out;
    }

    private void collectFilter(long id, List<Long> out) {
        out.add(id);
        for (Folder child : listByParent(id)) {
            collectFilter(child.id, out);
        }
    }

    /** 删除时要处理的范围：子孙文件夹（不含默认文件夹，默认永远保留） */
    public List<Long> descendantsForDelete(long id) {
        List<Long> out = new ArrayList<>();
        out.add(id);
        collectDelete(id, out);
        return out;
    }

    private void collectDelete(long id, List<Long> out) {
        for (Folder child : listByParent(id)) {
            if (child.id == DEFAULT_ID) continue;
            out.add(child.id);
            collectDelete(child.id, out);
        }
    }

    /** 面包屑：从顶层到该文件夹的名字路径，如 ["学习", "Android"] */
    public List<String> pathNames(long id) {
        List<String> names = new ArrayList<>();
        long cur = id;
        int guard = 0;
        while (cur != 0 && guard++ < 20) {
            Folder f = find(cur);
            if (f == null) break;
            names.add(0, f.name);
            cur = f.parentId;
        }
        return names;
    }
}
