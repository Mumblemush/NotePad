package com.example.android.notepad.ui;

import com.example.android.notepad.NotesList;
import com.example.android.notepad.R;
import com.example.android.notepad.calendar.CalendarActivity;
import com.example.android.notepad.todo.TodoActivity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

/**
 * 底部导航条的绑定：四个页面共用（笔记 / 待办 / 日历 / 我的）。
 * 点未选中的 tab 就跳过去；当前页的 tab 只高亮、不跳。
 */
public final class NavHelper {

    private NavHelper() {
    }

    public static void bind(final Activity activity) {
        View navNotes = activity.findViewById(R.id.nav_notes);
        View navTodo = activity.findViewById(R.id.nav_todo);
        View navCalendar = activity.findViewById(R.id.nav_calendar);
        View navMe = activity.findViewById(R.id.nav_me);

        if (navNotes != null) {
            navNotes.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!(activity instanceof NotesList)) {
                        open(activity, NotesList.class);
                    }
                }
            });
        }

        if (navTodo != null) {
            navTodo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!(activity instanceof TodoActivity)) {
                        open(activity, TodoActivity.class);
                    }
                }
            });
        }

        if (navCalendar != null) {
            navCalendar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!(activity instanceof CalendarActivity)) {
                        open(activity, CalendarActivity.class);
                    }
                }
            });
        }

        // 功能H⑧：第四个 tab「我的」→ 设置页
        if (navMe != null) {
            navMe.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!(activity instanceof SettingsActivity)) {
                        open(activity, SettingsActivity.class);
                    }
                }
            });
        }

        // 当前页高亮
        int currentId = R.id.nav_notes;
        if (activity instanceof TodoActivity) {
            currentId = R.id.nav_todo;
        } else if (activity instanceof CalendarActivity) {
            currentId = R.id.nav_calendar;
        } else if (activity instanceof SettingsActivity) {
            currentId = R.id.nav_me;
        }
        highlight(activity, currentId);
    }

    private static void highlight(Activity activity, int currentId) {
        int[] ids = {R.id.nav_notes, R.id.nav_todo, R.id.nav_calendar, R.id.nav_me};
        for (int id : ids) {
            TextView tv = (TextView) activity.findViewById(id);
            if (tv == null) continue;
            if (id == currentId) {
                // 功能H③：选中色跟着主题走
                tv.setTextColor(ThemeHelper.primary(activity));
                tv.setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                tv.setTextColor(activity.getResources().getColor(R.color.nav_unselected));
                tv.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    private static void open(Activity from, Class<?> target) {
        Intent intent = new Intent(from, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        from.startActivity(intent);
    }
}
