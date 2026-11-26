package com.example.android.notepad.todo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 待办用的独立数据库。
 * 单独一个 .db 文件，不动老师的 NotePadProvider，避免影响原有笔记功能。
 */
public class TodoDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "notepad_todo.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "todos";

    public TodoDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, "
                + "done INTEGER NOT NULL DEFAULT 0, "
                + "due_time INTEGER NOT NULL DEFAULT 0, "
                + "priority INTEGER NOT NULL DEFAULT 2, "
                + "created INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 目前只有 v1，无升级逻辑
    }

    /** 新增一条待办 */
    public long insert(String title, long dueTime, int priority) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("done", 0);
        cv.put("due_time", dueTime);
        cv.put("priority", priority);
        cv.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert(TABLE, null, cv);
    }

    /** 勾选 / 取消勾选 */
    public void setDone(long id, boolean done) {
        ContentValues cv = new ContentValues();
        cv.put("done", done ? 1 : 0);
        getWritableDatabase().update(TABLE, cv, "_id = ?", new String[]{String.valueOf(id)});
    }

    /** 修改一条待办的标题 / 截止时间 / 优先级（编辑用） */
    public void update(long id, String title, long dueTime, int priority) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("due_time", dueTime);
        cv.put("priority", priority);
        getWritableDatabase().update(TABLE, cv, "_id = ?", new String[]{String.valueOf(id)});
    }

    /** 删除 */
    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "_id = ?", new String[]{String.valueOf(id)});
    }

    /**
     * 查全部：顺序是
     * 1) 未完成且没过期（按优先级：紧急→一般→不急，再按截止时间）
     * 2) 未完成但已过期（同样按优先级）
     * 3) 已完成（沉到最下面）
     * CASE WHEN 那段就是给"已过期"单独排一段。
     */
    public List<Todo> getAll() {
        List<Todo> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        Cursor cursor = getReadableDatabase().query(
                TABLE,
                new String[]{"_id", "title", "done", "due_time", "priority", "created"},
                null, null, null, null,
                "done ASC, "
                        + "CASE WHEN done = 0 AND due_time > 0 AND due_time < " + now
                        + " THEN 1 ELSE 0 END ASC, "
                        + "priority ASC, due_time ASC, created DESC"
        );
        while (cursor != null && cursor.moveToNext()) {
            list.add(new Todo(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getInt(2) == 1,
                    cursor.getLong(3),
                    cursor.getInt(4),
                    cursor.getLong(5)
            ));
        }
        if (cursor != null) cursor.close();
        return list;
    }

    /** 查某一天（0 点 ~ 次日 23:59:59）的待办，日历页用 */
    public List<Todo> getByDay(long dayStart, long dayEnd) {
        List<Todo> list = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query(
                TABLE,
                new String[]{"_id", "title", "done", "due_time", "priority", "created"},
                "due_time >= ? AND due_time <= ?",
                new String[]{String.valueOf(dayStart), String.valueOf(dayEnd)},
                null, null,
                "done ASC, priority ASC, created DESC"
        );
        while (cursor != null && cursor.moveToNext()) {
            list.add(new Todo(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getInt(2) == 1,
                    cursor.getLong(3),
                    cursor.getInt(4),
                    cursor.getLong(5)
            ));
        }
        if (cursor != null) cursor.close();
        return list;
    }
}
