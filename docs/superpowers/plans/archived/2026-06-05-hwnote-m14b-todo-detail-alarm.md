# M14b 待办详情页 + 时间选择器 + 提醒通知 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现待办详情编辑页面（TodoDetailActivity）、自定义 4 列滚轮时间选择器（DateTimePickerDialog）、重复选择器（RepeatPickerBottomSheet）和 AlarmManager 精确闹钟提醒通知系统。

**Architecture:** TodoDetailActivity 采用与 NoteEditorActivity 相同的 onPause 自动保存模式。时间选择器用 4 列 NumberPicker（日期/上午下午/小时/分钟）实现华为风格滚轮。通知系统由 TodoAlarmManager（object 工具类）+ TodoAlarmReceiver（BroadcastReceiver）+ TodoBootReceiver 三件套组成，通过 AlarmManager.setExactAndAllowWhileIdle 实现精确闹钟。

**Tech Stack:** Kotlin / XML View / NumberPicker / BottomSheetDialog / AlarmManager / NotificationManager / BroadcastReceiver

---

## 文件结构

| 文件 | 职责 | 操作 |
|------|------|------|
| `res/values/strings.xml` | M14b 新增字符串 | 修改 |
| `res/drawable/ic_bell.xml` | 提醒铃铛图标 | 新增 |
| `res/drawable/ic_repeat.xml` | 重复图标 | 新增 |
| `res/drawable/ic_notes.xml` | 备注图标 | 新增 |
| `res/drawable/ic_clear.xml` | 清除 ✕ 图标 | 新增 |
| `res/layout/activity_todo_detail.xml` | 详情页主布局 | 新增 |
| `res/layout/dialog_datetime_picker.xml` | 时间选择器布局 | 新增 |
| `res/layout/dialog_repeat_picker.xml` | 重复选择器布局 | 新增 |
| `view/picker/DateTimePickerDialog.kt` | 4 列 NumberPicker 时间选择器 | 新增 |
| `view/picker/RepeatPickerBottomSheet.kt` | 重复类型单选列表 | 新增 |
| `model/alarm/TodoAlarmManager.kt` | AlarmManager 精确闹钟调度 | 新增 |
| `controller/todo/TodoAlarmReceiver.kt` | BroadcastReceiver 通知/完成/贪睡 | 新增 |
| `controller/todo/TodoBootReceiver.kt` | 开机恢复闹钟 | 新增 |
| `controller/todo/TodoDetailActivity.kt` | 详情页 Activity | 新增 |
| `controller/list/TodoListFragment.kt` | 接通详情页跳转 + QuickAddBar 时间 | 修改 |
| `App.kt` | 创建 NotificationChannel | 修改 |
| `AndroidManifest.xml` | 权限 + receiver + activity 声明 | 修改 |

---

## Task 1：资源文件 — strings + drawables

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/res/values/strings.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_bell.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_repeat.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_notes.xml`
- Create: `code/HuaWeiNote/app/src/main/res/drawable/ic_clear.xml`

- [ ] **Step 1: 在 strings.xml 末尾 `</resources>` 前追加 M14b 字符串**

```xml
    <!-- M14b: 待办详情 -->
    <string name="todo_detail_title_hint">待办事项</string>
    <string name="todo_detail_memo_hint">添加备注</string>
    <string name="todo_detail_add_remind">添加提醒</string>
    <string name="todo_detail_repeat">重复</string>
    <string name="todo_detail_no_repeat">不重复</string>
    <string name="todo_detail_important">重要</string>
    <string name="todo_detail_memo_label">备注</string>
    <string name="todo_detail_share">分享</string>
    <string name="todo_detail_delete">删除</string>
    <string name="todo_detail_folder_none">未分类</string>
    <string name="todo_remind_format">%1$s %2$s%3$d:%4$02d</string>
    <string name="todo_remind_today_format">今天 %1$s%2$d:%3$02d</string>
    <string name="todo_delete_title">删除待办</string>
    <string name="todo_delete_message">该待办将移入"最近删除"，30 天后自动清理。</string>
    <string name="todo_save_failed">待办保存失败</string>
    <string name="toast_todo_share_placeholder">分享功能（待开发）</string>

    <!-- M14b: 时间选择器 -->
    <string name="picker_datetime_today">今天</string>
    <string name="picker_datetime_am">上午</string>
    <string name="picker_datetime_pm">下午</string>
    <string name="picker_datetime_confirm">确定</string>
    <string name="picker_datetime_cancel">取消</string>
    <string name="picker_datetime_title_format">%1$d年%2$d月%3$d日%4$s</string>

    <!-- M14b: 重复选择器 -->
    <string name="picker_repeat_title">重复</string>
    <string name="picker_repeat_none">不重复</string>

    <!-- M14b: 通知 -->
    <string name="notification_channel_todo">待办提醒</string>
    <string name="notification_todo_title">备忘录</string>
    <string name="notification_action_complete">标记完成</string>
    <string name="notification_action_snooze">10分钟后提醒</string>
    <string name="alarm_permission_title">需要闹钟权限</string>
    <string name="alarm_permission_message">请在系统设置中允许「精确闹钟」权限以设置待办提醒。</string>
    <string name="notification_permission_title">需要通知权限</string>
    <string name="notification_permission_message">请允许通知权限以接收待办提醒。</string>
```

- [ ] **Step 2: 创建 ic_bell.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM18,16v-5c0,-3.07 -1.63,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.64,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"/>
</vector>
```

- [ ] **Step 3: 创建 ic_repeat.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z"/>
</vector>
```

- [ ] **Step 4: 创建 ic_notes.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M3,18h12v-2H3v2zM3,6v2h18V6H3zM3,13h18v-2H3v2z"/>
</vector>
```

