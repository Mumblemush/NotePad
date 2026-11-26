package com.example.android.notepad;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.content.Context;

import com.example.android.notepad.image.ImageStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * 富文本的存与读。
 *
 * 数据库 note 列里存的东西长这样：
 *   {"v":1,"t":"今天学了 ContentProvider","spans":[{"s":2,"e":6,"k":"b"}]}
 *
 *   v     版本号，用来判断这一列是不是富文本
 *   t     纯文字（图片在文字里占一个 \uFFFC 占位符）
 *   spans 格式区间：s 起点，e 终点，k 类型，v 取值
 *         k="b"   加粗        k="i"  斜体      k="bi" 又粗又斜
 *         k="c"   文字颜色，v 是 int 型颜色值
 *         k="z"   字号，v 是 sp 数值
 *         k="img" 图片，v 是图片文件名
 *
 * 为什么不用 HTML：Android 自带的 Html.toHtml 会把字号 span 直接丢掉，
 * 加粗颜色能存、字号存不下，5 档字号到头来只剩 3 档。
 * 自己写一个"文字 + 区间表"，格式一个不丢，读回来也是精确的。
 */
public final class NoteFormat {

    /**
     * 带文件名的图片 span。
     * 直接 new ImageSpan(drawable, source) 在某些 ROM 上画不出来（图片变成一个小方块），
     * 所以显示还是走 ImageSpan(context, bitmap) 这条老路，
     * 文件名自己揣着，保存时用 getSource() 拿回来。
     */
    public static class NoteImageSpan extends ImageSpan {
        private final String mName;

        public NoteImageSpan(Context context, Bitmap bitmap, String name) {
            super(context, bitmap);
            mName = name;
        }

        @Override
        public String getSource() {
            return mName;
        }
    }

    /** 解析图片要用到 Context 读应用私有目录，开 Activity 的时候调一次 init 塞进来 */
    private static Context sCtx;

    private NoteFormat() {
    }

    public static void init(Context ctx) {
        if (ctx != null) {
            sCtx = ctx.getApplicationContext();
        }
    }

    /** 判断数据库里这一列是不是富文本（老笔记是纯文本，直接跳过） */
    public static boolean isRich(String raw) {
        return raw != null && raw.startsWith("{\"v\":");
    }

