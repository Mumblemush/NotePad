package com.example.android.notepad.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 笔记里插的图片，统一放在应用私有目录 files/notes_img/ 下。
 *
 * 为什么不用原图、也不存绝对路径：
 *  - 原图动辄几 MB，一篇笔记插两张，数据库和内存都受不了，所以复制一份压缩过的进来；
 *  - 相册里的图用户随时可能删、可能改名，绝对路径存下来过两天就是死链，
 *    所以只认自己目录里那份，数据库里只记文件名。
 */
public final class ImageStore {

    /** 存盘用的最长边：1080 足够看清细节，体积又压得住 */
    public static final int SAVE_MAX_EDGE = 1080;
    /** 编辑器里显示的最长边：比屏幕宽一点点，铺满一行又不撑出横向滚动 */
    public static final int SHOW_MAX_EDGE = 900;
    private static final int JPEG_QUALITY = 85;
    private static final String DIR_NAME = "notes_img";

    private ImageStore() {
    }

    public static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), DIR_NAME);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static File fileOf(Context ctx, String name) {
        return new File(dir(ctx), name);
    }

    /**
     * 把相册里选的图复制一份压缩版进来，返回文件名（不带路径）。
     * 失败返回 null。
     */
    public static String save(Context ctx, Uri uri) {
        if (uri == null) {
            return null;
        }
        InputStream in = null;
        try {
            // 第一遍只读尺寸，不解码像素，省内存
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            in = ctx.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, opts);
            closeQuietly(in);
            in = null;

            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                return null;
            }

            // 按 2 的幂降采样，先粗缩到 1080 上下
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSizeFor(opts.outWidth, opts.outHeight, SAVE_MAX_EDGE);
            in = ctx.getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(in, null, opts);
            closeQuietly(in);
            in = null;
            if (bm == null) {
                return null;
            }

            // 再精确缩到最长边 1080
            Bitmap scaled = scaleToMaxEdge(bm, SAVE_MAX_EDGE);
            if (scaled != bm) {
                bm.recycle();
            }

            String name = "img_" + System.currentTimeMillis() + ".jpg";
            File out = fileOf(ctx, name);
            FileOutputStream fos = new FileOutputStream(out);
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
            fos.flush();
            fos.close();
            scaled.recycle();
            return name;
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    /** 按文件名读一张出来，最长边限制在 maxEdge 内；读不到返回 null */
    public static Bitmap load(Context ctx, String name, int maxEdge) {
        if (name == null || name.length() == 0) {
            return null;
        }
        try {
            File f = fileOf(ctx, name);
            if (!f.exists()) {
                return null;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (opts.outWidth <= 0) {
                return null;
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSizeFor(opts.outWidth, opts.outHeight, maxEdge);
            Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (bm == null) {
                return null;
            }
            Bitmap scaled = scaleToMaxEdge(bm, maxEdge);
            if (scaled != bm) {
                bm.recycle();
            }
            return scaled;
        } catch (Exception e) {
            return null;
        }
    }

    public static void remove(Context ctx, String name) {
        if (name == null || name.length() == 0) {
            return;
        }
        try {
            File f = fileOf(ctx, name);
            if (f.exists()) {
                f.delete();
            }
        } catch (Exception e) {
            // 删不掉就算了，别影响主流程
        }
    }

    /** 一次删一串（"a.jpg|b.jpg"） */
    public static void removeAll(Context ctx, String packed) {
        if (packed == null || packed.length() == 0) {
            return;
        }
        for (String name : packed.split("\\|")) {
            remove(ctx, name);
        }
    }

    private static int sampleSizeFor(int width, int height, int maxEdge) {
        int longEdge = Math.max(width, height);
        int sample = 1;
        while (longEdge / sample > maxEdge * 2) {
            sample *= 2;
        }
        return sample;
    }

    private static Bitmap scaleToMaxEdge(Bitmap src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= maxEdge) {
            return src;
        }
        float ratio = (float) maxEdge / longEdge;
        int nw = Math.max(1, Math.round(w * ratio));
        int nh = Math.max(1, Math.round(h * ratio));
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }
}
