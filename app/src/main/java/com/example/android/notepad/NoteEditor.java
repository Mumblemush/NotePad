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

import static com.example.android.notepad.R.*;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

// ===== 功能E新增 import：笔记归属文件夹 =====
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.TextView;
import android.widget.Toast;

// ===== 功能F新增 import：格式工具栏 =====
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.ArrayAdapter;

// ===== 功能G新增 import：背景色 =====
import android.graphics.Color;
import android.view.ViewGroup;

// ===== 功能F⑥新增 import：正文插图 =====
import android.graphics.Bitmap;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.text.Editable;
import android.text.style.ImageSpan;

// ===== 功能I新增 import：自绘顶栏、标题区、字数统计 =====
import android.text.TextWatcher;
import android.widget.PopupMenu;

import com.example.android.notepad.image.ImageStore;

import com.example.android.notepad.folder.Folder;
import com.example.android.notepad.folder.FolderDbHelper;
import com.example.android.notepad.ui.DialogHelper;
import com.example.android.notepad.ui.ThemeHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * This Activity handles "editing" a note, where editing is responding to
 * {@link Intent#ACTION_VIEW} (request to view data), edit a note
 * {@link Intent#ACTION_EDIT}, create a note {@link Intent#ACTION_INSERT}, or
 * create a new note from the current contents of the clipboard {@link Intent#ACTION_PASTE}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler}
 * or {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NoteEditor extends Activity {
    // For logging and debugging purposes
    private static final String TAG = "NoteEditor";

    /*
     * Creates a projection that returns the note ID and the note contents.
     */
    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,
            NotePad.Notes.COLUMN_NAME_CREATE_DATE,  // 功能I：标题下面那行要显示创建时间
            NotePad.Notes.COLUMN_NAME_FOLDER,  // 功能E：所属文件夹
            NotePad.Notes.COLUMN_NAME_BG_COLOR,  // 功能G：重开笔记时要把存下的底色铺回去
            NotePad.Notes.COLUMN_NAME_IMAGES     // 功能F：换图时要知道这篇原来引用过哪些图
    };

    // ===== 功能E：文件夹归属 =====
    // 功能E：编辑页的文件夹状态
    private FolderDbHelper folderDb;
    private long currentFolderId = NotePad.Notes.DEFAULT_FOLDER_ID;
    private TextView tvFolder;
    // 功能E：用户是否已经在选择框里手动改过文件夹。
    // 改过就不要再让 onResume 的 requery 把值覆盖回数据库里的旧值。
    private boolean folderPicked = false;

    // A label for the saved state of the activity
    private static final String ORIGINAL_CONTENT = "origContent";

    // This Activity can be started by more than one action. Each action is represented
    // as a "state" constant
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // Global mutable variables
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;

    // ===== 功能F：格式工具栏 =====
    // 字号五档。中间那档 18sp 就是正文默认字号（跟 note_editor.xml 里对齐），选它等于"不加格式"
    private static final int[] FONT_SIZES = {14, 16, 18, 22, 28};
    private static final int DEFAULT_SIZE_INDEX = 2;
    private int mSizeIndex = DEFAULT_SIZE_INDEX;

    // 文字颜色：黑 / 红 / 橙 / 绿 / 蓝 / 紫
    private static final int[] TEXT_COLORS = {
            0xFF222222, 0xFFD32F2F, 0xFFEF6C00, 0xFF2E7D32, 0xFF1976D2, 0xFF6A1B9A};
    private static final String[] TEXT_COLOR_NAMES = {
            "黑色", "红色", "橙色", "绿色", "蓝色", "紫色"};

    // cutSpans 用它区分清哪一类格式
    private static final int KIND_STYLE = 1;
    private static final int KIND_COLOR = 2;
    private static final int KIND_SIZE = 3;

    // 功能F⑥：选图的请求码
    private static final int REQUEST_PICK_IMAGE = 1001;
    // 刚插完图，别让 onResume 拿库里的旧内容把正文刷回去
    private boolean skipReloadOnResume = false;

    private TextView btnBold;
    private TextView btnItalic;

    // 功能G：当前笔记的背景色，打开时从库里读，没读过就是默认白
    private int mBgColor = 0xFFFFFFFF;
    // 用户在这次编辑里手动改过背景色，onResume 就别再拿库里的旧值覆盖回去
    private boolean bgPicked = false;

    // ===== 功能I：编辑页重做 =====
    // 顶栏"更多"菜单里的两项，PopupMenu 用（自己编号，不用菜单资源文件）
    private static final int MENU_ID_DELETE = 1;
    private static final int MENU_ID_REVERT = 2;

    private TextView tvTitle;
    private TextView tvMeta;
    // 当前标题。null 表示这篇还没有标题，新建笔记保存时会自动取正文前 30 字
    private String mTitle;
    // 用户手动改过标题，onResume 读库时就不要把界面刷回旧值
    private boolean titleCustom = false;
    // 创建时间，显示在标题下面那行元信息里
    private long mCreated;

    /**
     * This method is called by Android when the Activity is first started. From the incoming
     * Intent, it determines what kind of editing is desired, and then does it.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Creates an Intent to use when the Activity object's result is sent back to the
         * caller.
         */
        final Intent intent = getIntent();

        /*
         *  Sets up for the edit, based on the action specified for the incoming Intent.
         */

        // Gets the action that triggered the intent filter for this Activity
        final String action = intent.getAction();

        // For an edit action:
        if (Intent.ACTION_EDIT.equals(action)) {

            // Sets the Activity state to EDIT, and gets the URI for the data to be edited.
            mState = STATE_EDIT;
            mUri = intent.getData();

            // For an insert or paste action:
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            // Sets the Activity state to INSERT, gets the general note URI, and inserts an
            // empty record in the provider
            mState = STATE_INSERT;

            // 功能E：新建笔记直接写进"来源文件夹"（从列表页传过来的 folder_id）
            currentFolderId = intent.getLongExtra(FolderDbHelper.EXTRA_FOLDER_ID,
                    NotePad.Notes.DEFAULT_FOLDER_ID);
            ContentValues initValues = new ContentValues();
            initValues.put(NotePad.Notes.COLUMN_NAME_FOLDER, currentFolderId);

            // 功能H③：新建笔记用设置页里定的默认底色
            String defBg = ThemeHelper.defaultBg(this);
            try {
                mBgColor = Color.parseColor(defBg);
            } catch (Exception e) {
                mBgColor = 0xFFFFFFFF;
            }
            initValues.put(NotePad.Notes.COLUMN_NAME_BG_COLOR, hexOf(mBgColor));

            mUri = getContentResolver().insert(intent.getData(), initValues);

            /*
             * If the attempt to insert the new note fails, shuts down this Activity. The
             * originating Activity receives back RESULT_CANCELED if it requested a result.
             * Logs that the insert failed.
             */
            if (mUri == null) {

                // Writes the log identifier, a message, and the URI that failed.
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                // Closes the activity.
                finish();
                return;
            }

            // Since the new entry was created, this sets the result to be returned
            // set the result to be returned.
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

        // If the action was other than EDIT or INSERT:
        } else {

            // Logs an error that the action was not understood, finishes the Activity, and
            // returns RESULT_CANCELED to an originating Activity.
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * Using the URI passed in with the triggering Intent, gets the note or notes in
         * the provider.
         * Note: This is being done on the UI thread. It will block the thread until the query
         * completes. In a sample app, going against a simple provider based on a local database,
         * the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        mCursor = managedQuery(
            mUri,         // The URI that gets multiple notes from the provider.
            PROJECTION,   // A projection that returns the note ID and note content for each note.
            null,         // No "where" clause selection criteria.
            null,         // No "where" clause selection values.
            null          // Use the default sort order (modification date, descending)
        );

        // For a paste, initializes the data from clipboard.
        // (Must be done after mCursor is initialized.)
        if (Intent.ACTION_PASTE.equals(action)) {
            // Does the paste
            performPaste();
            // Switches the state to EDIT so the title can be modified.
            mState = STATE_EDIT;
        }

        // Sets the layout for this Activity. See res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // Gets a handle to the EditText in the the layout.
        mText = (EditText) findViewById(R.id.note);

        // 功能H③：新建笔记时先把设置页定的默认底色铺上（编辑已有笔记时 onResume 会再按库里的值刷）
        mText.setBackgroundColor(mBgColor);

        // 功能H③：编辑页的「←」「⋮」和文件夹标签也跟着主题色走
        int accent = ThemeHelper.primary(this);
        ((TextView) findViewById(R.id.btn_back)).setTextColor(accent);
        ((TextView) findViewById(R.id.btn_more)).setTextColor(accent);

        // 功能I：正文一变就刷新标题下面那行的字数
        mText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshMeta();
            }
        });

        // ===== 功能E：文件夹归属 =====
        folderDb = new FolderDbHelper(this);
        tvFolder = (TextView) findViewById(R.id.tv_note_folder);
        if (tvFolder != null) {
            tvFolder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showFolderPicker();
                }
            });
        }
        updateFolderLabel();

        // ===== 功能I：自绘顶栏 + 标题区 =====
        tvTitle = (TextView) findViewById(R.id.tv_note_title);
        tvMeta = (TextView) findViewById(R.id.tv_note_meta);
        if (tvTitle != null) {
            tvTitle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTitleEditor();
                }
            });
        }
        TextView btnBack = (TextView) findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }
        TextView btnMore = (TextView) findViewById(R.id.btn_more);
        if (btnMore != null) {
            btnMore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showEditorMenu(view);
                }
            });
        }

        // ===== 功能F：格式工具栏 =====
        setupFormatBar();

        // 功能F⑥：富文本解析要用 Context 读图片文件，先把它塞给 NoteFormat
        NoteFormat.init(this);
        // 点正文里的图片 → 弹大图
        mText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    showImageAt(event.getX(), event.getY());
                }
                return false;   // 不吃事件，光标定位、长按选择照常
            }
        });

        /*
         * If this Activity had stopped previously, its state was written the ORIGINAL_CONTENT
         * location in the saved Instance state. This gets the state.
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    /**
     * This method is called when the Activity is about to come to the foreground. This happens
     * when the Activity comes to the top of the task stack, OR when it is first starting.
     *
     * Moves to the first note in the list, sets an appropriate title for the action chosen by
     * the user, puts the note contents into the TextView, and saves the original text as a
     * backup.
     */
    @Override
    protected void onResume() {
        super.onResume();

        /*
         * mCursor is initialized, since onCreate() always precedes onResume for any running
         * process. This tests that it's not null, since it should always contain data.
         */
        if (mCursor != null) {
            // Requery in case something changed while paused (such as the title)
            mCursor.requery();

            /* Moves to the first record. Always call moveToFirst() before accessing data in
             * a Cursor for the first time. The semantics of using a Cursor are that when it is
             * created, its internal index is pointing to a "place" immediately before the first
             * record.
             */
            mCursor.moveToFirst();

            // 功能E：编辑已有笔记时，从数据库读出它所在的文件夹
            // 用户刚在选择框里改过的话，以用户的选择为准
            int colFolderIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_FOLDER);
            if (colFolderIndex >= 0 && !folderPicked) {
                currentFolderId = mCursor.getLong(colFolderIndex);
                updateFolderLabel();
            }

            // 功能G：把库里存的背景色铺到正文区（用户刚改过的话以用户选的为准）
            int colBgIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_BG_COLOR);
            if (colBgIndex >= 0 && !bgPicked) {
                String bg = mCursor.getString(colBgIndex);
                if (bg != null) {
                    try {
                        mBgColor = Color.parseColor(bg);
                    } catch (Exception e) {
                        mBgColor = 0xFFFFFFFF;
                    }
                    mText.setBackgroundColor(mBgColor);
                }
            }

            // 功能I：标题不再放顶栏，改成正文上方那块大字。
            // 用户这次手动改过标题就以用户的为准，否则从库里读
            int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
            if (!titleCustom) {
                String dbTitle = mCursor.getString(colTitleIndex);
                // 新建的记录，Provider 会先塞一个 <Untitled> 占位，那不是真标题，当空的处理
                if (getString(android.R.string.untitled).equals(dbTitle)) {
                    dbTitle = null;
                }
                mTitle = dbTitle;
            }
            refreshTitle();

            // 创建时间，显示在标题下面那行元信息里
            int colCreatedIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CREATE_DATE);
            if (colCreatedIndex >= 0) {
                mCreated = mCursor.getLong(colCreatedIndex);
            }
            refreshMeta();

            /*
             * onResume() may have been called after the Activity lost focus (was paused).
             * The user was either editing or creating a note when the Activity paused.
             * The Activity should re-display the text that had been retrieved previously, but
             * it should not move the cursor. This helps the user to continue editing or entering.
             */

            // Gets the note text from the Cursor and puts it in the TextView, but doesn't change
            // the text cursor's position.
            // 富文本：库里的字符串要解析一下，加粗/颜色/字号才会回来；老笔记是纯文本，原样显示
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            // 功能F⑥：从相册选完图回来时，库里还没有这张图（onActivityResult 才插的），
            // 这里如果刷新就会把刚插好的图片冲掉，所以跳过这一次
            if (!skipReloadOnResume) {
                mText.setTextKeepState(NoteFormat.parse(note));
            }
            skipReloadOnResume = false;

            // Stores the original note text, to allow the user to revert changes.
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }

        /*
         * Something is wrong. The Cursor should always contain data. Report an error in the
         * note.
         */
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    /**
     * This method is called when an Activity loses focus during its normal operation, and is then
     * later on killed. The Activity has a chance to save its state so that the system can restore
     * it.
     *
     * Notice that this method isn't a normal part of the Activity lifecycle. It won't be called
     * if the user simply navigates away from the Activity.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save away the original text, so we still have it if the activity
        // needs to be killed while paused.
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * This method is called when the Activity loses focus.
     *
     * For Activity objects that edit information, onPause() may be the one place where changes are
     * saved. The Android application model is predicated on the idea that "save" and "exit" aren't
     * required actions. When users navigate away from an Activity, they shouldn't have to go back
     * to it to complete their work. The act of going away should save everything and leave the
     * Activity in a state where Android can destroy it if necessary.
     *
     * If the user hasn't done anything, then this deletes or clears out the note, otherwise it
     * writes the user's work to the provider.
     */
    @Override
    protected void onPause() {
        super.onPause();

        /*
         * Tests to see that the query operation didn't fail (see onCreate()). The Cursor object
         * will exist, even if no records were returned, unless the query failed because of some
         * exception or error.
         *
         */
        if (mCursor != null) {

            // Get the current note text.
            // 注意：这里拿到的是带格式的内容，长度判断要用纯文本，不能直接 toString
            CharSequence content = mText.getText();
            String plain = NoteFormat.plainText(content);
            int length = plain.length();

            /*
             * If the Activity is in the midst of finishing and there is no text in the current
             * note, returns a result of CANCELED to the caller, and deletes the note. This is done
             * even if the note was being edited, the assumption being that the user wanted to
             * "clear out" (delete) the note.
             */
            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();

                /*
                 * Writes the edits to the provider. The note has been edited if an existing note was
                 * retrieved into the editor *or* if a new note was inserted. In the latter case,
                 * onCreate() inserted a new empty note into the provider, and it is this new note
                 * that is being edited.
                 */
            } else if (mState == STATE_EDIT) {
                // 保存前先把格式序列化，否则加粗颜色字号全丢
                updateNote(NoteFormat.serialize(content), null);
            } else if (mState == STATE_INSERT) {
                // 功能I：手动写过标题就用自己的，没写过才让 updateNote 自动取正文前 30 字
                updateNote(NoteFormat.serialize(content), mTitle);
                mState = STATE_EDIT;
          }
        }
    }

    // ===================== 功能I：标题区 / 自绘顶栏 =====================

    /** 把当前标题刷到正文上方那块大字上；标题为空就留白，让灰色的「标题」占位露出来 */
    private void refreshTitle() {
        if (tvTitle == null) {
            return;
        }
        tvTitle.setText(mTitle == null ? "" : mTitle);
    }

    /** 刷新标题下面那行元信息：创建时间 ｜ 字数 */
    private void refreshMeta() {
        if (tvMeta == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (mCreated > 0) {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());
            sb.append(fmt.format(new Date(mCreated)));
            sb.append(" ｜ ");
        }
        int words = (mText == null) ? 0 : NoteFormat.plainText(mText.getText()).length();
        sb.append(words).append(" 字");
        tvMeta.setText(sb.toString());
    }

    /**
     * 点标题 → 弹框改名。
     * 改完立刻写一次库，不然 onResume 的 requery 会把界面刷回数据库里的旧标题。
     */
    private void showTitleEditor() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(mTitle == null ? "" : mTitle);
        input.setSelection(input.getText().length());

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText().toString().trim();
                        mTitle = (text.length() == 0) ? null : text;
                        titleCustom = true;
                        refreshTitle();

                        ContentValues cv = new ContentValues();
                        cv.put(NotePad.Notes.COLUMN_NAME_TITLE, mTitle == null ? "" : mTitle);
                        try {
                            getContentResolver().update(mUri, cv, null, null);
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton("取消", null);
        DialogHelper.create(b, this, "笔记标题", null).show();
    }

    /** 顶栏「⋮」：删除笔记 / 撤销这次修改 */
    private void showEditorMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, MENU_ID_DELETE, 0, "删除笔记");
        // 新建还没落过库的笔记，撤销没有意义
        if (mState == STATE_EDIT) {
            menu.getMenu().add(0, MENU_ID_REVERT, 1, "撤销这次修改");
        }
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == MENU_ID_DELETE) {
                    confirmDelete();
                    return true;
                }
                if (id == MENU_ID_REVERT) {
                    cancelNote();
                    return true;
                }
                return false;
            }
        });
        menu.show();
    }

    /** 删除前问一句：删掉就真没了，正文里插过的图片文件也会跟着清 */
    private void confirmDelete() {
        DialogHelper.confirm(this, "删除这篇笔记？",
                "正文和里面插入的图片会一起删除，没法恢复。",
                "删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteNote();
                        finish();
                    }
                }).show();
    }

