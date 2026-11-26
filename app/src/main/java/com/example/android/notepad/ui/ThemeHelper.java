package com.example.android.notepad.ui;

import com.example.android.notepad.R;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

/**
 * 功能H③：七套主题 + 两项默认值。
 *
 * 每套主题有**三档**颜色，都从用户给的那张色卡来：
 *   1. surface —— 色卡压深一档：顶栏和加号的底色，配白字（对比 3.1~4.6）；
 *   2. ink     —— 同色相更亮一档：白底上的字和线（底栏选中、编辑页箭头、日历选中圆）；
 *   3. light   —— 色卡再压浅一点，铺色块底（chip 底）。
 *
 * 压深时只降明度、不降饱和（这套色明度 43~55%、饱和 55~70%），
 * 一旦把饱和也压下去，黄色会变芥末、绿色会变橄榄，就是"土"的来源。
 *
 * 顶栏统一白字。选中哪一套存在 SharedPreferences，各页在 onResume 重新上色。
 */
public final class ThemeHelper {

    /** 顺序和下面三个数组一一对应 */
    public static final String[] NAMES = {"紫", "粉", "蓝", "绿", "黄", "橙", "灰"};

    /** 顶栏 / 加号底色：色卡原色压深到白字可读（明度 43~55%，饱和 55~70%） */
    public static final int[] SURFACE = {
            0xFF7C77D2,     // 紫（白字对比 3.9）
            0xFFCE6B86,     // 粉（3.5）
            0xFF5488C6,     // 蓝（3.7）
            0xFF4C9760,     // 绿（3.6）
            0xFFBC8A22,     // 黄（3.1，金色）
            0xFFC87240,     // 橙（3.5）
            0xFF6E7683,     // 灰（4.6）
    };

    /** 白底上的文字、图标、选中态：同色相，但不压灰，走鲜正 */
    public static final int[] INK = {
            0xFF7A6FD2,     // 紫
            0xFFD25F80,     // 粉
            0xFF3C86CE,     // 蓝
            0xFF3E9E59,     // 绿
            0xFFBF9000,     // 黄
            0xFFDB6A2C,     // 橙
            0xFF5F6674,     // 灰
    };

    /** 色卡压浅：色块底 */
    public static final int[] LIGHT = {
            0xFFEDEEF9,
            0xFFFBE9E9,
            0xFFE9F1FA,
            0xFFF0F6E4,
            0xFFFEF4CF,
            0xFFFDEBDC,
            0xFFF0F1F3,
    };

    private static final String PREF = "notepad_setting";
    private static final String KEY_THEME = "theme_index";
    private static final String KEY_NEW_FOLDER = "new_note_folder";
    private static final String KEY_NEW_BG = "new_note_bg";

    private ThemeHelper() {
    }

    private static SharedPreferences pref(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ================= 主题 =================

    public static int index(Context context) {
        int i = pref(context).getInt(KEY_THEME, 0);
        return (i < 0 || i >= SURFACE.length) ? 0 : i;
    }

    public static void setIndex(Context context, int index) {
        pref(context).edit().putInt(KEY_THEME, index).apply();
    }

    /** 顶栏 / 加号底色：色卡原色 */
    public static int surface(Context context) {
        return SURFACE[index(context)];
    }

    /** 白底上的强调色（底栏选中、编辑页箭头、日历选中圆）：同色相加深 */
    public static int primary(Context context) {
        return INK[index(context)];
    }

    public static int light(Context context) {
        return LIGHT[index(context)];
    }

    /** 顶栏 / 加号上的文字色：七套统一白字 */
    public static int onSurface(Context context) {
        return 0xFFFFFFFF;
    }

    /**
     * 给页面上色：顶栏（底色 + 文字色）、右下角加号（底色 + 文字色）。
     * 各页面布局里控件 id 不一样，挨个试一遍。
     */
    public static void apply(Activity activity) {
        int surfaceColor = surface(activity);
        int onColor = onSurface(activity);

        View title = activity.findViewById(R.id.tv_top_title);
        if (title != null && title.getParent() instanceof View) {
            ((View) title.getParent()).setBackgroundColor(surfaceColor);
        }
        if (title instanceof TextView) {
            ((TextView) title).setTextColor(onColor);
        }
        View more = activity.findViewById(R.id.btn_top_more);
        if (more instanceof TextView) {
            ((TextView) more).setTextColor(onColor);
        }

        View fab = activity.findViewById(R.id.fab_add);
        if (fab == null) {
            fab = activity.findViewById(R.id.fab_add_todo);
        }
        if (fab == null) {
            fab = activity.findViewById(R.id.fab_add_on_date);
        }
        if (fab != null) {
            fab.setBackground(circle(surfaceColor));
            if (fab instanceof TextView) {
                ((TextView) fab).setTextColor(onColor);
            }
        }
    }

    // ================= 新建笔记的默认值 =================

    /** 默认放进哪个文件夹；1 = 「未分类」 */
    public static long defaultFolderId(Context context) {
        return pref(context).getLong(KEY_NEW_FOLDER, 1);
    }

    public static void setDefaultFolderId(Context context, long folderId) {
        pref(context).edit().putLong(KEY_NEW_FOLDER, folderId).apply();
    }

    /** 新建笔记默认底色，存成 "#RRGGBB" */
    public static String defaultBg(Context context) {
        return pref(context).getString(KEY_NEW_BG, "#FFFFFF");
    }

    public static void setDefaultBg(Context context, String color) {
        pref(context).edit().putString(KEY_NEW_BG, color).apply();
    }

    // ================= 小工具 =================

    public static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }
}
