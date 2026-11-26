package com.example.android.notepad.calendar;

import com.example.android.notepad.R;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * 功能H⑤：自绘月历。
 *
 * 为什么不用系统 CalendarView：它没有任何接口能给"某一天"加标记，也没法把月份显示成中文。
 *
 * 每月天数和 1 号是星期几都从 Calendar 现算（getActualMaximum / get(DAY_OF_WEEK)），
 * 闰年、大小月、跨年翻页都由系统日历规则保证，这里不写死任何一张月表。
 */
public class MonthGrid extends LinearLayout {

    /** 点了某一天 */
    public interface OnDaySelectedListener {
        void onDaySelected(int year, int month, int dayOfMonth);
    }

    /** 点了上一月 / 下一月 */
    public interface OnMonthChangedListener {
        void onMonthChanged(int year, int month);
    }

    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final String[] WEEK_LABELS = {"一", "二", "三", "四", "五", "六", "日"};

    private TextView tvMonthTitle;
    private TextView tvPrev;
    private TextView tvNext;
    private final TextView[][] nums = new TextView[ROWS][COLS];
    private final View[][] boxes = new View[ROWS][COLS];
    private final View[][] dots = new View[ROWS][COLS];

    /** 功能H③：跟着主题走的主色（选中圆、今天、左右箭头都用它） */
    private int accentColor;

    private int year;
    private int month;          // 0 = 一月
    private int selectedDay = 1;
    /** 日期 → 小圆点颜色（只有"这天还有没做完的待办"才放进来） */
    private final Map<Integer, Integer> markColors = new HashMap<>();

    private OnDaySelectedListener dayListener;
    private OnMonthChangedListener monthListener;

    public MonthGrid(Context context) {
        super(context);
        init(context);
    }

    public MonthGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MonthGrid(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getSelectedDay() {
        return selectedDay;
    }

    public void setOnDaySelectedListener(OnDaySelectedListener l) {
        dayListener = l;
    }

    public void setOnMonthChangedListener(OnMonthChangedListener l) {
        monthListener = l;
    }

    /** 设置标记：key 是几号，value 是圆点颜色 */
    public void setMarks(Map<Integer, Integer> marks) {
        markColors.clear();
        if (marks != null) {
            markColors.putAll(marks);
        }
        refresh();
    }

    /** 外部改了选中日期（比如初始化为今天） */
    public void setSelectedDay(int day) {
        selectedDay = day;
        refresh();
    }

    // ================= 搭建结构（只做一次） =================

    private void init(Context context) {
        setOrientation(VERTICAL);
        setBackgroundColor(Color.WHITE);

        accentColor = context.getResources().getColor(R.color.app_primary);

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);
        month = now.get(Calendar.MONTH);
        selectedDay = now.get(Calendar.DAY_OF_MONTH);

        addView(buildHeader(context));
        addView(buildWeekRow(context));
        for (int r = 0; r < ROWS; r++) {
            addView(buildCellRow(context, r));
        }

        refresh();
    }

    /** 月份头：「‹  2026年11月  ›」 */
    private View buildHeader(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(4));

