package com.example.android.notepad.todo;

/** 一条待办的数据结构 */
public class Todo {
    public long id;
    public String title;
    public boolean done;
    public long dueTime;   // 截止时间（毫秒），0 表示没设置
    public int priority;   // 1=紧急 2=一般 3=不急
    public long created;

    public Todo(long id, String title, boolean done, long dueTime, int priority, long created) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.dueTime = dueTime;
        this.priority = priority;
        this.created = created;
    }
}
