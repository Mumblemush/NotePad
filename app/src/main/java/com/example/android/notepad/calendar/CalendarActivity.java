package com.example.android.notepad.calendar;

import com.example.android.notepad.R;
import com.example.android.notepad.todo.Todo;
import com.example.android.notepad.todo.TodoAdapter;
import com.example.android.notepad.todo.TodoDbHelper;
import com.example.android.notepad.todo.TodoDialog;
import com.example.android.notepad.ui.NavHelper;
import com.example.android.notepad.ui.ThemeHelper;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 日历页：上面是自绘月历（有没做完待办的日期带颜色小圆点），
 * 下面是选中那天的待办，可以直接勾完成；右下角加号在这天加一条。
 */
public class CalendarActivity extends Activity {

    private TodoDbHelper db;
    private MonthGrid monthGrid;
    private TodoAdapter adapter;
    private TextView tvDayTitle;
    private TextView tvEmptyDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);
        NavHelper.bind(this);

        db = new TodoDbHelper(this);
        monthGrid = (MonthGrid) findViewById(R.id.month_grid);
        tvDayTitle = (TextView) findViewById(R.id.tv_day_title);
        tvEmptyDay = (TextView) findViewById(R.id.tv_empty_day);

        // 功能H②：顶栏标题
        ((TextView) findViewById(R.id.tv_top_title)).setText("日历");
        // 功能H⑧：设置挪到底栏「我的」，这里不再挂 ⋮
        ThemeHelper.apply(this);
        // 功能H③：月历里的选中圆、今天的数字、左右箭头也跟主题色
        monthGrid.setAccentColor(ThemeHelper.primary(this));

        adapter = new TodoAdapter(this, new TodoAdapter.OnTodoCheckListener() {
            @Override
            public void onCheck(Todo todo, boolean done) {
                db.setDone(todo.id, done);
                refreshDay();
                loadMarks();
            }
        });
        // 日历页是"某一天"的列表，不需要「待完成 / 已过期」这种分组标题
        adapter.setGrouped(false);

        ListView listView = (ListView) findViewById(R.id.lv_day);
        listView.setAdapter(adapter);

        monthGrid.setOnDaySelectedListener(new MonthGrid.OnDaySelectedListener() {
            @Override
            public void onDaySelected(int year, int month, int dayOfMonth) {
                refreshDay();
            }
        });

        // 翻月以后，圆点要换成新月份那几天的
        monthGrid.setOnMonthChangedListener(new MonthGrid.OnMonthChangedListener() {
            @Override
            public void onMonthChanged(int year, int month) {
                refreshDay();
                loadMarks();
            }
        });

        // 右下角加号：新待办的截止时间默认是选中那天的 23:59:59
        findViewById(R.id.fab_add_on_date).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TodoDialog.showAdd(CalendarActivity.this, db, dueOfSelectedDay(), new Runnable() {
                    @Override
                    public void run() {
                        refreshDay();
                        loadMarks();
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 功能H③：从设置页回来重新上色
        ThemeHelper.apply(this);
        NavHelper.bind(this);
        monthGrid.setAccentColor(ThemeHelper.primary(this));
        if (db != null) {
            refreshDay();
            loadMarks();
        }
    }

    /** 刷新下方"选中这天"的标题和列表 */
    private void refreshDay() {
        long[] range = dayRange();
        List<Todo> dayTodos = db.getByDay(range[0], range[1]);

        Calendar sel = Calendar.getInstance();
        sel.set(monthGrid.getYear(), monthGrid.getMonth(), monthGrid.getSelectedDay());
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault());
        String date = fmt.format(sel.getTime());

        if (dayTodos.isEmpty()) {
            tvDayTitle.setText(date + " · 没有待办");
        } else {
            tvDayTitle.setText(date + " · " + dayTodos.size() + " 条待办");
        }

        adapter.setItems(dayTodos);
        tvEmptyDay.setVisibility(dayTodos.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * 给月历打圆点：这天只要还有没做完的待办就标一个，
     * 颜色按当天最急的那条取（紧急浅红 / 一般浅黄 / 不急浅蓝），跟待办页的三条色条同一套色。
     */
    private void loadMarks() {
        List<Todo> all = db.getAll();
        Map<Integer, Integer> priorities = new HashMap<>();
        Calendar c = Calendar.getInstance();

        for (Todo t : all) {
            if (t.done || t.dueTime <= 0) {
                continue;
            }
            c.setTimeInMillis(t.dueTime);
            if (c.get(Calendar.YEAR) != monthGrid.getYear()
                    || c.get(Calendar.MONTH) != monthGrid.getMonth()) {
                continue;
            }
            int day = c.get(Calendar.DAY_OF_MONTH);
            Integer current = priorities.get(day);
            // priority 1 紧急 / 2 一般 / 3 不急，数字越小越靠前
            if (current == null || t.priority < current) {
                priorities.put(day, t.priority);
            }
        }

        Map<Integer, Integer> colors = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : priorities.entrySet()) {
            colors.put(entry.getKey(), colorOfPriority(entry.getValue()));
        }
        monthGrid.setMarks(colors);
    }

    private int colorOfPriority(int priority) {
        if (priority == 1) {
            return getResources().getColor(R.color.priority_high);
        }
        if (priority == 3) {
            return getResources().getColor(R.color.priority_low);
        }
        return getResources().getColor(R.color.priority_normal);
    }

    /** 选中那天的 00:00:00 ~ 23:59:59 */
    private long[] dayRange() {
        Calendar start = Calendar.getInstance();
        start.set(monthGrid.getYear(), monthGrid.getMonth(), monthGrid.getSelectedDay(), 0, 0, 0);
        start.set(Calendar.MILLISECOND, 0);

        Calendar end = Calendar.getInstance();
        end.set(monthGrid.getYear(), monthGrid.getMonth(), monthGrid.getSelectedDay(), 23, 59, 59);
        end.set(Calendar.MILLISECOND, 999);

        return new long[]{start.getTimeInMillis(), end.getTimeInMillis()};
    }

    private long dueOfSelectedDay() {
        return dayRange()[1];
    }
}