        tvPrev = arrow(context, "‹");
        tvPrev.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                changeMonth(-1);
            }
        });

        tvMonthTitle = new TextView(context);
        tvMonthTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvMonthTitle.setTextColor(context.getResources().getColor(R.color.text_main));
        tvMonthTitle.setGravity(Gravity.CENTER);
        tvMonthTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        tvNext = arrow(context, "›");
        tvNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                changeMonth(1);
            }
        });

        row.addView(tvPrev, new LinearLayout.LayoutParams(dp(40), dp(40)));
        row.addView(tvMonthTitle, new LinearLayout.LayoutParams(0, dp(40), 1));
        row.addView(tvNext, new LinearLayout.LayoutParams(dp(40), dp(40)));
        return row;
    }

    /** 功能H③：换主题时由外面调一次 */
    public void setAccentColor(int color) {
        accentColor = color;
        if (tvPrev != null) {
            tvPrev.setTextColor(color);
        }
        if (tvNext != null) {
            tvNext.setTextColor(color);
        }
        refresh();
    }

    private TextView arrow(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(accentColor);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    /** 一 二 三 四 五 六 日 */
    private View buildWeekRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setPadding(dp(4), 0, dp(4), dp(2));
        for (int i = 0; i < COLS; i++) {
            TextView tv = new TextView(context);
            tv.setText(WEEK_LABELS[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTextColor(context.getResources().getColor(R.color.text_hint));
            tv.setGravity(Gravity.CENTER);
            row.addView(tv, new LinearLayout.LayoutParams(0, dp(24), 1));
        }
        return row;
    }

    private View buildCellRow(Context context, int row) {
        LinearLayout line = new LinearLayout(context);
        line.setOrientation(HORIZONTAL);
        line.setPadding(dp(4), 0, dp(4), 0);

        for (int col = 0; col < COLS; col++) {
            LinearLayout box = new LinearLayout(context);
            box.setOrientation(VERTICAL);
            box.setGravity(Gravity.CENTER);

            TextView num = new TextView(context);
            num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            num.setGravity(Gravity.CENTER);
            box.addView(num, new LinearLayout.LayoutParams(dp(32), dp(32)));

            View dot = new View(context);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(5), dp(5));
            dotLp.topMargin = dp(3);
            dotLp.gravity = Gravity.CENTER_HORIZONTAL;
            dot.setVisibility(INVISIBLE);
            box.addView(dot, dotLp);

            line.addView(box, new LinearLayout.LayoutParams(0, dp(44), 1));

            boxes[row][col] = box;
            nums[row][col] = num;
            dots[row][col] = dot;
        }
        return line;
    }

    // ================= 每次数据变化只更新内容 =================

    private void refresh() {
        Calendar first = Calendar.getInstance();
        first.set(year, month, 1);

        // 周一开头：DAY_OF_WEEK 里周日=1、周一=2，换算成 0~6
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);

        tvMonthTitle.setText(year + "年" + (month + 1) + "月");

        Calendar now = Calendar.getInstance();
        boolean sameMonthAsToday = now.get(Calendar.YEAR) == year
                && now.get(Calendar.MONTH) == month;

        int day = 1;
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                int index = r * COLS + col;
                TextView num = nums[r][col];
                View box = boxes[r][col];
                View dot = dots[r][col];

                if (index < offset || day > daysInMonth) {
                    // 这个格子不属于本月
                    num.setText("");
                    num.setBackground(null);
                    dot.setVisibility(INVISIBLE);
                    box.setTag(null);
                    box.setOnClickListener(null);
                    box.setClickable(false);
                    continue;
                }

                final int d = day;
                num.setText(String.valueOf(d));

                int textColor = getResources().getColor(R.color.text_main);
                if (sameMonthAsToday && d == now.get(Calendar.DAY_OF_MONTH)) {
                    textColor = accentColor;
                }

                if (d == selectedDay) {
                    // 选中的那天：实心圆 + 白字
                    num.setBackground(circle(accentColor));
                    textColor = Color.WHITE;
                } else {
                    num.setBackground(null);
                }
                num.setTextColor(textColor);

                Integer mark = markColors.get(d);
                if (mark != null) {
                    dot.setVisibility(VISIBLE);
                    dot.setBackground(circle(mark));
                } else {
                    dot.setVisibility(INVISIBLE);
                }

                box.setTag(d);
                box.setClickable(true);
                box.setOnClickListener(cellClick);
                day++;
            }
        }
    }

    private final OnClickListener cellClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Object tag = v.getTag();
            if (tag == null) {
                return;
            }
            selectedDay = (Integer) tag;
            refresh();
            if (dayListener != null) {
                dayListener.onDaySelected(year, month, selectedDay);
            }
        }
    };

    private void changeMonth(int delta) {
        int y = year;
        int m = month + delta;
        if (m < 0) {
            m = 11;
            y--;
        } else if (m > 11) {
            m = 0;
            y++;
        }
        year = y;
        month = m;

        // 换月后选中日可能超范围（比如从 31 号换到 2 月）
        Calendar c = Calendar.getInstance();
        c.set(year, month, 1);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        if (selectedDay > max) {
            selectedDay = max;
        }
        refresh();
        if (monthListener != null) {
            monthListener.onMonthChanged(year, month);
        }
    }

    // ================= 小工具 =================

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
