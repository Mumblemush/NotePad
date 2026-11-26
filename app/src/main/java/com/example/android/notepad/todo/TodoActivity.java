package com.example.android.notepad.todo;

import com.example.android.notepad.R;
import com.example.android.notepad.ui.DialogHelper;
import com.example.android.notepad.ui.NavHelper;
import com.example.android.notepad.ui.ThemeHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * 待办页：新建 / 勾选 / 点击编辑 / 长按删除。
 * 排序：未完成（未过期）→ 已过期 → 已完成，各自带分组标题。
 */
public class TodoActivity extends Activity {

    private TodoDbHelper db;
    private TodoAdapter adapter;
    private ListView lv;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_todo);
        NavHelper.bind(this);

        db = new TodoDbHelper(this);
        lv = (ListView) findViewById(R.id.lv_todo);
        tvEmpty = (TextView) findViewById(R.id.tv_empty_todo);

        // 功能H②：顶栏标题
        ((TextView) findViewById(R.id.tv_top_title)).setText("待办");
        // 功能H⑧：设置挪到底栏「我的」，这里不再挂 ⋮
        ThemeHelper.apply(this);

        adapter = new TodoAdapter(this, new TodoAdapter.OnTodoCheckListener() {
            @Override
            public void onCheck(final Todo todo, final boolean done) {
                db.setDone(todo.id, done);
                // 刷新（勾选后立刻重新排序，做到"移到已完成区"）
                lv.post(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
            }
        });
        lv.setAdapter(adapter);

        // 点一行 = 编辑这条待办（改内容 / 优先级 / 截止时间）
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Todo todo = adapter.getItem(position);
                if (todo == null) return;          // 分组标题行不算
                TodoDialog.showEdit(TodoActivity.this, db, todo, new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
            }
        });

        // 长按删除
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                final Todo todo = adapter.getItem(position);
                if (todo == null) {
                    return false;
                }
                DialogHelper.confirm(TodoActivity.this, "删除待办",
                        "确定删除「" + todo.title + "」吗？",
                        "删除", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                db.delete(todo.id);
                                refresh();
                            }
                        }).show();
                return true;
            }
        });

        // 功能H①：待办页也换成右下角圆形按钮
        View btnAdd = findViewById(R.id.fab_add_todo);
        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TodoDialog.showAdd(TodoActivity.this, db, 0, new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
            }
        });

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 功能H③：从设置页回来重新上色
        ThemeHelper.apply(this);
        NavHelper.bind(this);
        // 从日历页添加完待办回来时刷新
        if (db != null) {
            refresh();
        }
    }

    private void refresh() {
        List<Todo> list = db.getAll();
        adapter.setItems(list);
        // 空列表显示提示，但列表本身保持占位，底部导航才不会浮到中间
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