- [ ] **Step 5: 创建 ic_clear.xml**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M19,6.41L17.59,5 12,10.59 6.41,5 5,6.41 10.59,12 5,17.59 6.41,19 12,13.41 17.59,19 19,17.59 13.41,12z"/>
</vector>
```

- [ ] **Step 6: Commit**

```bash
git add code/HuaWeiNote/app/src/main/res/values/strings.xml \
  code/HuaWeiNote/app/src/main/res/drawable/ic_bell.xml \
  code/HuaWeiNote/app/src/main/res/drawable/ic_repeat.xml \
  code/HuaWeiNote/app/src/main/res/drawable/ic_notes.xml \
  code/HuaWeiNote/app/src/main/res/drawable/ic_clear.xml
git commit -m "feat(m14b): 加待办详情/时间选择器/通知资源(strings+drawables)"
```

---

## Task 2：布局文件 — activity_todo_detail.xml + dialog_datetime_picker.xml + dialog_repeat_picker.xml

**Files:**
- Create: `code/HuaWeiNote/app/src/main/res/layout/activity_todo_detail.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_datetime_picker.xml`
- Create: `code/HuaWeiNote/app/src/main/res/layout/dialog_repeat_picker.xml`

- [ ] **Step 1: 创建 activity_todo_detail.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@android:color/white"
    android:orientation="vertical">

    <!-- 顶栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="4dp"
        android:paddingEnd="16dp">

        <ImageButton
            android:id="@+id/btn_back"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/editor_back_cd"
            android:src="@drawable/ic_arrow_back" />
    </LinearLayout>

    <!-- 内容区 -->
    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- 文件夹指示器 -->
            <TextView
                android:id="@+id/folder_indicator"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:drawablePadding="4dp"
                android:paddingBottom="12dp"
                android:text="@string/todo_detail_folder_none"
                android:textColor="@color/text_hint"
                android:textSize="13sp" />

            <!-- 标题行 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <CheckBox
                    android:id="@+id/check_complete"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="12dp"
                    android:button="@null"
                    android:background="@drawable/selector_todo_checkbox" />

                <EditText
                    android:id="@+id/input_title"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:background="@null"
                    android:hint="@string/todo_detail_title_hint"
                    android:inputType="text"
                    android:maxLines="1"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:layout_marginTop="12dp"
                android:layout_marginBottom="4dp"
                android:background="@color/divider" />

            <!-- 提醒行 -->
            <LinearLayout
                android:id="@+id/row_remind"
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackground"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="12dp"
                    android:src="@drawable/ic_bell"
                    android:tint="@color/text_hint" />

                <TextView
                    android:id="@+id/tv_remind"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/todo_detail_add_remind"
                    android:textColor="@color/text_hint"
                    android:textSize="15sp" />

                <ImageView
                    android:id="@+id/btn_clear_remind"
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:src="@drawable/ic_clear"
                    android:tint="@color/text_hint"
                    android:visibility="gone" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="@color/divider" />

            <!-- 重复行 -->
            <LinearLayout
                android:id="@+id/row_repeat"
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:background="?attr/selectableItemBackground"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="12dp"
                    android:src="@drawable/ic_repeat"
                    android:tint="@color/text_hint" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/todo_detail_repeat"
                    android:textColor="@color/text_primary"
                    android:textSize="15sp" />

                <TextView
                    android:id="@+id/tv_repeat_value"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/todo_detail_no_repeat"
                    android:textColor="@color/text_hint"
                    android:textSize="14sp" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="@color/divider" />

            <!-- 重要行 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="48dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="12dp"
                    android:src="@drawable/ic_important"
                    android:tint="@color/text_hint" />

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/todo_detail_important"
                    android:textColor="@color/text_primary"
                    android:textSize="15sp" />

                <Switch
                    android:id="@+id/switch_important"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="@color/divider" />

            <!-- 备注区 -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:paddingTop="12dp">

                <ImageView
                    android:layout_width="24dp"
                    android:layout_height="24dp"
                    android:layout_marginEnd="12dp"
                    android:layout_marginTop="2dp"
                    android:src="@drawable/ic_notes"
                    android:tint="@color/text_hint" />

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/todo_detail_memo_label"
                        android:textColor="@color/text_primary"
                        android:textSize="15sp" />

                    <EditText
                        android:id="@+id/input_memo"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:background="@null"
                        android:gravity="top"
                        android:hint="@string/todo_detail_memo_hint"
                        android:inputType="textMultiLine"
                        android:minHeight="100dp"
                        android:textColor="@color/text_primary"
                        android:textSize="14sp" />
                </LinearLayout>
            </LinearLayout>
        </LinearLayout>
    </ScrollView>

    <!-- 底部动作栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="@color/surface"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <LinearLayout
            android:id="@+id/btn_share"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="?attr/selectableItemBackground"
            android:gravity="center"
            android:orientation="horizontal">

            <ImageView
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:layout_marginEnd="4dp"
                android:src="@drawable/ic_share"
                android:tint="@color/text_primary" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/todo_detail_share"
                android:textColor="@color/text_primary"
                android:textSize="13sp" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/btn_delete"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:background="?attr/selectableItemBackground"
            android:gravity="center"
            android:orientation="horizontal">

            <ImageView
                android:layout_width="20dp"
                android:layout_height="20dp"
                android:layout_marginEnd="4dp"
                android:src="@drawable/ic_delete"
                android:tint="#E53935" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/todo_detail_delete"
                android:textColor="#E53935"
                android:textSize="13sp" />
        </LinearLayout>
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 2: 创建 dialog_datetime_picker.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_bottom_sheet"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/tv_date_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:paddingBottom="12dp"
        android:textColor="@color/text_primary"
        android:textSize="16sp"
        android:textStyle="bold" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:orientation="horizontal">

        <NumberPicker
            android:id="@+id/picker_date"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="2" />

        <NumberPicker
            android:id="@+id/picker_ampm"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1" />

        <NumberPicker
            android:id="@+id/picker_hour"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1" />

        <NumberPicker
            android:id="@+id/picker_minute"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:gravity="center"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/btn_cancel"
            android:layout_width="0dp"
            android:layout_height="44dp"
            android:layout_marginEnd="8dp"
            android:layout_weight="1"
            android:background="?attr/selectableItemBackground"
            android:gravity="center"
            android:text="@string/picker_datetime_cancel"
            android:textColor="@color/primary"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/btn_confirm"
            android:layout_width="0dp"
            android:layout_height="44dp"
            android:layout_marginStart="8dp"
            android:layout_weight="1"
            android:background="?attr/selectableItemBackground"
            android:gravity="center"
            android:text="@string/picker_datetime_confirm"
            android:textColor="@color/primary"
            android:textSize="15sp" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 3: 创建 dialog_repeat_picker.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_bottom_sheet"
    android:orientation="vertical"
    android:paddingTop="16dp"
    android:paddingBottom="8dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:paddingBottom="8dp"
        android:text="@string/picker_repeat_title"
        android:textColor="@color/text_primary"
        android:textSize="18sp"
        android:textStyle="bold" />

    <RadioGroup
        android:id="@+id/radio_group"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <RadioButton
            android:id="@+id/rb_none"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:text="@string/picker_repeat_none"
            android:textColor="@color/text_primary"
            android:textSize="15sp" />

        <RadioButton
            android:id="@+id/rb_daily"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:text="@string/todo_repeat_daily"
            android:textColor="@color/text_primary"
            android:textSize="15sp" />

        <RadioButton
            android:id="@+id/rb_weekly"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:text="@string/todo_repeat_weekly"
            android:textColor="@color/text_primary"
            android:textSize="15sp" />

        <RadioButton
            android:id="@+id/rb_monthly"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:text="@string/todo_repeat_monthly"
            android:textColor="@color/text_primary"
            android:textSize="15sp" />

        <RadioButton
            android:id="@+id/rb_yearly"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:paddingStart="16dp"
            android:paddingEnd="16dp"
            android:text="@string/todo_repeat_yearly"
            android:textColor="@color/text_primary"
            android:textSize="15sp" />
    </RadioGroup>

    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:layout_marginTop="4dp"
        android:background="@color/divider" />

    <TextView
        android:id="@+id/btn_cancel"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="?attr/selectableItemBackground"
        android:gravity="center"
        android:text="@string/action_cancel"
        android:textColor="@color/primary"
        android:textSize="15sp" />
</LinearLayout>
```

