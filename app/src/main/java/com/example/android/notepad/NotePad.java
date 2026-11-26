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

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Defines a contract between the Note Pad content provider and its clients. A contract defines the
 * information that a client needs to access the provider as one or more data tables. A contract
 * is a public, non-extendable (final) class that contains constants defining column names and
 * URIs. A well-written client depends only on the constants in the contract.
 */
public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    // This class cannot be instantiated
    private NotePad() {
    }

    /**
     * Notes table contract
     */
    public static final class Notes implements BaseColumns {

        // This class cannot be instantiated
        private Notes() {}

        /**
         * The table name offered by this provider
         */
        public static final String TABLE_NAME = "notes";

        /*
         * URI definitions
         */

        /**
         * The scheme part for this provider's URI
         */
        private static final String SCHEME = "content://";

        /**
         * Path parts for the URIs
         */

        /**
         * Path part for the Notes URI
         */
        private static final String PATH_NOTES = "/notes";

        /**
         * Path part for the Note ID URI
         */
        private static final String PATH_NOTE_ID = "/notes/";

        /**
         * 0-relative position of a note ID segment in the path part of a note ID URI
         */
        public static final int NOTE_ID_PATH_POSITION = 1;

        /**
         * Path part for the Live Folder URI
         */
        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /**
         * The content URI base for a single note. Callers must
         * append a numeric note id to this Uri to retrieve a note
         */
        public static final Uri CONTENT_ID_URI_BASE
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /**
         * The content URI match pattern for a single note, specified by its ID. Use this to match
         * incoming URIs or to construct an Intent.
         */
        public static final Uri CONTENT_ID_URI_PATTERN
            = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        /**
         * The content Uri pattern for a notes listing for live folders
         */
        public static final Uri LIVE_FOLDER_URI
            = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

        /*
         * MIME type definitions
         */

        /**
         * The MIME type of {@link #CONTENT_URI} providing a directory of notes.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        /**
         * The MIME type of a {@link #CONTENT_URI} sub-directory of a single
         * note.
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /*
         * Column definitions
         */

        /**
         * Column name for the title of the note
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * Column name of the note content
         * <P>Type: TEXT</P>
         */
        public static final String COLUMN_NAME_NOTE = "note";

        /**
         * Column name for the creation timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * Column name for the modification timestamp
         * <P>Type: INTEGER (long from System.curentTimeMillis())</P>
         */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        /**
         * Column name for the folder that the note belongs to
         * <P>Type: INTEGER</P>
         * <P>references folders._id; 1 is the default "未分类" folder</P>
         */
        public static final String COLUMN_NAME_FOLDER = "folder_id";

        /**
         * The id of the default folder ("未分类") that every uncategorized note belongs to
         */
        public static final long DEFAULT_FOLDER_ID = 1;

        /**
         * Column name of the plain text of the note.
         * <P>Type: TEXT</P>
         * <P>存剥掉格式标记后的纯文本，只给搜索用（功能B）</P>
         */
        public static final String COLUMN_NAME_PLAIN_TEXT = "plain_text";

        /**
         * Column name of the note's background color.
         * <P>Type: TEXT ("#RRGGBB")</P>
         */
        public static final String COLUMN_NAME_BG_COLOR = "bg_color";

        /**
         * Column name of the image file names used by this note.
         * <P>Type: TEXT（多个用 "|" 分隔，只存文件名，不存绝对路径）</P>
         * <P>正文里的图片信息在 note 的格式数据里已经有了，这一列是冗余索引，
         *    只为删笔记时能直接找到要清理的图片文件</P>
         */
        public static final String COLUMN_NAME_IMAGES = "image_path";

        /**
         * 笔记背景色的可选值。比"纯白"稍微带一点点色，看着是彩色的纸，
         * 但不至于艳到压字。
         */
        public static final String[] BG_COLORS = {
                "#FFFFFF", // 白
                "#FFF2F2", // 浅红
                "#FFFAE6", // 浅黄
                "#EDF5FF", // 浅蓝
                "#F0FAF2", // 浅绿
                "#F5F0FB", // 浅紫
        };

        /**
         * 背景色对应的名字，顺序和 BG_COLORS 一一对应
         */
        public static final String[] BG_COLOR_NAMES = {
                "白", "浅红", "浅黄", "浅蓝", "浅绿", "浅紫"
        };

        /**
         * 默认背景色
         */
        public static final String DEFAULT_BG_COLOR = "#FFFFFF";
    }
}
