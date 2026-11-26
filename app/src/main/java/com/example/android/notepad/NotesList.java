/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;
import com.example.android.notepad.ui.DialogHelper;
import com.example.android.notepad.ui.NavHelper;
import com.example.android.notepad.ui.ThemeHelper;

import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
// ===== 功能A新增 import：TextView 显示时间控件，SimpleDateFormat/Date 格式化时间 =====
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
// ===== 功能B新增 import：搜索框输入监听 =====
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

// ===== 功能E新增 import：文件夹分类（导航条 + 面包屑 + 筛选）=====
import android.graphics.drawable.GradientDrawable;
// 功能G：列表行按笔记背景色上色
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;

import com.example.android.notepad.folder.Folder;
import com.example.android.notepad.folder.FolderDbHelper;
import com.example.android.notepad.image.ImageStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    /**
     * The columns needed by the cursor adapter
     * 功能A：加入修改时间列 modified，列表才能拿到时间戳
     */
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID, // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2  ← 新增
            NotePad.Notes.COLUMN_NAME_FOLDER, // 3  功能E：所属文件夹
            NotePad.Notes.COLUMN_NAME_BG_COLOR, // 4  功能G：背景色
    };

    /** The index of the title column */
    private static final int COLUMN_INDEX_TITLE = 1;

    /** 功能A：修改时间列在 PROJECTION 中的下标 */
    private static final int COLUMN_INDEX_MODIFIED = 2;

    // ===== 功能H②：自绘顶栏菜单项 id（PopupMenu 用，不走系统菜单）=====
    private static final int MENU_ID_PASTE = 1;

    // ===== 功能E：文件夹状态 =====
    /** 当前所在文件夹；0 表示「全部笔记」（不筛选） */
    private long currentFolderId = 0;
    private FolderDbHelper folderDb;
    private LinearLayout folderBar;
    private TextView tvFolderPath;
    private EditText searchInput;
    /** folder_id → 文件夹名，列表里显示"· 学习"用 */
    private final Map<Long, String> folderNames = new HashMap<>();

    /**
     * onCreate is called when Android starts this Activity from scratch.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 功能B：换成自定义布局（顶部搜索框 + 列表），必须在 getListView() 之前调用
        // 底部导航：改成带底栏的布局（笔记 / 待办 / 日历）
        setContentView(R.layout.activity_notes_with_nav);
        // 绑定底部导航（笔记 / 待办 / 日历切换）
        NavHelper.bind(this);
        // 功能H③：按当前主题给顶栏和加号上色
        ThemeHelper.apply(this);

        // ===== 功能E：文件夹数据源（独立库，不碰老师的 Provider）=====
        folderDb = new FolderDbHelper(this);
        folderDb.getReadableDatabase();     // 首次运行会建好默认文件夹「未分类」(id=1)
        loadFolderNames();
        folderBar = (LinearLayout) findViewById(R.id.folder_bar);
        tvFolderPath = (TextView) findViewById(R.id.tv_folder_path);

        // 功能H①：右下角圆形悬浮按钮就是「新建笔记」，顶栏的铅笔图标已经删掉了
        findViewById(R.id.fab_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startInsert();
            }
        });

        // 功能H②：自绘顶栏的标题和右侧 ⋮
        ((TextView) findViewById(R.id.tv_top_title)).setText("笔记");
        View btnTopMore = findViewById(R.id.btn_top_more);
        btnTopMore.setVisibility(View.VISIBLE);
        btnTopMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTopMenu();
            }
        });

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        getListView().setOnCreateContextMenuListener(this);

        /* Performs a managed query. The Activity handles closing and requerying the cursor
         * when needed.
         *
         * Please see the introductory note about performing provider operations on the UI thread.
         */
        // 功能B：managedQuery 换成 getContentResolver().query，方便搜索时 changeCursor 替换游标
        Cursor cursor = getContentResolver().query(
                getIntent().getData(),            // Use the default content URI for the provider.
                PROJECTION,                       // Return the note ID and title for each note.
                null,                             // No where clause, return all records.
                null,                             // No where clause, therefore no where column values.
                NotePad.Notes.DEFAULT_SORT_ORDER  // Use the default sort order.
        );

        /*
         * The following two arrays create a "map" between columns in the cursor and view IDs
         * for items in the ListView. Each element in the dataColumns array represents
         * a column name; each element in the viewID array represents the ID of a View.
         * The SimpleCursorAdapter maps them in ascending order to determine where each column
         * value will appear in the ListView.
         */

        // 功能A：要显示的列 = 标题 + 修改时间
        // 功能G：再多一列背景色，铺到整行上
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
                NotePad.Notes.COLUMN_NAME_BG_COLOR
        };

        // 功能A：分别显示到标题控件 text1 和时间控件 text_time
        // 功能G：背景色作用在整行的根布局 row_bg
        int[] viewIDs = { android.R.id.text1, R.id.text_time, R.id.row_bg };

        // Creates the backing adapter for the ListView.
        SimpleCursorAdapter adapter
                = new SimpleCursorAdapter(
                this,                             // The Context for the ListView
                R.layout.noteslist_item,          // Points to the XML for a list item
                cursor,                           // The cursor to get items from
                dataColumns,
                viewIDs
        );

        // 功能A：时间列是 long 毫秒数，SimpleCursorAdapter 默认会显示成一串数字。
        // 用 ViewBinder 拦截它，格式化成 "2026-10-26 08:27:24" 可读时间。
        adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
                if (view.getId() == R.id.text_time) {
                    long timeMillis = cursor.getLong(columnIndex);
                    // 功能H⑦：原来那串 "2026-11-23 08:55:00" 太长，列表里看着累。
                    // 改成"今天 08:55 / 昨天 21:10 / 11-20 09:30 / 2025-12-01"这种能省就省的写法
                    String timeStr = shortTime(timeMillis);

                    // ===== 功能E：时间后面带上所属文件夹，例如 "2026-11-16 08:00:23 · 学习" =====
                    int folderIdx = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_FOLDER);
                    if (folderIdx >= 0) {
                        long folderId = cursor.getLong(folderIdx);
                        String folderName = folderNames.get(folderId);
                        if (folderId != NotePad.Notes.DEFAULT_FOLDER_ID && folderName != null) {
                            timeStr = timeStr + "  ·  " + folderName;
                        }
                    }

                    ((TextView) view).setText(timeStr);
                    return true; // 处理完了，告诉适配器不用默认逻辑
                }
                // 功能G：这一列的"值"是颜色的十六进制字符串，直接铺成整行底色
                if (view.getId() == R.id.row_bg) {
                    String bg = cursor.getString(columnIndex);
                    try {
                        int color = Color.parseColor(bg == null
                                ? NotePad.Notes.DEFAULT_BG_COLOR : bg);
                        if (color != 0xFFFFFFFF) {
                            view.setBackgroundColor(color);
                        }
                    } catch (Exception e) {
                        // 颜色串坏了就当白的，不显示也不影响别的
                    }
                    return true;
                }
                return false; // 其他列（title）走默认逻辑
            }
        });

        // Sets the ListView's adapter to be the cursor adapter that was just created.
        setListAdapter(adapter);

        // ===== 功能B：搜索框 =====
        // 拿到搜索框，输入内容一变就重新查询
        searchInput = (EditText) findViewById(R.id.search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // ===== 功能E：把文件夹导航条画出来 =====
        refreshFolderBar();

        // 功能H⑩：进页面就按当前条件查一次，空文件夹/没笔记时才有空态提示
        // （适配器自己那次查询管显示，但管不到空态那行字）
        refreshList();
    }

    /** 功能E：把所有文件夹的名字缓存起来，列表行显示"· 学习"用 */
    private void loadFolderNames() {
        folderNames.clear();
        for (Folder f : folderDb.listAll()) {
            folderNames.put(f.id, f.name);
        }
    }

    /**
     * 功能E：刷新笔记列表。
     * 两个条件叠加：当前文件夹（含其所有子孙文件夹） + 搜索关键词。
     */
    private void refreshList() {
        // 关键词
        String keyword = (searchInput == null) ? "" : searchInput.getText().toString().trim();

        StringBuilder selection = new StringBuilder();
        List<String> args = new ArrayList<>();

        // 条件一：当前文件夹范围内（点进"学习"能看到它和子文件夹里的全部笔记）
        if (currentFolderId != 0) {
            List<Long> ids = folderDb.selfAndDescendants(currentFolderId);
            selection.append(NotePad.Notes.COLUMN_NAME_FOLDER).append(" IN (");
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) selection.append(",");
                selection.append("?");
                args.add(String.valueOf(ids.get(i)));
            }
            selection.append(")");
        }

        // 条件二：关键词（标题或内容）
        if (!keyword.isEmpty()) {
            if (selection.length() > 0) selection.append(" AND ");
            // 富文本之后 note 列里存的是带格式的数据（含 {"v":1,"spans"... 这种字样），
            // 直接匹配会搜出乱七八糟的结果，所以搜 plain_text。
            // 老的纯文本笔记还没有 plain_text（是 NULL），用 COALESCE 退回 note 列照样能搜到。
            selection.append("(")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? OR ")
                    .append("COALESCE(").append(NotePad.Notes.COLUMN_NAME_PLAIN_TEXT)
                    .append(",").append(NotePad.Notes.COLUMN_NAME_NOTE).append(") LIKE ?")
                    .append(")");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }

        Cursor newCursor = getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                selection.length() == 0 ? null : selection.toString(),
                args.isEmpty() ? null : args.toArray(new String[0]),
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        // changeCursor 会自动关闭旧游标
        ((SimpleCursorAdapter) getListAdapter()).changeCursor(newCursor);

        // 功能H⑩：空态提示。搜索时有词就说"没找到含 xx 的笔记"，否则就是"这里还没笔记"
        View empty = findViewById(R.id.tv_empty);
        if (empty != null) {
            boolean hasNote = newCursor != null && newCursor.getCount() > 0;
            empty.setVisibility(hasNote ? View.GONE : View.VISIBLE);
            if (!hasNote && empty instanceof TextView) {
                ((TextView) empty).setText(keyword.isEmpty()
                        ? "这里还没有笔记，点右下角「＋」写一篇"
                        : "没有找到含「" + keyword + "」的笔记");
            }
        }
    }

    /**
     * 功能E：画文件夹导航条。
     * 顶层：全部 | 未分类 | 学习 | 工作 | ＋
     * 进到某一层后：上一级 | 它的子文件夹 | ＋
     */
    private void refreshFolderBar() {
        loadFolderNames();

        // 面包屑
        if (currentFolderId == 0) {
            tvFolderPath.setText("全部笔记");
            tvFolderPath.setOnClickListener(null);
        } else {
            List<String> path = folderDb.pathNames(currentFolderId);
            StringBuilder sb = new StringBuilder("全部笔记");
            for (String name : path) {
                sb.append(" › ").append(name);
            }
            tvFolderPath.setText(sb.toString());
            // 点面包屑 = 回到全部
            tvFolderPath.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentFolderId = 0;
                    refreshFolderBar();
                    refreshList();
                }
            });
        }

        folderBar.removeAllViews();

        // 顶层时给一个选中态的「全部」
        if (currentFolderId == 0) {
            folderBar.addView(makeChip("全部", true, null));
        } else {
            // 非顶层：加一个「← 上一级」
            folderBar.addView(makeChip("← 上一级", false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Folder f = folderDb.find(currentFolderId);
                    currentFolderId = (f == null) ? 0 : f.parentId;
                    refreshFolderBar();
                    refreshList();
                }
            }));
        }

        // 当前这一层的子文件夹
        long parentForChildren = (currentFolderId == 0) ? 0 : currentFolderId;
        for (final Folder child : folderDb.listByParent(parentForChildren)) {
            TextView chip = makeChip(child.name, false, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    currentFolderId = child.id;
                    refreshFolderBar();
                    refreshList();
                }
            });
            // 长按文件夹：改名 / 删除。
            // 「未分类」是默认文件夹，允许改名，但不给删除的入口（删了笔记就没地方落）
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showFolderActions(child);
                    return true;
                }
            });
            folderBar.addView(chip);
        }

        // 固定在最后的「＋新建」
        folderBar.addView(makeChip("＋", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptNewFolder();
            }
        }));
    }

    /** 功能E：生成一个文件夹按钮 */
    private TextView makeChip(String text, boolean selected, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(14), dp(7), dp(14), dp(7));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        // 功能H③：选中的 chip 直接用顶栏那个色底 + 白字，跟顶栏一条心
        bg.setColor(selected ? ThemeHelper.surface(this) : 0xFFEEEEEE);
        tv.setBackground(bg);
        tv.setTextColor(selected ? 0xFFFFFFFF : 0xFF333333);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(2), dp(4), dp(2));
        tv.setLayoutParams(lp);

        if (listener != null) {
            tv.setOnClickListener(listener);
        }
        return tv;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /** 功能E：在当前这一层新建文件夹 */
    private void promptNewFolder() {
        final EditText input = new EditText(this);
        input.setHint("文件夹名，例如 学习");
        input.setSingleLine(true);

        long parentId = (currentFolderId == 0) ? 0 : currentFolderId;

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setView(input)
                .setPositiveButton("创建", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            android.widget.Toast.makeText(NotesList.this, "文件夹名不能为空",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        folderDb.create(name, parentId);
                        refreshFolderBar();
                    }
                })
                .setNegativeButton("取消", null);
        DialogHelper.create(b, this, "新建文件夹", null).show();
    }

    /** 功能E：长按文件夹 → 改名 / 删除 */
    private void showFolderActions(final Folder folder) {
        // 默认文件夹只给"重命名"，不给"删除"
        final boolean isDefault = (folder.id == FolderDbHelper.DEFAULT_ID);
        String[] items = isDefault
                ? new String[]{"重命名"}
                : new String[]{"重命名", "删除文件夹"};

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setItems(items,
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (which == 0) {
                                    promptRenameFolder(folder);
                                } else {
                                    confirmDeleteFolder(folder);
                                }
                            }
                        });
        DialogHelper.create(b, this, folder.name, null).show();
    }

    /** 功能E：重命名文件夹 */
    private void promptRenameFolder(final Folder folder) {
        final EditText input = new EditText(this);
        input.setText(folder.name);
        input.setSelection(input.getText().length());
        input.setSingleLine(true);

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(this)
                .setView(input)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim();
                        if (name.isEmpty()) {
                            android.widget.Toast.makeText(NotesList.this, "文件夹名不能为空",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        folderDb.rename(folder.id, name);
                        refreshFolderBar();
                        refreshList();
                    }
                })
                .setNegativeButton("取消", null);
        DialogHelper.create(b, this, "重命名文件夹", null).show();
    }

    /** 功能E：删除文件夹——里面的笔记挪到「未分类」，笔记本身不删 */
    private void confirmDeleteFolder(final Folder folder) {
        DialogHelper.confirm(this, "删除文件夹",
                "「" + folder.name + "」和它下面的子文件夹会被删除，"
                        + "里面的笔记会移到「未分类」，笔记内容不会丢。",
                "删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        // 1. 该文件夹 + 所有子孙文件夹的 id
                        List<Long> ids = folderDb.descendantsForDelete(folder.id);

                        // 2. 把里面的笔记改成「未分类」
                        StringBuilder where = new StringBuilder(NotePad.Notes.COLUMN_NAME_FOLDER + " IN (");
                        String[] args = new String[ids.size()];
                        for (int i = 0; i < ids.size(); i++) {
                            if (i > 0) where.append(",");
                            where.append("?");
                            args[i] = String.valueOf(ids.get(i));
                        }
                        where.append(")");

                        android.content.ContentValues cv = new android.content.ContentValues();
                        cv.put(NotePad.Notes.COLUMN_NAME_FOLDER, NotePad.Notes.DEFAULT_FOLDER_ID);
                        getContentResolver().update(NotePad.Notes.CONTENT_URI, cv,
                                where.toString(), args);

                        // 3. 删掉文件夹记录
                        for (Long id : ids) {
                            folderDb.deleteSelf(id);
                        }

                        // 4. 如果当前正待在被删的文件夹里，退回顶层
                        if (ids.contains(currentFolderId)) {
                            currentFolderId = 0;
                        }
                        refreshFolderBar();
                        refreshList();
                    }
                }).show();
    }

    /** 功能E：从编辑页回来，笔记可能换了文件夹，刷新一下列表 */    @Override
    protected void onResume() {
        super.onResume();
        // 功能H③：从设置页回来要重新上色（顶栏 / 加号 / 底栏选中 / chip 选中）
        ThemeHelper.apply(this);
        NavHelper.bind(this);
        if (folderDb != null) {
            refreshFolderBar();
            refreshList();
        }
    }

    // 功能H②：原来这里的 onCreateOptionsMenu / onPrepareOptionsMenu 是往系统 ActionBar 塞菜单的。
    // 顶栏改成自己画以后没地方塞了，这两块整体删掉；「粘贴」挪到下面的 showTopMenu()。
    // 长按笔记的上下文菜单（onCreateContextMenu / onContextItemSelected）不受影响。

    /**
     * 功能H②：顶栏右侧 ⋮ 的菜单。系统 ActionBar 去掉了，菜单改成自己弹一个 PopupMenu，
     * 现在只剩「粘贴」一项：把剪贴板内容当成新笔记打开（老师原版功能）。
     */
    private void showTopMenu() {
        PopupMenu popup = new PopupMenu(this, findViewById(R.id.btn_top_more));
        // 剪贴板空的时候这一项点不动，跟原来系统菜单的行为一致
        popup.getMenu().add(0, MENU_ID_PASTE, 0, getString(R.string.menu_paste))
                .setEnabled(isClipboardReady());
        // 功能H⑧：设置已经挪到底栏「我的」，这里不再重复放
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == MENU_ID_PASTE) {
                    startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    /**
     * 功能H⑦：列表里的时间能省就省。
     * 今天 → "今天 08:55"；昨天 → "昨天 21:10"；今年 → "11-20 09:30"；往年 → "2025-12-01"
     */
    private String shortTime(long timeMillis) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(timeMillis);

        boolean thisYear = now.get(Calendar.YEAR) == t.get(Calendar.YEAR);
        String hm = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(timeMillis));

        if (thisYear && now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)) {
            return "今天 " + hm;
        }
        if (thisYear && now.get(Calendar.DAY_OF_YEAR) - t.get(Calendar.DAY_OF_YEAR) == 1) {
            return "昨天 " + hm;
        }
        if (thisYear) {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    .format(new Date(timeMillis));
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date(timeMillis));
    }

    /** 剪贴板里有没有东西 */
    private boolean isClipboardReady() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboard != null && clipboard.hasPrimaryClip();
    }

    // 功能H②：onPrepareOptionsMenu 跟着一起删了（系统菜单不再使用）。

    /**
     * 功能H①：新建笔记。原来挂在顶栏菜单里的 menu_add，现在交给右下角的圆形按钮。
     */
    private void startInsert() {
        /*
         * Launches a new Activity using an Intent. The intent filter for the Activity
         * has to have action ACTION_INSERT. No category is set, so DEFAULT is assumed.
         * In effect, this starts the NoteEditor Activity in NotePad.
         */
        // 功能E：新建笔记自动放进当前所在的文件夹（在"全部"里新建 → 进"未分类"）
        // 功能H③：在「全部」里新建时，用设置页里定的默认文件夹
        long folder = currentFolderId == 0 ? ThemeHelper.defaultFolderId(this) : currentFolderId;
        Intent addIntent = new Intent(Intent.ACTION_INSERT, getIntent().getData());
        addIntent.putExtra(FolderDbHelper.EXTRA_FOLDER_ID, folder);
        startActivity(addIntent);
    }

    // 功能H②：onOptionsItemSelected 也一起删了，「粘贴」改由 showTopMenu() 处理。


    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     *
     * The only available options are COPY and DELETE.
     *
     * Context-click is equivalent to long-press.
     *
     * @param menu A ContexMenu object to which items should be added.
     * @param view The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        // Tries to get the position of the item in the ListView that was long-pressed.
        try {
            // Casts the incoming data object into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            // If the menu object can't be cast, logs an error.
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        /*
         * Gets the data associated with the item at the selected position. getItem() returns
         * whatever the backing adapter of the ListView has associated with the item. In NotesList,
         * the adapter associated all of the data for a note with its list item. As a result,
         * getItem() returns that data as a Cursor.
         */
        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        // If the cursor is empty, then for some reason the adapter can't get the data from the
        // provider, so returns null to the caller.
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        // Append to the
        // menu items for any other activities that can do stuff with it
        // as well.  This does a query on the system for any activities that
        // implement the ALTERNATIVE_ACTION for our data, adding a menu item
        // for each one that is found.
        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        /*
         * Gets the extra info from the menu item. When an note in the Notes list is long-pressed, a
         * context menu appears. The menu items for the menu automatically get the data
         * associated with the note that was long-pressed. The data comes from the provider that
         * backs the list.
         *
         * The note's data is passed to the context menu creation routine in a ContextMenuInfo
         * object.
         *
         * When one of the context menu items is clicked, the same data is passed, along with the
         * note ID, to onContextItemSelected() via the item parameter.
         */
        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }
        // Appends the selected note's ID to the URI sent with the incoming Intent.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        /*
         * Gets the menu item's ID and compares it to known actions.
         */
        int id = item.getItemId();
        if (id == R.id.context_open) {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) { //BEGIN_INCLUDE(copy)
            // Gets a handle to the clipboard service.
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            // Copies the notes URI to the clipboard. In effect, this copies the note itself
            clipboard.setPrimaryClip(ClipData.newUri(   // new clipboard item holding a URI
                    getContentResolver(),               // resolver to retrieve URI info
                    "Note",                             // label for the clip
                    noteUri));                          // the URI

            // Returns to the caller and skips further processing.
            return true;
            //END_INCLUDE(copy)
        } else if (id == R.id.context_delete) {
            // 功能H⑩：删记录之前，先把这篇笔记插过的图片文件清掉。
            // 编辑页删笔记时也是这么做的，但列表页长按删走的另一条路，
            // 漏了这一步 files/notes_img/ 里的图就永远留在那儿占地方。
            deleteNoteImages(noteUri);

            // Deletes the note from the provider by passing in a URI in note ID format.
            // Please see the introductory note about performing provider operations on the
            // UI thread.
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being
                    // passed in.
                    null      // No where clause is used, so no where arguments are needed.
            );

            // Returns to the caller and skips further processing.
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * 功能H⑩：按笔记的 URI 查一下它占用了哪些图片，逐个删掉。
     * 查不到（比如这篇没插图）就当没这回事，不能因为清理失败挡住删除。
     */
    private void deleteNoteImages(Uri noteUri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(noteUri,
                    new String[]{NotePad.Notes.COLUMN_NAME_IMAGES}, null, null, null);
            if (c != null && c.moveToFirst()) {
                ImageStore.removeAll(this, c.getString(0));
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * This method is called when the user clicks a note in the displayed list.
     *
     * This method handles incoming actions of either PICK (get data from the provider) or
     * GET_CONTENT (get or create data). If the incoming action is EDIT, this method sends a
     * new Intent to start NoteEditor.
     * @param l The ListView that contains the clicked item
     * @param v The View of the individual item
     * @param position The position of v in the displayed list
     * @param id The row ID of the clicked item
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // Constructs a new URI from the incoming URI and the row ID
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // Gets the action from the incoming Intent
        String action = getIntent().getAction();

        // Handles requests for note data
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // Sets the result to return to the component that called this Activity. The
            // result contains the new URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // Sends out an Intent to start an Activity that can handle ACTION_EDIT. The
            // Intent's data is the note ID URI. The effect is to call NoteEdit.
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}