//BEGIN_INCLUDE(paste)
    /**
     * A helper method that replaces the note's data with the contents of the clipboard.
     */
    private final void performPaste() {

        // Gets a handle to the Clipboard Manager
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // Gets a content resolver instance
        ContentResolver cr = getContentResolver();

        // Gets the clipboard data from the clipboard
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            // Gets the first item from the clipboard data
            ClipData.Item item = clip.getItemAt(0);

            // Tries to get the item's contents as a URI pointing to a note
            Uri uri = item.getUri();

            // Tests to see that the item actually is an URI, and that the URI
            // is a content URI pointing to a provider whose MIME type is the same
            // as the MIME type supported by the Note pad provider.
            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                // The clipboard holds a reference to data with a note MIME type. This copies it.
                Cursor orig = cr.query(
                        uri,            // URI for the content provider
                        PROJECTION,     // Get the columns referred to in the projection
                        null,           // No selection variables
                        null,           // No selection variables, so no criteria are needed
                        null            // Use the default sort order
                );

                // If the Cursor is not null, and it contains at least one record
                // (moveToFirst() returns true), then this gets the note data from it.
                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    // Closes the cursor.
                    orig.close();
                }
            }

            // If the contents of the clipboard wasn't a reference to a note, then
            // this converts whatever it is to text.
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // Updates the current note with the retrieved title and text.
            updateNote(text, title);
        }
    }
