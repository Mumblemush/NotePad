package com.example.android.notepad.todo;

import com.example.android.notepad.R;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 待办列表适配器。
 * 显示顺序：「待完成」分组（没做完、也没过期）→「已过期」分组 →「已完成」分组。
 * 三个分组标题都是插进来的"假行"，不占数据。
 */
public class TodoAdapter extends BaseAdapter {

    public interface OnTodoCheckListener {
        void onCheck(Todo todo, boolean done);
    }

    private static final int TYPE_TODO = 0;
    private static final int TYPE_HEADER = 1;

    private final LayoutInflater inflater;
    private final Context context;
    private final OnTodoCheckListener listener;
    private final SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    /** 一行可能是待办，也可能是分组标题 */
    private static class Row {
        boolean header;
        boolean headerOverdue;
        String headerText;
        boolean overdue;        // 未完成 + 有截止时间 + 已经过了
        Todo todo;
    }

    private List<Row> rows = new ArrayList<>();

    /** true = 待办页那套（插「待完成 / 已过期 / 已完成」分组标题）；false = 日历页"某一天"的列表，不插 */
    private boolean grouped = true;

    public TodoAdapter(Context context, OnTodoCheckListener listener) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    public void setGrouped(boolean grouped) {
        this.grouped = grouped;
    }

    public void setItems(List<Todo> list) {
        long now = System.currentTimeMillis();
        rows = new ArrayList<>();
        boolean pendingHeaderAdded = false;
        boolean overdueHeaderAdded = false;
        boolean doneHeaderAdded = false;

        if (list != null) {
            for (Todo t : list) {
                // 过期只对"还没做完 + 有截止时间"成立
                boolean overdue = !t.done && t.dueTime > 0 && t.dueTime < now;

                // 第一组（没做完、也没逾期）前面插一个「待完成」标题，跟其它分组对齐
                if (grouped && !t.done && !overdue && !pendingHeaderAdded) {
                    rows.add(makeHeader("待完成", false));
                    pendingHeaderAdded = true;
                }
                if (grouped && overdue && !overdueHeaderAdded) {
                    rows.add(makeHeader("已过期", true));
                    overdueHeaderAdded = true;
                }
                if (grouped && t.done && !doneHeaderAdded) {
                    rows.add(makeHeader("已完成", false));
                    doneHeaderAdded = true;
                }

                Row r = new Row();
                r.todo = t;
                r.overdue = overdue;
                rows.add(r);
            }
        }
        notifyDataSetChanged();
    }

    private Row makeHeader(String text, boolean overdue) {
        Row r = new Row();
        r.header = true;
        r.headerText = text;
        r.headerOverdue = overdue;
        return r;
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header ? TYPE_HEADER : TYPE_TODO;
    }

    @Override
    public Todo getItem(int position) {
        return rows.get(position).todo;
    }

    @Override
    public long getItemId(int position) {
        Todo t = rows.get(position).todo;
        return t == null ? -1 : t.id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row = rows.get(position);

        if (row.header) {
            View header = inflater.inflate(R.layout.todo_header_item, parent, false);
            TextView tv = (TextView) header;
            tv.setText(row.headerText);
            if (row.headerOverdue) {
                tv.setTextColor(context.getResources().getColor(R.color.text_overdue));
                tv.setBackgroundColor(context.getResources().getColor(R.color.header_overdue_bg));
            }
            return header;
        }

        View view = convertView;
        if (view == null || view instanceof TextView) {
            view = inflater.inflate(R.layout.todo_item, parent, false);
        }

        final Todo todo = row.todo;
        if (todo == null) return view;

        View bar = view.findViewById(R.id.priority_bar);
        CheckBox cb = (CheckBox) view.findViewById(R.id.cb_done);
        TextView title = (TextView) view.findViewById(R.id.tv_todo_title);
        TextView due = (TextView) view.findViewById(R.id.tv_todo_due);

        // 优先级色条（完成的一律变灰）
        if (todo.done) {
            bar.setBackgroundColor(context.getResources().getColor(R.color.priority_done));
        } else if (todo.priority == 1) {
            bar.setBackgroundColor(context.getResources().getColor(R.color.priority_high));
        } else if (todo.priority == 3) {
            bar.setBackgroundColor(context.getResources().getColor(R.color.priority_low));
        } else {
            bar.setBackgroundColor(context.getResources().getColor(R.color.priority_normal));
        }

        title.setText(todo.title);

        // 标题样式：已完成灰 + 划线；未完成正常
        if (todo.done) {
            title.setTextColor(context.getResources().getColor(R.color.text_done));
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            title.setTextColor(context.getResources().getColor(R.color.text_main));
            title.setPaintFlags(title.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }

        // 截止时间：过期未完成的用红色并标「已过期」
        if (todo.dueTime > 0) {
            String timeText = "截止 " + fmt.format(new Date(todo.dueTime));
            if (row.overdue) {
                due.setText("已过期 · " + timeText);
                due.setTextColor(context.getResources().getColor(R.color.text_overdue));
            } else {
                due.setText(timeText);
                due.setTextColor(context.getResources().getColor(
                        todo.done ? R.color.text_done : R.color.text_hint));
            }
        } else {
            due.setText("无截止时间");
            due.setTextColor(context.getResources().getColor(
                    todo.done ? R.color.text_done : R.color.text_hint));
        }

        // 勾选框：先清监听再设状态，避免复用行误触发
        cb.setOnCheckedChangeListener(null);
        cb.setChecked(todo.done);
        cb.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (listener != null) {
                    listener.onCheck(todo, isChecked);
                }
            }
        });

        return view;
    }
}
