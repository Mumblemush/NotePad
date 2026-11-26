package com.example.android.notepad.folder;

/** 一个文件夹结点（支持多级：parentId = 0 表示顶层） */
public class Folder {
    public long id;
    public String name;
    public long parentId;

    public Folder(long id, String name, long parentId) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
    }
}
