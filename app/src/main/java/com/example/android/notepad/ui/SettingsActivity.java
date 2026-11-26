package com.example.android.notepad.ui;

import com.example.android.notepad.NotePad;
import com.example.android.notepad.R;
import com.example.android.notepad.folder.Folder;
import com.example.android.notepad.folder.FolderDbHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * 功能H③：设置页。
 * 三项：外观主题（六套）/ 新建笔记的默认文件夹与默认底色 / 关于。
 * 主题选完立刻生效——三个主页面在 onResume 里会重新上色。
 */
public class SettingsActivity extends Activity {

    private LinearLayout themeList;
    private TextView tvDefaultFolder;
    private View vDefaultBg;
    private FolderDbHelper folderDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 功能H⑧：这里就是底栏第四个 tab「我的」，所以要有自己的底栏
        NavHelper.bind(this);

        ((TextView) findViewById(R.id.tv_top_title)).setText("我的");
        // 设置页自己不需要 ⋮
        findViewById(R.id.btn_top_more).setVisibility(View.GONE);

        themeList = (LinearLayout) findViewById(R.id.theme_list);
        tvDefaultFolder = (TextView) findViewById(R.id.tv_default_folder);
        vDefaultBg = findViewById(R.id.v_default_bg);

        folderDb = new FolderDbHelper(this);

        buildThemeRows();

        findViewById(R.id.row_default_folder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDefaultFolderPicker();
            }
        });

        findViewById(R.id.row_default_bg).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDefaultBgPicker();
            }
        });

        findViewById(R.id.row_about).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder b = new AlertDialog.Builder(SettingsActivity.this)
                        .setMessage("NotePad 记事本（移动软件开发期中实验）\n\n"
                                + "在老师原版 NotePad 的基础上做了：时间戳、搜索、文件夹分类、"
                                + "待办与日历、富文本（加粗/斜体/字号/颜色/插图）、笔记背景色，"
                                + "并重做了编辑页和整体界面。\n\n"
                                + "数据全部保存在本机，不联网、不申请存储权限。")
                        .setPositiveButton("知道了", null);
                DialogHelper.create(b, SettingsActivity.this, "关于本应用", null).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeHelper.apply(this);
        NavHelper.bind(this);      // 换主题后底栏「我的」的高亮色也要跟着变
        refreshValues();
    }

    // ================= 外观主题 =================

    private void buildThemeRows() {
        themeList.removeAllViews();
        int current = ThemeHelper.index(this);

        for (int i = 0; i < ThemeHelper.NAMES.length; i++) {
            final int index = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(16), 0);

            // 左边一个该主题底色的小圆（就是它的顶栏色）
            View dot = new View(this);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(ThemeHelper.SURFACE[i]);
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(16), dp(16));
            dotLp.rightMargin = dp(12);
            row.addView(dot, dotLp);

            TextView name = new TextView(this);
            name.setText(ThemeHelper.NAMES[i]);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            name.setTextColor(getResources().getColor(R.color.text_main));
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView check = new TextView(this);
            check.setText(i == current ? "✓" : "");
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            check.setTextColor(ThemeHelper.INK[i]);
            row.addView(check, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ThemeHelper.setIndex(SettingsActivity.this, index);
                    buildThemeRows();
                    ThemeHelper.apply(SettingsActivity.this);   // 本页顶栏当场换色
                }
            });

            themeList.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

            if (i < ThemeHelper.NAMES.length - 1) {
                View line = new View(this);
                line.setBackgroundColor(getResources().getColor(R.color.divider));
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lineLp.leftMargin = dp(16);
                themeList.addView(line, lineLp);
            }
        }
    }

    // ================= 新建笔记默认值 =================

    private void refreshValues() {
        Folder f = folderDb.find(ThemeHelper.defaultFolderId(this));
        tvDefaultFolder.setText(f == null ? FolderDbHelper.DEFAULT_NAME : f.name);

        try {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(4));
            bg.setColor(Color.parseColor(ThemeHelper.defaultBg(this)));
            bg.setStroke(dp(1), 0xFFDDDDDD);
            vDefaultBg.setBackground(bg);
        } catch (Exception e) {
            vDefaultBg.setBackgroundColor(Color.WHITE);
        }
    }

    private void showDefaultFolderPicker() {
        final List<Folder> all = folderDb.listAll();
        String[] names = new String[all.size()];
        long current = ThemeHelper.defaultFolderId(this);
        int checked = 0;
        for (int i = 0; i < all.size(); i++) {
            names[i] = all.get(i).name;
            if (all.get(i).id == current) {
                checked = i;
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ThemeHelper.setDefaultFolderId(SettingsActivity.this, all.get(which).id);
                        refreshValues();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null);
        DialogHelper.create(b, this, "新建笔记默认放进", null).show();
    }

    private void showDefaultBgPicker() {
        String current = ThemeHelper.defaultBg(this);
        int checked = 0;
        for (int i = 0; i < NotePad.Notes.BG_COLORS.length; i++) {
            if (NotePad.Notes.BG_COLORS[i].equalsIgnoreCase(current)) {
                checked = i;
            }
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setSingleChoiceItems(NotePad.Notes.BG_COLOR_NAMES, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ThemeHelper.setDefaultBg(SettingsActivity.this,
                                        NotePad.Notes.BG_COLORS[which]);
                                refreshValues();
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton("取消", null);
        DialogHelper.create(b, this, "新建笔记默认底色", null).show();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
