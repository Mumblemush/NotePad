package com.example.android.notepad.todo;

import com.example.android.notepad.R;
import com.example.android.notepad.ui.DialogHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 待办对话框：新建和编辑共用同一套布局。
 * 内容 + 优先级（浅红紧急/浅黄一般/浅蓝不急）+ 截止时间（日期 + 具体时刻）
 */
public final class TodoDialog {

    private TodoDialog() {
    }

    /**
     * 新建待办
     *
     * @param defaultDueMillis 默认截止时间，0 表示不预设
     * @param onSaved          保存成功后的回调（刷新列表用）
     */
    public static void showAdd(final Activity activity, final TodoDbHelper db,
                               long defaultDueMillis, final Runnable onSaved) {
        show(activity, db, null, defaultDueMillis, onSaved);
    }

    /** 编辑已有待办：标题、优先级、截止时间都能改 */
    public static void showEdit(final Activity activity, final TodoDbHelper db,
                                Todo todo, final Runnable onSaved) {
        show(activity, db, todo, todo.dueTime, onSaved);
    }

    private static void show(final Activity activity, final TodoDbHelper db, final Todo editing,
                             long defaultDueMillis, final Runnable onSaved) {

        final boolean isEdit = (editing != null);
        final View content = activity.getLayoutInflater().inflate(R.layout.dialog_add_todo, null);

        final EditText etTitle = (EditText) content.findViewById(R.id.et_todo_title);
        final TextView tvHigh = (TextView) content.findViewById(R.id.priority_high);
        final TextView tvNormal = (TextView) content.findViewById(R.id.priority_normal);
        final TextView tvLow = (TextView) content.findViewById(R.id.priority_low);
        final TextView tvDue = (TextView) content.findViewById(R.id.tv_todo_due_pick);

        // 编辑时用原来的值，新建时用默认值
        final int[] priority = {isEdit ? editing.priority : 2};
        final long[] dueMillis = {isEdit ? editing.dueTime : defaultDueMillis};

        if (isEdit) {
            etTitle.setText(editing.title);
            etTitle.setSelection(etTitle.getText().length());
        }

        final SimpleDateFormat dateFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        // 刷新三个优先级按钮的选中样式
        final Runnable refreshPriority = new Runnable() {
            @Override
            public void run() {
                tvHigh.setText(priority[0] == 1 ? "紧急 ✓" : "紧急");
                tvNormal.setText(priority[0] == 2 ? "一般 ✓" : "一般");
                tvLow.setText(priority[0] == 3 ? "不急 ✓" : "不急");
                tvHigh.setAlpha(priority[0] == 1 ? 1f : 0.55f);
                tvNormal.setAlpha(priority[0] == 2 ? 1f : 0.55f);
                tvLow.setAlpha(priority[0] == 3 ? 1f : 0.55f);
            }
        };

        // 刷新截止时间文字
        final Runnable refreshDue = new Runnable() {
            @Override
            public void run() {
                if (dueMillis[0] > 0) {
                    tvDue.setText("截止：" + dateFmt.format(new Date(dueMillis[0])) + "（点这里改）");
                } else {
                    tvDue.setText("截止：未设置（点这里选日期和时间）");
                }
            }
        };

        tvHigh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                priority[0] = 1;
                refreshPriority.run();
            }
        });
        tvNormal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                priority[0] = 2;
                refreshPriority.run();
            }
        });
        tvLow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                priority[0] = 3;
                refreshPriority.run();
            }
        });

        tvDue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Calendar cal = Calendar.getInstance();
                if (dueMillis[0] > 0) {
                    cal.setTimeInMillis(dueMillis[0]);
                }
                new DatePickerDialog(activity, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, final int year, final int month, final int dayOfMonth) {
                        // 选完日期接着选"几点几分"
                        Calendar base = Calendar.getInstance();
                        if (dueMillis[0] > 0) base.setTimeInMillis(dueMillis[0]);
                        final int hour = base.get(Calendar.HOUR_OF_DAY);
                        final int minute = base.get(Calendar.MINUTE);

                        // 延迟一点点再弹时间框：日期框还在退出动画里就弹新对话框容易出现窗口重叠报错
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                new TimePickerDialog(activity, new TimePickerDialog.OnTimeSetListener() {
                                    @Override
                                    public void onTimeSet(TimePicker view2, int hourOfDay, int minuteOfHour) {
                                        Calendar pick = Calendar.getInstance();
                                        pick.set(year, month, dayOfMonth, hourOfDay, minuteOfHour, 0);
                                        pick.set(Calendar.MILLISECOND, 0);
                                        dueMillis[0] = pick.getTimeInMillis();
                                        refreshDue.run();
                                    }
                                }, hour, minute, true).show();      // true = 24 小时制
                            }
                        }, 250);
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            }
        });

        refreshPriority.run();
        refreshDue.run();

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setView(content)
                .setPositiveButton(isEdit ? "保存" : "添加", null)
                .setNegativeButton("取消", null);

        // 功能H⑨：编辑框里也给一个「删除」——长按删除太隐蔽，很多人根本试不出来
        if (isEdit) {
            builder.setNeutralButton("删除", null);
        }

        // 标题栏用主题色自绘（Holo 自带那条青蓝渐变跟七套主题不搭）
        final AlertDialog dialog = DialogHelper.create(builder, activity,
                isEdit ? "编辑待办" : "新建待办",
                new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                // d 就是刚 show 出来的这个对话框；用它自己，别引用外面那个 final 变量
                final AlertDialog self = (AlertDialog) d;
                self.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String title = etTitle.getText().toString().trim();
                        if (title.isEmpty()) {
                            Toast.makeText(activity, "待办内容不能为空", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (isEdit) {
                            db.update(editing.id, title, dueMillis[0], priority[0]);
                        } else {
                            db.insert(title, dueMillis[0], priority[0]);
                        }
                        self.dismiss();
                        if (onSaved != null) onSaved.run();
                    }
                });

                if (isEdit) {
                    self.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            DialogHelper.confirm(activity, "删除待办",
                                    "确定删除「" + editing.title + "」吗？删了就没了，不能撤销。",
                                    "删除", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface confirm, int which) {
                                            db.delete(editing.id);
                                            self.dismiss();
                                            if (onSaved != null) onSaved.run();
                                        }
                                    }).show();
                        }
                    });
                }
            }
        });

        dialog.show();
    }
}