- [ ] **Step 4: Commit**

```bash
git add code/HuaWeiNote/app/src/main/res/layout/activity_todo_detail.xml \
  code/HuaWeiNote/app/src/main/res/layout/dialog_datetime_picker.xml \
  code/HuaWeiNote/app/src/main/res/layout/dialog_repeat_picker.xml
git commit -m "feat(m14b): 加待办详情页/时间选择器/重复选择器布局"
```

---

## Task 3：DateTimePickerDialog — 4 列滚轮时间选择器

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/picker/DateTimePickerDialog.kt`

- [ ] **Step 1: 创建 DateTimePickerDialog.kt**

BottomSheetDialog 内嵌 4 列 NumberPicker（日期/上午下午/小时/分钟）。

```kotlin
package com.fan.hwnote.app.view.picker

import android.content.Context
import android.view.LayoutInflater
import android.widget.NumberPicker
import android.widget.TextView
import com.fan.hwnote.app.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DateTimePickerDialog(
    context: Context,
    private val initialEpoch: Long,
    private val onDateTimeSelected: (Long) -> Unit,
) : BottomSheetDialog(context) {

    private val baseCalendar = Calendar.getInstance()
    private val dateStrings = mutableListOf<String>()
    private val dateCalendars = mutableListOf<Calendar>()
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.CHINESE)

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_datetime_picker, null)
        setContentView(view)

        val tvTitle = view.findViewById<TextView>(R.id.tv_date_title)
        val pickerDate = view.findViewById<NumberPicker>(R.id.picker_date)
        val pickerAmPm = view.findViewById<NumberPicker>(R.id.picker_ampm)
        val pickerHour = view.findViewById<NumberPicker>(R.id.picker_hour)
        val pickerMinute = view.findViewById<NumberPicker>(R.id.picker_minute)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)
        val btnConfirm = view.findViewById<TextView>(R.id.btn_confirm)

        buildDateList()

        val initCal = Calendar.getInstance()
        if (initialEpoch > 0) {
            initCal.timeInMillis = initialEpoch
        } else {
            roundToNext5Minutes(initCal)
        }

        // 日期列
        pickerDate.minValue = 0
        pickerDate.maxValue = dateStrings.size - 1
        pickerDate.displayedValues = dateStrings.toTypedArray()
        pickerDate.wrapSelectorWheel = false
        pickerDate.value = findDateIndex(initCal)

        // 上午/下午列
        pickerAmPm.minValue = 0
        pickerAmPm.maxValue = 1
        pickerAmPm.displayedValues = arrayOf(
            context.getString(R.string.picker_datetime_am),
            context.getString(R.string.picker_datetime_pm),
        )
        pickerAmPm.wrapSelectorWheel = false
        pickerAmPm.value = if (initCal.get(Calendar.AM_PM) == Calendar.AM) 0 else 1

        // 小时列 (1-12)
        pickerHour.minValue = 1
        pickerHour.maxValue = 12
        pickerHour.wrapSelectorWheel = true
        val h = initCal.get(Calendar.HOUR)
        pickerHour.value = if (h == 0) 12 else h

        // 分钟列 (00-59)
        pickerMinute.minValue = 0
        pickerMinute.maxValue = 59
        pickerMinute.setFormatter { String.format("%02d", it) }
        pickerMinute.wrapSelectorWheel = true
        pickerMinute.value = initCal.get(Calendar.MINUTE)

        updateTitle(tvTitle, pickerDate.value)

        pickerDate.setOnValueChangedListener { _, _, newVal ->
            updateTitle(tvTitle, newVal)
        }

        btnCancel.setOnClickListener { dismiss() }

        btnConfirm.setOnClickListener {
            val cal = dateCalendars[pickerDate.value].clone() as Calendar
            val amPm = if (pickerAmPm.value == 0) Calendar.AM else Calendar.PM
            var hour = pickerHour.value
            if (hour == 12) hour = 0
            cal.set(Calendar.AM_PM, amPm)
            cal.set(Calendar.HOUR, hour)
            cal.set(Calendar.MINUTE, pickerMinute.value)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onDateTimeSelected(cal.timeInMillis)
            dismiss()
        }
    }

    private fun buildDateList() {
        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        for (i in 0 until 365) {
            val day = today.clone() as Calendar
            day.add(Calendar.DAY_OF_MONTH, i)
            dateCalendars += day
            dateStrings += if (i == 0) {
                context.getString(R.string.picker_datetime_today)
            } else {
                "${day.get(Calendar.MONTH) + 1}月${day.get(Calendar.DAY_OF_MONTH)}日"
            }
        }
    }

    private fun findDateIndex(cal: Calendar): Int {
        val target = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        for (i in dateCalendars.indices) {
            if (dateCalendars[i].get(Calendar.YEAR) == target.get(Calendar.YEAR)
                && dateCalendars[i].get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
            ) return i
        }
        return 0
    }

    private fun updateTitle(tv: TextView, dateIndex: Int) {
        val cal = dateCalendars[dateIndex]
        val dow = dayOfWeekFormat.format(cal.time)
        tv.text = context.getString(
            R.string.picker_datetime_title_format,
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            dow,
        )
    }

    companion object {
        fun roundToNext5Minutes(cal: Calendar) {
            val min = cal.get(Calendar.MINUTE)
            val remainder = min % 5
            if (remainder != 0) {
                cal.add(Calendar.MINUTE, 5 - remainder)
            } else {
                cal.add(Calendar.MINUTE, 5)
            }
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/picker/DateTimePickerDialog.kt
git commit -m "feat(m14b): 加 DateTimePickerDialog 4列滚轮时间选择器"
```

---

## Task 4：RepeatPickerBottomSheet — 重复类型选择器

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/picker/RepeatPickerBottomSheet.kt`

- [ ] **Step 1: 创建 RepeatPickerBottomSheet.kt**

```kotlin
package com.fan.hwnote.app.view.picker

import android.content.Context
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.entity.RepeatType
import com.google.android.material.bottomsheet.BottomSheetDialog

class RepeatPickerBottomSheet(
    context: Context,
    private val current: RepeatType,
    private val onRepeatSelected: (RepeatType) -> Unit,
) : BottomSheetDialog(context) {

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_repeat_picker, null)
        setContentView(view)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group)
        val btnCancel = view.findViewById<TextView>(R.id.btn_cancel)

        val checkedId = when (current) {
            RepeatType.NONE -> R.id.rb_none
            RepeatType.DAILY -> R.id.rb_daily
            RepeatType.WEEKLY -> R.id.rb_weekly
            RepeatType.MONTHLY -> R.id.rb_monthly
            RepeatType.YEARLY -> R.id.rb_yearly
        }
        radioGroup.check(checkedId)

        radioGroup.setOnCheckedChangeListener { _, id ->
            val type = when (id) {
                R.id.rb_none -> RepeatType.NONE
                R.id.rb_daily -> RepeatType.DAILY
                R.id.rb_weekly -> RepeatType.WEEKLY
                R.id.rb_monthly -> RepeatType.MONTHLY
                R.id.rb_yearly -> RepeatType.YEARLY
                else -> RepeatType.NONE
            }
            onRepeatSelected(type)
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/view/picker/RepeatPickerBottomSheet.kt
git commit -m "feat(m14b): 加 RepeatPickerBottomSheet 重复类型选择器"
```

---

## Task 5：TodoAlarmManager + App NotificationChannel

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/alarm/TodoAlarmManager.kt`
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/App.kt`

- [ ] **Step 1: 创建 TodoAlarmManager.kt**

```kotlin
package com.fan.hwnote.app.model.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.fan.hwnote.app.model.entity.Todo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TodoAlarmManager {

    const val ACTION_REMIND = "com.fan.hwnote.ACTION_TODO_REMIND"
    const val ACTION_COMPLETE = "com.fan.hwnote.ACTION_TODO_COMPLETE"
    const val ACTION_SNOOZE = "com.fan.hwnote.ACTION_TODO_SNOOZE"
    const val EXTRA_TODO_ID = "todo_id"
    const val EXTRA_TODO_TITLE = "todo_title"
    const val CHANNEL_ID = "todo_reminders"

    fun scheduleAlarm(context: Context, todo: Todo) {
        if (todo.remindAt <= 0 || todo.isCompleted || todo.deletedAt > 0) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            return
        }

        val intent = Intent(ACTION_REMIND).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_TODO_ID, todo.id)
            putExtra(EXTRA_TODO_TITLE, todo.title)
        }
        val pi = PendingIntent.getBroadcast(
            context, todo.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, todo.remindAt, pi)
    }

    fun cancelAlarm(context: Context, todoId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_REMIND).apply {
            setPackage(context.packageName)
        }
        val pi = PendingIntent.getBroadcast(
            context, todoId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
    }

    fun scheduleSnooze(context: Context, todoId: Long, todoTitle: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_REMIND).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_TODO_TITLE, todoTitle)
        }
        val pi = PendingIntent.getBroadcast(
            context, todoId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000L
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, snoozeTime, pi)
    }

    suspend fun rescheduleAll(context: Context) = withContext(Dispatchers.IO) {
        val repo = com.fan.hwnote.app.model.TodoRepository
        val todos = repo.listPendingAlarms()
        for (todo in todos) {
            if (todo.remindAt > System.currentTimeMillis()) {
                scheduleAlarm(context, todo)
            }
        }
    }
}
```

- [ ] **Step 2: 修改 App.kt — 在 onCreate 中创建 NotificationChannel**

在 `App.kt` 的 import 区追加：
```kotlin
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
```

在 `onCreate()` 中 `GlobalScope.launch` 前追加：
```kotlin
        createNotificationChannel()
```

在 class 内、`companion object` 前追加：
```kotlin
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TodoAlarmManager.CHANNEL_ID,
                getString(R.string.notification_channel_todo),
                NotificationManager.IMPORTANCE_HIGH,
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/model/alarm/TodoAlarmManager.kt \
  code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/App.kt
git commit -m "feat(m14b): 加 TodoAlarmManager 闹钟调度 + App 创建 NotificationChannel"
```

---

## Task 6：TodoAlarmReceiver — 通知/完成/贪睡

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/todo/TodoAlarmReceiver.kt`

- [ ] **Step 1: 创建 TodoAlarmReceiver.kt**

```kotlin
package com.fan.hwnote.app.controller.todo

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.TodoRepository
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TodoAlarmReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(TodoAlarmManager.EXTRA_TODO_ID, -1L)
        val todoTitle = intent.getStringExtra(TodoAlarmManager.EXTRA_TODO_TITLE) ?: ""
        if (todoId <= 0L) return

        when (intent.action) {
            TodoAlarmManager.ACTION_REMIND -> showNotification(context, todoId, todoTitle)

            TodoAlarmManager.ACTION_COMPLETE -> {
                val pending = goAsync()
                GlobalScope.launch(Dispatchers.IO) {
                    runCatching { TodoRepository.completeTodo(todoId) }
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager
                    nm.cancel(todoId.toInt())
                    pending.finish()
                }
            }

            TodoAlarmManager.ACTION_SNOOZE -> {
                TodoAlarmManager.scheduleSnooze(context, todoId, todoTitle)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
                nm.cancel(todoId.toInt())
            }
        }
    }

    private fun showNotification(context: Context, todoId: Long, todoTitle: String) {
        val tapIntent = Intent(context, TodoDetailActivity::class.java).apply {
            putExtra(TodoDetailActivity.EXTRA_TODO_ID, todoId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPi = PendingIntent.getActivity(
            context, todoId.toInt(), tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val completeIntent = Intent(TodoAlarmManager.ACTION_COMPLETE).apply {
            setPackage(context.packageName)
            putExtra(TodoAlarmManager.EXTRA_TODO_ID, todoId)
            putExtra(TodoAlarmManager.EXTRA_TODO_TITLE, todoTitle)
        }
        val completePi = PendingIntent.getBroadcast(
            context, (todoId.toInt() * 10 + 1), completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(TodoAlarmManager.ACTION_SNOOZE).apply {
            setPackage(context.packageName)
            putExtra(TodoAlarmManager.EXTRA_TODO_ID, todoId)
            putExtra(TodoAlarmManager.EXTRA_TODO_TITLE, todoTitle)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context, (todoId.toInt() * 10 + 2), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, TodoAlarmManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(context.getString(R.string.notification_todo_title))
            .setContentText(todoTitle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(tapPi)
            .addAction(0, context.getString(R.string.notification_action_complete), completePi)
            .addAction(0, context.getString(R.string.notification_action_snooze), snoozePi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(todoId.toInt(), notification)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/todo/TodoAlarmReceiver.kt
git commit -m "feat(m14b): 加 TodoAlarmReceiver 通知/完成/贪睡处理"
```

---

## Task 7：TodoBootReceiver + AndroidManifest 权限声明

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/todo/TodoBootReceiver.kt`
- Modify: `code/HuaWeiNote/app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 创建 TodoBootReceiver.kt**

```kotlin
package com.fan.hwnote.app.controller.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class TodoBootReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        GlobalScope.launch {
            runCatching { TodoAlarmManager.rescheduleAll(context.applicationContext) }
            pending.finish()
        }
    }
}
```

- [ ] **Step 2: 修改 AndroidManifest.xml — 加权限和组件声明**

在 `<uses-permission android:name="android.permission.RECORD_AUDIO" />` 后追加：
```xml
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

在 `</provider>` 后、`</application>` 前追加：
```xml
        <activity
            android:name=".controller.todo.TodoDetailActivity"
            android:exported="false"
            android:parentActivityName=".controller.list.NoteListActivity"
            android:windowSoftInputMode="adjustResize" />

        <receiver
            android:name=".controller.todo.TodoAlarmReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.fan.hwnote.ACTION_TODO_REMIND" />
                <action android:name="com.fan.hwnote.ACTION_TODO_COMPLETE" />
                <action android:name="com.fan.hwnote.ACTION_TODO_SNOOZE" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".controller.todo.TodoBootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 3: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/todo/TodoBootReceiver.kt \
  code/HuaWeiNote/app/src/main/AndroidManifest.xml
git commit -m "feat(m14b): 加 TodoBootReceiver + Manifest 权限/组件声明"
```

---

## Task 8：TodoDetailActivity — 详情页（第一部分：类骨架 + onCreate + 数据加载）

**Files:**
- Create: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/todo/TodoDetailActivity.kt`

这个文件较大（~300 行），分 Task 8 和 Task 9 两步写入。Task 8 写类骨架、字段、onCreate、loadTodo、UI 更新方法。Task 9 写保存、事件处理、权限。

- [ ] **Step 1: 创建 TodoDetailActivity.kt 第一部分**

```kotlin
package com.fan.hwnote.app.controller.todo

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fan.hwnote.app.R
import com.fan.hwnote.app.model.FolderRepository
import com.fan.hwnote.app.model.TodoRepository
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
import com.fan.hwnote.app.model.entity.RepeatType
import com.fan.hwnote.app.model.entity.Todo
import com.fan.hwnote.app.view.picker.DateTimePickerDialog
import com.fan.hwnote.app.view.picker.RepeatPickerBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class TodoDetailActivity : AppCompatActivity() {

    private lateinit var folderIndicator: TextView
    private lateinit var checkComplete: CheckBox
    private lateinit var inputTitle: EditText
    private lateinit var rowRemind: View
    private lateinit var tvRemind: TextView
    private lateinit var btnClearRemind: ImageView
    private lateinit var rowRepeat: View
    private lateinit var tvRepeatValue: TextView
    private lateinit var switchImportant: Switch
    private lateinit var inputMemo: EditText

    private var todoId = -1L
    private var loadedTodo: Todo? = null
    private var pendingRemindAt = 0L
    private var pendingRepeatType = RepeatType.NONE
    private var pendingFolderId: Long? = null
    private var pendingIsCompleted = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showDateTimePicker()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_todo_detail)

        folderIndicator = findViewById(R.id.folder_indicator)
        checkComplete = findViewById(R.id.check_complete)
        inputTitle = findViewById(R.id.input_title)
        rowRemind = findViewById(R.id.row_remind)
        tvRemind = findViewById(R.id.tv_remind)
        btnClearRemind = findViewById(R.id.btn_clear_remind)
        rowRepeat = findViewById(R.id.row_repeat)
        tvRepeatValue = findViewById(R.id.tv_repeat_value)
        switchImportant = findViewById(R.id.switch_important)
        inputMemo = findViewById(R.id.input_memo)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }
        rowRemind.setOnClickListener { onRemindClicked() }
        btnClearRemind.setOnClickListener { clearRemind() }
        rowRepeat.setOnClickListener { showRepeatPicker() }
        checkComplete.setOnCheckedChangeListener { _, isChecked ->
            pendingIsCompleted = isChecked
        }
        folderIndicator.setOnClickListener { showFolderPicker() }
        findViewById<View>(R.id.btn_share).setOnClickListener {
            Toast.makeText(this, R.string.toast_todo_share_placeholder, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.btn_delete).setOnClickListener { deleteTodo() }

        todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        loadTodo()
    }

    private fun loadTodo() {
        if (todoId <= 0L) {
            val newTodo = Todo.new()
            loadedTodo = newTodo
            pendingRemindAt = 0L
            pendingRepeatType = RepeatType.NONE
            pendingFolderId = null
            pendingIsCompleted = false
            updateRemindUI()
            updateRepeatUI()
            updateFolderUI()
            inputTitle.requestFocus()
            return
        }

        lifecycleScope.launch {
            val todo = TodoRepository.getById(todoId) ?: run {
                Toast.makeText(this@TodoDetailActivity,
                    R.string.todo_save_failed, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            loadedTodo = todo
            pendingRemindAt = todo.remindAt
            pendingRepeatType = todo.repeatType
            pendingFolderId = todo.folderId
            pendingIsCompleted = todo.isCompleted

            inputTitle.setText(todo.title)
            inputMemo.setText(todo.memo)
            checkComplete.isChecked = todo.isCompleted
            switchImportant.isChecked = todo.isImportant
            updateRemindUI()
            updateRepeatUI()
            updateFolderUI()
        }
    }

    private fun updateRemindUI() {
        if (pendingRemindAt <= 0) {
            tvRemind.text = getString(R.string.todo_detail_add_remind)
            tvRemind.setTextColor(ContextCompat.getColor(this, R.color.text_hint))
            btnClearRemind.visibility = View.GONE
            return
        }

        val cal = Calendar.getInstance().apply { timeInMillis = pendingRemindAt }
        val now = Calendar.getInstance()
        val isToday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
            && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM)
            getString(R.string.picker_datetime_am) else getString(R.string.picker_datetime_pm)
        var hour = cal.get(Calendar.HOUR)
        if (hour == 0) hour = 12
        val minute = cal.get(Calendar.MINUTE)

        val text = if (isToday) {
            getString(R.string.todo_remind_today_format, amPm, hour, minute)
        } else {
            val dateStr = "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
            getString(R.string.todo_remind_format, dateStr, amPm, hour, minute)
        }
        tvRemind.text = text

        val overdue = pendingRemindAt < System.currentTimeMillis()
        val color = if (overdue) ContextCompat.getColor(this, R.color.danger)
            else ContextCompat.getColor(this, R.color.primary)
        tvRemind.setTextColor(color)
        btnClearRemind.visibility = View.VISIBLE
    }

    private fun updateRepeatUI() {
        val text = when (pendingRepeatType) {
            RepeatType.NONE -> getString(R.string.todo_detail_no_repeat)
            RepeatType.DAILY -> getString(R.string.todo_repeat_daily)
            RepeatType.WEEKLY -> getString(R.string.todo_repeat_weekly)
            RepeatType.MONTHLY -> getString(R.string.todo_repeat_monthly)
            RepeatType.YEARLY -> getString(R.string.todo_repeat_yearly)
        }
        tvRepeatValue.text = text
    }

    private fun updateFolderUI() {
        val fId = pendingFolderId
        if (fId == null) {
            folderIndicator.text = getString(R.string.todo_detail_folder_none)
            return
        }
        lifecycleScope.launch {
            val folder = FolderRepository.get(fId)
            folderIndicator.text = folder?.name ?: getString(R.string.todo_detail_folder_none)
        }
    }

    companion object {
        const val EXTRA_TODO_ID = "todo_id"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/todo/TodoDetailActivity.kt
git commit -m "feat(m14b): 加 TodoDetailActivity 骨架(onCreate+loadTodo+UI更新)"
```

---

## Task 9：TodoDetailActivity — 第二部分：保存 + 事件处理 + 权限

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/todo/TodoDetailActivity.kt`

在 `companion object` 前追加以下方法。

- [ ] **Step 1: 追加 onPause 保存逻辑**

在 `updateFolderUI()` 方法后、`companion object` 前追加：

```kotlin
    override fun onPause() {
        super.onPause()
        saveTodo()
    }

    private fun saveTodo() {
        val loaded = loadedTodo ?: return
        val title = inputTitle.text.toString().trim()
        val memo = inputMemo.text.toString()
        val isImportant = switchImportant.isChecked

        if (loaded.id == 0L && title.isEmpty()) return

        val toSave = loaded.copy(
            title = title,
            memo = memo,
            isCompleted = pendingIsCompleted,
            isImportant = isImportant,
            remindAt = pendingRemindAt,
            repeatType = pendingRepeatType,
            folderId = pendingFolderId,
        )

        lifecycleScope.launch(Dispatchers.IO) {
            if (loaded.id == 0L) {
                val newId = TodoRepository.insert(toSave)
                if (newId > 0L) {
                    val saved = toSave.copy(id = newId)
                    if (saved.remindAt > System.currentTimeMillis()) {
                        TodoAlarmManager.scheduleAlarm(this@TodoDetailActivity, saved)
                    }
                    withContext(Dispatchers.Main) {
                        todoId = newId
                        loadedTodo = saved
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TodoDetailActivity,
                            R.string.todo_save_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                TodoRepository.update(toSave)
                if (toSave.remindAt > System.currentTimeMillis()) {
                    TodoAlarmManager.scheduleAlarm(this@TodoDetailActivity, toSave)
                } else {
                    TodoAlarmManager.cancelAlarm(this@TodoDetailActivity, toSave.id)
                }
                withContext(Dispatchers.Main) {
                    loadedTodo = toSave
                }
            }
        }
    }
```

- [ ] **Step 2: 追加提醒/重复/文件夹/删除事件处理**

在 `saveTodo()` 方法后追加：

```kotlin
    private fun onRemindClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.alarm_permission_title)
                    .setMessage(R.string.alarm_permission_message)
                    .setPositiveButton(R.string.action_open_settings) { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .show()
                return
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        showDateTimePicker()
    }

    private fun showDateTimePicker() {
        DateTimePickerDialog(this, pendingRemindAt) { epochMillis ->
            pendingRemindAt = epochMillis
            updateRemindUI()
        }.show()
    }

    private fun clearRemind() {
        pendingRemindAt = 0L
        pendingRepeatType = RepeatType.NONE
        if (todoId > 0L) {
            TodoAlarmManager.cancelAlarm(this, todoId)
        }
        updateRemindUI()
        updateRepeatUI()
    }

    private fun showRepeatPicker() {
        RepeatPickerBottomSheet(this, pendingRepeatType) { type ->
            pendingRepeatType = type
            updateRepeatUI()
        }.show()
    }

    private fun showFolderPicker() {
        lifecycleScope.launch {
            val folders = FolderRepository.list()
            val names = mutableListOf(getString(R.string.todo_detail_folder_none))
            val ids = mutableListOf<Long?>(null)
            for (f in folders) {
                names += f.name
                ids += f.id
            }
            val currentIdx = ids.indexOf(pendingFolderId).coerceAtLeast(0)
            AlertDialog.Builder(this@TodoDetailActivity)
                .setTitle(R.string.category_picker_title)
                .setSingleChoiceItems(names.toTypedArray(), currentIdx) { dialog, which ->
                    pendingFolderId = ids[which]
                    updateFolderUI()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun deleteTodo() {
        if (todoId <= 0L) {
            finish()
            return
        }
        com.fan.hwnote.app.view.DeleteConfirmBottomSheet.show(
            context = this,
            title = getString(R.string.todo_delete_title),
            message = getString(R.string.todo_delete_message),
            confirmLabel = getString(R.string.action_delete),
            confirmIsDanger = true,
        ) {
            lifecycleScope.launch {
                TodoRepository.softDelete(todoId)
                TodoAlarmManager.cancelAlarm(this@TodoDetailActivity, todoId)
                finish()
            }
        }
    }
```

- [ ] **Step 3: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/todo/TodoDetailActivity.kt
git commit -m "feat(m14b): TodoDetailActivity 加 onPause 保存+事件处理+权限检查"
```

---

## Task 10：TodoListFragment 接通详情页跳转 + QuickAddBar 时间选择 + 闹钟注册

**Files:**
- Modify: `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFragment.kt`

- [ ] **Step 1: 替换 onClick Toast 为 DetailActivity 跳转**

`TodoListFragment.kt` 的 adapter 构造 (L86-88) 中 `onClick` 回调：

旧代码：
```kotlin
            onClick = { todo ->
                Toast.makeText(requireContext(), todo.title, Toast.LENGTH_SHORT).show()
            },
```

新代码：
```kotlin
            onClick = { todo ->
                val intent = Intent(requireContext(), TodoDetailActivity::class.java)
                intent.putExtra(TodoDetailActivity.EXTRA_TODO_ID, todo.id)
                startActivity(intent)
            },
```

- [ ] **Step 2: 替换 quickAddTime 的 Toast 为 DateTimePickerDialog**

旧代码 (L100-102)：
```kotlin
        quickAddTime.setOnClickListener {
            Toast.makeText(requireContext(), "时间选择（M14b 实现）", Toast.LENGTH_SHORT).show()
        }
```

新代码：
```kotlin
        quickAddTime.setOnClickListener {
            DateTimePickerDialog(requireContext(), quickAddRemindAt) { epochMillis ->
                quickAddRemindAt = epochMillis
                updateQuickAddTimeIcon()
            }.show()
        }
```

- [ ] **Step 3: 追加 updateQuickAddTimeIcon 方法**

在 `updateQuickAddImportantIcon()` 方法后追加：

```kotlin
    private fun updateQuickAddTimeIcon() {
        val color = if (quickAddRemindAt > 0)
            ContextCompat.getColor(requireContext(), R.color.primary)
        else
            ContextCompat.getColor(requireContext(), R.color.text_hint)
        quickAddTime.setColorFilter(color)
    }
```

- [ ] **Step 4: saveQuickAdd 中新增闹钟注册**

在 `saveQuickAdd()` 方法中，`TodoRepository.insert(...)` 后追加闹钟注册：

旧代码：
```kotlin
        lifecycleScope.launch {
            TodoRepository.insert(Todo.new().copy(
                title = title,
                remindAt = quickAddRemindAt,
                isImportant = quickAddIsImportant,
                folderId = folderId,
            ))
            hideQuickAddBar()
            reload()
        }
```

新代码：
```kotlin
        lifecycleScope.launch {
            val todo = Todo.new().copy(
                title = title,
                remindAt = quickAddRemindAt,
                isImportant = quickAddIsImportant,
                folderId = folderId,
            )
            val newId = TodoRepository.insert(todo)
            if (newId > 0L && todo.remindAt > System.currentTimeMillis()) {
                TodoAlarmManager.scheduleAlarm(requireContext(), todo.copy(id = newId))
            }
            hideQuickAddBar()
            reload()
        }
```

- [ ] **Step 5: 追加 import 语句**

在 import 区追加：
```kotlin
import com.fan.hwnote.app.controller.todo.TodoDetailActivity
import com.fan.hwnote.app.model.alarm.TodoAlarmManager
import com.fan.hwnote.app.view.picker.DateTimePickerDialog
```

移除不再需要的 `import android.widget.Toast`（如果其他地方也没用到了）。

- [ ] **Step 6: showQuickAddBar 中追加 updateQuickAddTimeIcon 调用**

在 `showQuickAddBar()` 方法中 `updateQuickAddImportantIcon()` 后追加：
```kotlin
        updateQuickAddTimeIcon()
```

- [ ] **Step 7: Commit**

```bash
git add code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/controller/list/TodoListFragment.kt
git commit -m "feat(m14b): TodoListFragment 接通详情页跳转+时间选择+闹钟注册"
```

---

## Task 11：DateTimePickerDialog.roundToNext5Minutes 单元测试

**Files:**
- Create: `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/view/picker/DateTimePickerDialogTest.kt`

- [ ] **Step 1: 创建测试文件**

```kotlin
package com.fan.hwnote.app.view.picker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Calendar

class DateTimePickerDialogTest {

    @Test
    fun `roundToNext5Minutes - exact multiple rounds up by 5`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 5, 14, 30, 15)
            set(Calendar.MILLISECOND, 500)
        }
        DateTimePickerDialog.roundToNext5Minutes(cal)
        assertEquals(35, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `roundToNext5Minutes - not on boundary rounds to next 5`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 5, 14, 37, 20)
        }
        DateTimePickerDialog.roundToNext5Minutes(cal)
        assertEquals(40, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun `roundToNext5Minutes - minute 58 crosses hour`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 5, 14, 58, 0)
        }
        DateTimePickerDialog.roundToNext5Minutes(cal)
        assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
  ./gradlew :app:test --tests "com.fan.hwnote.app.view.picker.DateTimePickerDialogTest" --info
```

预期：3 tests PASSED。

- [ ] **Step 3: Commit**

```bash
git add code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/view/picker/DateTimePickerDialogTest.kt
git commit -m "test(m14b): 加 DateTimePickerDialog.roundToNext5Minutes 单测"
```

---

## Task 12：构建验证 + 全量测试

- [ ] **Step 1: clean + assembleDebug + test**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
  ./gradlew :app:clean :app:assembleDebug :app:test
```

预期：编译通过 + 181 项测试全绿（178 旧 + 3 新）。

- [ ] **Step 2: 如有编译错误或测试失败，就地修复并 commit**

---

## Task 13：STATUS 更新 + 计划归档

**Files:**
- Modify: `docs/superpowers/STATUS.md`
- Move: `docs/superpowers/plans/2026-06-05-hwnote-m14b-todo-detail-alarm.md` → `docs/superpowers/plans/archived/`

- [ ] **Step 1: 更新 STATUS.md**

在里程碑表追加 M14b 行：
```
| M14b | 待办详情+时间选择+通知 | 13 | 3 | TodoDetailActivity(onPause 自动保存) + DateTimePickerDialog(4列 NumberPicker) + RepeatPickerBottomSheet + TodoAlarmManager(精确闹钟) + TodoAlarmReceiver(通知/完成/贪睡) + TodoBootReceiver(开机恢复) + NotificationChannel + POST_NOTIFICATIONS/SCHEDULE_EXACT_ALARM 权限 |
```

更新头部统计：
- 测试数 178→181
- 里程碑 M14a→M14b
- 最新 HEAD commit hash

- [ ] **Step 2: 归档计划文件**

```bash
mv docs/superpowers/plans/2026-06-05-hwnote-m14b-todo-detail-alarm.md \
   docs/superpowers/plans/archived/
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/STATUS.md \
  docs/superpowers/plans/archived/2026-06-05-hwnote-m14b-todo-detail-alarm.md
git commit -m "docs(m14b): 标记 M14b 完成 + 归档实施计划"
```