//END_INCLUDE(paste)

    /** 功能E：把"文件夹：学习 › Android"显示出来 */
    private void updateFolderLabel() {
        if (tvFolder == null || folderDb == null) return;
        List<String> path = folderDb.pathNames(currentFolderId);
        String name = (path == null || path.isEmpty())
                ? FolderDbHelper.DEFAULT_NAME
                : android.text.TextUtils.join(" › ", path);
        // 功能I：文件夹从那一整行蓝色提示挪到标题下面的元信息里，只留名字 + 一个下拉箭头
        tvFolder.setText("｜ " + name + " ▾");
        // 功能H③：这行跟着主题色
        tvFolder.setTextColor(ThemeHelper.primary(this));
    }

    /** 功能E：弹出文件夹选择框（整棵树平铺，缩进表示层级） */
    private void showFolderPicker() {
        final List<Folder> flat = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        collectTree(0, 0, flat, labels);

        int checked = 0;
        for (int i = 0; i < flat.size(); i++) {
            if (flat.get(i).id == currentFolderId) {
                checked = i;
                break;
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setSingleChoiceItems(labels.toArray(new String[0]), checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                currentFolderId = flat.get(which).id;
                                folderPicked = true;
                                // 立刻写库，避免对话框关掉后 onResume 的 requery 把界面刷回旧值
                                ContentValues cv = new ContentValues();
                                cv.put(NotePad.Notes.COLUMN_NAME_FOLDER, currentFolderId);
                                try {
                                    getContentResolver().update(mUri, cv, null, null);
                                } catch (Exception ignored) {
                                }
                                updateFolderLabel();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton("取消", null);
        DialogHelper.create(b, this, "放到哪个文件夹", null).show();
    }

    private void collectTree(long parentId, int depth, List<Folder> out, List<String> labels) {
        for (Folder f : folderDb.listByParent(parentId)) {
            out.add(f);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                sb.append("        ");
            }
            if (depth > 0) {
                // 用纯 ASCII 的短横线做层级前缀：部分机型字体没有框线字符（└）的字形，会显示成奇怪的字母
                sb.append("- ");
            }
            sb.append(f.name);
            labels.add(sb.toString());
            collectTree(f.id, depth + 1, out, labels);
        }
    }

    /**
     * Replaces the current note contents with the text and title provided as arguments.
     * @param text The new note contents to use.
     * @param title The new note title to use
     */
    private final void updateNote(String text, String title) {

        // text 传进来的时候已经是序列化后的富文本（也可能就是纯文本，比如从剪贴板粘来的）
        // 顺手算一份纯文本写进 plain_text，功能B 的搜索只认这一列
        String plain = NoteFormat.plainText(text);

        // Sets up a map to contain values to be updated in the provider.
        ContentValues values = new ContentValues();
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        // 功能E：保存时把当前选的文件夹一起写进去
        values.put(NotePad.Notes.COLUMN_NAME_FOLDER, currentFolderId);

        // If the action is to insert a new note, this creates an initial title for it.
        if (mState == STATE_INSERT) {

            // If no title was provided as an argument, create one from the note text.
            if (title == null) {

                // Get the note's length（标题只能用纯文本切，不能把格式数据切进去）
                int length = plain.length();

                // Sets the title by getting a substring of the text that is 31 characters long
                // or the number of characters in the note plus one, whichever is smaller.
                title = plain.substring(0, Math.min(30, length));

                // If the resulting length is more than 30 characters, chops off any
                // trailing spaces
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            // In the values map, sets the value of the title
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // In the values map, sets the value of the title
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        // This puts the desired notes text into the map.
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
        // 搜索用的纯文本
        values.put(NotePad.Notes.COLUMN_NAME_PLAIN_TEXT, plain);
        // 功能G：背景色一起存
        values.put(NotePad.Notes.COLUMN_NAME_BG_COLOR, hexOf(mBgColor));
        // 功能F⑥：把这篇里用到的图片文件名记一份，删笔记时靠它清文件
        values.put(NotePad.Notes.COLUMN_NAME_IMAGES, NoteFormat.imageFiles(text));

        /*
         * Updates the provider with the new values in the map. The ListView is updated
         * automatically. The provider sets this up by setting the notification URI for
         * query Cursor objects to the incoming URI. The content resolver is thus
         * automatically notified when the Cursor for the URI changes, and the UI is
         * updated.
         * Note: This is being done on the UI thread. It will block the thread until the
         * update completes. In a sample app, going against a simple provider based on a
         * local database, the block will be momentary, but in a real app you should use
         * android.content.AsyncQueryHandler or android.os.AsyncTask.
         */
        getContentResolver().update(
                mUri,    // The URI for the record to update.
                values,  // The map of column names and new values to apply to them.
                null,    // No selection criteria are used, so no where columns are necessary.
                null     // No where columns are used, so no where arguments are necessary.
            );


    }

    /**
     * This helper method cancels the work done on a note.  It deletes the note if it was
     * newly created, or reverts to the original text of the note i
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // Put the original note text back into the database
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                // 纯文本那份也一起退回去，不然搜索列还留着改动后的内容
                values.put(NotePad.Notes.COLUMN_NAME_PLAIN_TEXT,
                        NoteFormat.plainText(mOriginalContent));
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // We inserted an empty note, make sure to delete it
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * Take care of deleting a note.  Simply deletes the entry.
     */
    private final void deleteNote() {
        if (mCursor != null) {
            // 功能F⑥：先把这篇带进来的图片文件清掉，再删记录
            int idxImages = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_IMAGES);
            if (idxImages >= 0 && mCursor.moveToFirst()) {
                ImageStore.removeAll(this, mCursor.getString(idxImages));
            }
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }

    // ===================== 功能F：格式工具栏 =====================

    private void setupFormatBar() {
        btnBold = (TextView) findViewById(R.id.btn_bold);
        btnItalic = (TextView) findViewById(R.id.btn_italic);
        TextView btnSizeDown = (TextView) findViewById(R.id.btn_size_down);
        TextView btnSizeUp = (TextView) findViewById(R.id.btn_size_up);
        TextView btnColor = (TextView) findViewById(R.id.btn_color);
        TextView btnImage = (TextView) findViewById(R.id.btn_image);
        TextView btnBg = (TextView) findViewById(R.id.btn_bg);

        if (btnBold != null) {
            btnBold.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleStyle(Typeface.BOLD);
                }
            });
        }
        if (btnItalic != null) {
            btnItalic.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleStyle(Typeface.ITALIC);
                }
            });
        }
        if (btnSizeDown != null) {
            btnSizeDown.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    changeSize(-1);
                }
            });
        }
        if (btnSizeUp != null) {
            btnSizeUp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    changeSize(1);
                }
            });
        }
        if (btnColor != null) {
            btnColor.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showColorPicker();
                }
            });
        }
        if (btnImage != null) {
            btnImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickImage();
                }
            });
        }
        // 功能G：整篇笔记的背景色
        if (btnBg != null) {
            btnBg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBgPicker();
                }
            });
        }
    }

    /**
     * 功能F⑥：打开系统的图片选择器。
     * 用 ACTION_GET_CONTENT 而不是直接读相册文件，好处是不用申请存储权限 ——
     * 系统会临时把这个图的读取权授给我们，选完就收回。
     */
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择图片"),
                    REQUEST_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "打不开图片选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }
        // 选中的图先复制一份压缩版到自己目录，再往正文里插
        String name = ImageStore.save(this, data.getData());
        if (name == null) {
            Toast.makeText(this, "这张图读不出来，换一张试试", Toast.LENGTH_SHORT).show();
            return;
        }
        insertImage(name);
        // 图插进正文后立刻落库一次：这样即使 onResume 刷新，库里也已经有这张图了
        skipReloadOnResume = true;
        updateNote(NoteFormat.serialize(mText.getText()), null);
    }

    /** 在光标处插一个图片占位符，并把 ImageSpan 挂上去 */
    private void insertImage(String name) {
        Bitmap bm = ImageStore.load(this, name, ImageStore.SHOW_MAX_EDGE);
        if (bm == null) {
            Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Editable e = mText.getText();
        int at = mText.getSelectionStart();
        if (at < 0 || at > e.length()) {
            at = e.length();
        }
        e.insert(at, "\uFFFC");
        // 用 NoteFormat 里那个带文件名的 span：既能显示，保存时也能拿到文件名
        NoteFormat.NoteImageSpan span = new NoteFormat.NoteImageSpan(this, bm, name);
        e.setSpan(span, at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        // 直接改 Editable 有时不会让 EditText 重新排版，setTextKeepState 一遍强制刷新，
        // 插完图当场就能看到（光标位置不会变）
        mText.setTextKeepState(e);
        mText.setSelection(Math.min(at + 1, mText.getText().length()));
        mText.requestLayout();
        mText.invalidate();
        Toast.makeText(this, "图片已插入", Toast.LENGTH_SHORT).show();
    }

    /** 点在正文的图片上 → 弹一个大图预览 */
    private void showImageAt(float x, float y) {
        try {
            int offset = mText.getOffsetForPosition(x, y);
            Spannable sb = mText.getText();
            int len = sb.length();
            if (len == 0) {
                return;
            }
            // 图片占一个字符，点在边缘时 offset 可能落在前后一格，前后都查一下。
            // 注意起点要先夹到 0：从 -1 开始的 for 循环条件一假就整个跳出了，等于什么都没查。
            int from = Math.max(0, offset - 1);
            int to = Math.min(len - 1, offset);
            ImageSpan hit = null;
            for (int o = from; o <= to; o++) {
                ImageSpan[] found = sb.getSpans(o, o + 1, ImageSpan.class);
                if (found.length > 0) {
                    hit = found[0];
                    break;
                }
            }
            if (hit == null) {
                return;
            }
            String name = hit.getSource();
            if (name == null) {
                return;
            }
            Bitmap bm = ImageStore.load(this, name, ImageStore.SAVE_MAX_EDGE);
            if (bm == null) {
                return;
            }
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(bm);
            iv.setAdjustViewBounds(true);
            new AlertDialog.Builder(this)
                    .setView(iv)
                    .setPositiveButton("关闭", null)
                    .show();
        } catch (Exception e) {
            // 点图定位失败就当普通点击，不影响编辑
        }
    }

    /**
     * 加粗 / 斜体。选中一段字点一下就给这段加上，再点一下取消。
     * 只对选中的文字生效，没选东西就提示。
     */
    private void toggleStyle(int style) {
        int start = mText.getSelectionStart();
        int end = mText.getSelectionEnd();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start == end) {
            Toast.makeText(this, "先选中要改的文字", Toast.LENGTH_SHORT).show();
            return;
        }

        Spannable sb = mText.getText();
        // 先判断这一整段是不是已经加粗了，是的话这次是取消
        boolean covered = isStyleCovered(sb, start, end, style);
        // 把选区里同类型的 span 清掉（跨出选区的部分拆开保留）
        cutSpans(sb, start, end, KIND_STYLE);
        if (!covered) {
            sb.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        String what = (style == Typeface.BOLD) ? "加粗" : "斜体";
        Toast.makeText(this, (covered ? "已取消" : "已设为") + what,
                Toast.LENGTH_SHORT).show();
        refreshStyleButtons();
    }

    /**
     * 字号加大或减小一档。中间那档 22sp 是不加格式（跟编辑器默认一样）。
     */
    private void changeSize(int dir) {
        int start = mText.getSelectionStart();
        int end = mText.getSelectionEnd();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start == end) {
            Toast.makeText(this, "先选中要改的文字", Toast.LENGTH_SHORT).show();
            return;
        }

        mSizeIndex = mSizeIndex + dir;
        if (mSizeIndex < 0) {
            mSizeIndex = 0;
        }
        if (mSizeIndex >= FONT_SIZES.length) {
            mSizeIndex = FONT_SIZES.length - 1;
        }

        Spannable sb = mText.getText();
        // 先把这段原来的字号清掉，不然会一层套一层越改越大
        cutSpans(sb, start, end, KIND_SIZE);
        // 中间那档就是默认大小，不用挂 span
        if (mSizeIndex != DEFAULT_SIZE_INDEX) {
            sb.setSpan(new AbsoluteSizeSpan(FONT_SIZES[mSizeIndex], true),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Toast.makeText(this, "字号 " + FONT_SIZES[mSizeIndex] + "sp",
                Toast.LENGTH_SHORT).show();
    }

    /**
     * 弹一个色板，选一个颜色套到选中的文字上。
     */
    private void showColorPicker() {
        int start = mText.getSelectionStart();
        int end = mText.getSelectionEnd();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        if (start == end) {
            Toast.makeText(this, "先选中要改的文字", Toast.LENGTH_SHORT).show();
            return;
        }
        final int selStart = start;
        final int selEnd = end;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, TEXT_COLOR_NAMES) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(TEXT_COLORS[position]);
                return tv;
            }
        };

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyColor(selStart, selEnd, TEXT_COLORS[which]);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null);
        DialogHelper.create(b, this, "文字颜色", null).show();
    }

    private void applyColor(int start, int end, int color) {
        Spannable sb = mText.getText();
        cutSpans(sb, start, end, KIND_COLOR);
        sb.setSpan(new ForegroundColorSpan(color), start, end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * 功能G：选整篇笔记的背景色。六个颜色都是极浅的，铺满整页也不抢字。
     */
    private void showBgPicker() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, NotePad.Notes.BG_COLOR_NAMES) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setBackgroundColor(Color.parseColor(NotePad.Notes.BG_COLORS[position]));
                tv.setTextColor(0xFF333333);
                tv.setPadding(24, 32, 24, 32);
                return tv;
            }
        };

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyBgColor(NotePad.Notes.BG_COLORS[which]);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null);
        DialogHelper.create(b, this, "笔记背景色", null).show();
    }

    private void applyBgColor(String color) {
        try {
            mBgColor = Color.parseColor(color);
        } catch (Exception e) {
            return;
        }
        mText.setBackgroundColor(mBgColor);
        bgPicked = true;
        Toast.makeText(this, "背景已换成" + bgNameOf(mBgColor), Toast.LENGTH_SHORT).show();
    }

    private String bgNameOf(int color) {
        String hex = hexOf(color);
        for (int i = 0; i < NotePad.Notes.BG_COLORS.length; i++) {
            if (NotePad.Notes.BG_COLORS[i].equalsIgnoreCase(hex)) {
                return NotePad.Notes.BG_COLOR_NAMES[i];
            }
        }
        return "自定义";
    }

    private String hexOf(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    /**
     * 清掉 [start,end) 里某一类 span。原来的 span 如果比选区大，就把两头剩下的部分
     * 原样补回去 —— 不然给一个词改颜色，会把同一行里本来同色的字一起吃掉。
     */
    private void cutSpans(Spannable sb, int start, int end, int kind) {
        if (kind == KIND_STYLE) {
            for (StyleSpan sp : sb.getSpans(start, end, StyleSpan.class)) {
                int s = sb.getSpanStart(sp);
                int e = sb.getSpanEnd(sp);
                int style = sp.getStyle();
                sb.removeSpan(sp);
                if (s < start) {
                    sb.setSpan(new StyleSpan(style), s, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (e > end) {
                    sb.setSpan(new StyleSpan(style), end, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } else if (kind == KIND_COLOR) {
            for (ForegroundColorSpan sp : sb.getSpans(start, end, ForegroundColorSpan.class)) {
                int s = sb.getSpanStart(sp);
                int e = sb.getSpanEnd(sp);
                int c = sp.getForegroundColor();
                sb.removeSpan(sp);
                if (s < start) {
                    sb.setSpan(new ForegroundColorSpan(c), s, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (e > end) {
                    sb.setSpan(new ForegroundColorSpan(c), end, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } else {
            for (AbsoluteSizeSpan sp : sb.getSpans(start, end, AbsoluteSizeSpan.class)) {
                int s = sb.getSpanStart(sp);
                int e = sb.getSpanEnd(sp);
                int px = sp.getSize();
                sb.removeSpan(sp);
                if (s < start) {
                    sb.setSpan(new AbsoluteSizeSpan(px, true), s, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (e > end) {
                    sb.setSpan(new AbsoluteSizeSpan(px, true), end, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }

    private boolean isStyleCovered(Spannable sb, int start, int end, int style) {
        for (StyleSpan sp : sb.getSpans(start, end, StyleSpan.class)) {
            if (sp.getStyle() == style
                    && sb.getSpanStart(sp) <= start && sb.getSpanEnd(sp) >= end) {
                return true;
            }
        }
        return false;
    }

    /**
     * 让 B / I 两个按钮反映当前选区的状态：整段已经是粗体，按钮就点亮。
     */
    private void refreshStyleButtons() {
        int start = mText.getSelectionStart();
        int end = mText.getSelectionEnd();
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }
        setBtnActive(btnBold, hasStyle(start, end, Typeface.BOLD));
        setBtnActive(btnItalic, hasStyle(start, end, Typeface.ITALIC));
    }

    private boolean hasStyle(int start, int end, int style) {
        if (start >= end) {
            return false;
        }
        Spannable sb = mText.getText();
        for (StyleSpan sp : sb.getSpans(start, end, StyleSpan.class)) {
            if (sp.getStyle() == style
                    && sb.getSpanStart(sp) <= start && sb.getSpanEnd(sp) >= end) {
                return true;
            }
        }
        return false;
    }

    private void setBtnActive(TextView btn, boolean active) {
        if (btn == null) {
            return;
        }
        if (active) {
            btn.setBackgroundColor(0xFF0288D1);
            btn.setTextColor(0xFFFFFFFF);
        } else {
            btn.setBackgroundResource(R.drawable.fmt_btn_bg);
            btn.setTextColor(0xFF333333);
        }
    }
}