    /**
     * 编辑器里的内容 → 存进数据库的字符串。
     * 编辑器会往文字上挂很多无关的 span（光标位置、输入法候选、拼写检查），
     * 这里只认加粗/斜体/颜色/字号/图片，其它的全部丢掉。
     */
    public static String serialize(CharSequence text) {
        if (text == null) {
            return "";
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        JSONObject root = new JSONObject();
        JSONArray spans = new JSONArray();
        try {
            Object[] all = sb.getSpans(0, sb.length(), Object.class);
            for (Object sp : all) {
                int start = sb.getSpanStart(sp);
                int end = sb.getSpanEnd(sp);
                if (start < 0 || end <= start) {
                    continue;
                }
                JSONObject item = new JSONObject();
                if (sp instanceof StyleSpan) {
                    int style = ((StyleSpan) sp).getStyle();
                    if (style == Typeface.BOLD) {
                        item.put("k", "b");
                    } else if (style == Typeface.ITALIC) {
                        item.put("k", "i");
                    } else if (style == (Typeface.BOLD | Typeface.ITALIC)) {
                        item.put("k", "bi");
                    } else {
                        continue;
                    }
                } else if (sp instanceof ForegroundColorSpan) {
                    item.put("k", "c");
                    item.put("v", ((ForegroundColorSpan) sp).getForegroundColor());
                } else if (sp instanceof AbsoluteSizeSpan) {
                    item.put("k", "z");
                    item.put("v", ((AbsoluteSizeSpan) sp).getSize());
                } else if (sp instanceof ImageSpan) {
                    item.put("k", "img");
                    // 只存文件名，不存绝对路径：路径会随安装、换设备失效
                    String src = ((ImageSpan) sp).getSource();
                    if (src == null || src.length() == 0) {
                        continue;
                    }
                    item.put("v", new File(src).getName());
                } else {
                    continue;
                }
                item.put("s", start);
                item.put("e", end);
                spans.put(item);
            }
            root.put("v", 1);
            root.put("t", sb.toString());
            root.put("spans", spans);
        } catch (Exception e) {
            // 序列化失败就退回纯文本，宁可不带格式也不能丢字
            return sb.toString();
        }
        return root.toString();
    }

    /**
     * 数据库里的字符串 → 能塞回编辑器 / 能显示的内容。
     * 老笔记是纯文本，原样返回。
     */
    public static CharSequence parse(String raw) {
        if (raw == null || raw.length() == 0) {
            return "";
        }
        if (!isRich(raw)) {
            return raw;
        }
        try {
            JSONObject root = new JSONObject(raw);
            String text = root.optString("t", "");
            SpannableStringBuilder sb = new SpannableStringBuilder(text);
            JSONArray spans = root.optJSONArray("spans");
            if (spans != null) {
                for (int i = 0; i < spans.length(); i++) {
                    JSONObject item = spans.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    int start = item.optInt("s", -1);
                    int end = item.optInt("e", -1);
                    if (start < 0 || end > text.length() || end <= start) {
                        continue;
                    }
                    applySpan(sb, item, start, end);
                }
            }
            return sb;
        } catch (Exception e) {
            // 解析失败就当纯文本，至少文字还在
            return raw;
        }
    }

    private static void applySpan(SpannableStringBuilder sb, JSONObject item,
                                  int start, int end) {
        String kind = item.optString("k", "");
        if ("b".equals(kind)) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("i".equals(kind)) {
            sb.setSpan(new StyleSpan(Typeface.ITALIC), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("bi".equals(kind)) {
            sb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("c".equals(kind)) {
            sb.setSpan(new ForegroundColorSpan(item.optInt("v", 0xFF222222)),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("z".equals(kind)) {
            sb.setSpan(new AbsoluteSizeSpan(item.optInt("v", 16), true),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if ("img".equals(kind)) {
            String name = item.optString("v", "");
            if (sCtx == null || name.length() == 0) {
                return;
            }
            Bitmap bm = ImageStore.load(sCtx, name, ImageStore.SHOW_MAX_EDGE);
            if (bm == null) {
                return;
            }
            // 文件名一定要跟着 span 走，否则下次保存时这张图会被当成"没有来源"跳过
            sb.setSpan(new NoteImageSpan(sCtx, bm, name), start, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * 从存好的富文本里挑出所有图片文件名，用 "|" 串起来，写进 image_path 列。
     * 这一列纯属索引：删笔记的时候靠它把没用的图片文件一起清掉。
     */
    public static String imageFiles(String raw) {
        if (!isRich(raw)) {
            return "";
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray spans = root.optJSONArray("spans");
            if (spans == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < spans.length(); i++) {
                JSONObject item = spans.optJSONObject(i);
                if (item == null || !"img".equals(item.optString("k", ""))) {
                    continue;
                }
                String name = item.optString("v", "");
                if (name.length() == 0) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('|');
                }
                sb.append(name);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 取纯文本，写进 plain_text 列给搜索用。
     * 图片占位符换成空格，免得搜"图片"搜不到、搜单个字符又搜出一堆。
     */
    public static String plainText(String raw) {
        if (raw == null) {
            return "";
        }
        if (!isRich(raw)) {
            return raw;
        }
        try {
            JSONObject root = new JSONObject(raw);
            return root.optString("t", "").replace('\uFFFC', ' ');
        } catch (Exception e) {
            return raw;
        }
    }

    /** 编辑器里的内容 → 纯文本（保存时直接用这个算 plain_text） */
    public static String plainText(CharSequence text) {
        if (text == null) {
            return "";
        }
        return text.toString().replace('\uFFFC', ' ');
    }
}
