package com.example.android.notepad.ui;

import com.example.android.notepad.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * 弹窗统一外观：标题条换成主题色（自绘），按钮文字换成同色相的强调色。
 *
 * 起因：Theme.Holo.Light 的 AlertDialog 标题条是系统写死的青蓝渐变 9-patch，
 * 跟七套主题放在一起像两个应用的弹窗。AlertDialog 没有属性可以改这条，
 * 只能用 setCustomTitle 塞一个自己画的标题栏把它顶掉。
 */
public final class DialogHelper {

    private DialogHelper() {
    }

    /**
     * 建一个着了色的弹窗。extra 是调用方自己的 onShow 逻辑（可为 null），
     * 因为 AlertDialog 只能挂一个 OnShowListener，这里统一包一层再转交。
     */
    public static AlertDialog create(AlertDialog.Builder builder, Activity activity,
                                     String title, DialogInterface.OnShowListener extra) {
        final View titleView = activity.getLayoutInflater().inflate(R.layout.dialog_title, null);
        TextView tvTitle = (TextView) titleView.findViewById(R.id.tv_dialog_title);
        tvTitle.setText(title);
        titleView.setBackgroundColor(ThemeHelper.surface(activity));
        builder.setCustomTitle(titleView);

        final AlertDialog dialog = builder.create();

        titleView.findViewById(R.id.btn_dialog_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                // 标题栏底下原本还有一条系统的青蓝色分隔线，跟自绘标题放一起很扎眼，直接藏掉
                int dividerId = activity.getResources().getIdentifier("titleDivider", "id", "android");
                if (dividerId > 0) {
                    View divider = dialog.findViewById(dividerId);
                    if (divider != null) divider.setVisibility(View.GONE);
                }

                int color = ThemeHelper.primary(activity);
                int[] ids = {
                        DialogInterface.BUTTON_POSITIVE,
                        DialogInterface.BUTTON_NEGATIVE,
                        DialogInterface.BUTTON_NEUTRAL
                };
                for (int id : ids) {
                    Button b = dialog.getButton(id);
                    if (b != null) b.setTextColor(color);
                }
                if (extra != null) extra.onShow(d);
            }
        });
        return dialog;
    }

    /** 只有一个「确定 / 取消」的确认框 */
    public static AlertDialog confirm(Activity activity, String title, String message,
                                      String positive, DialogInterface.OnClickListener onPositive) {
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(positive, onPositive)
                .setNegativeButton("取消", null);
        return create(b, activity, title, null);
    }
}
