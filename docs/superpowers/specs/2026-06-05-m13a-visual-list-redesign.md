# M13a 视觉基础 + 列表页重设计 — 设计文档

## 1. 目标

将 HwNote 的视觉体系从当前"绿色 Material Toolbar"风格全面切换到华为备忘录的"白底大标题 + 蓝色强调"风格。M13a 聚焦**色彩体系**和**列表页**，为后续 M13b-M13e 奠定视觉基础。

**参考截图：** `docs/superpowers/references/huawei-note/` 目录下 34 张华为备忘录真机截图。

## 2. 设计决策

### 2.1 色彩体系：绿 → 蓝

| 色名 | 旧值 | 新值 | 用途 |
|------|------|------|------|
| primary | #00897B (teal) | **#007DFF** (华为蓝) | FAB、底部导航高亮、筛选选中态、链接、按钮文字 |
| primary_dark | #00695C | **#0056B3** | 备用深色变体 |
| primary_light | #E0F2F1 | **#E3F2FD** | 淡蓝背景、卡片 ripple |
| star (收藏) | #FFB300 (amber) | **#FFB300** (不变) | 收藏星标保持黄色 |
| error/danger | #E53935 | **#E53935** (不变) | 错误/危险操作 |

**状态栏：**
- 旧：深绿状态栏 + 白色系统图标（`windowLightStatusBar=false`, `statusBarColor=#00695C`）
- 新：白色状态栏 + 深色系统图标（`windowLightStatusBar=true`, `statusBarColor=@android:color/white`）

### 2.2 列表页：去 AppBar，改大标题

**旧布局结构：**
```
CoordinatorLayout (bg=#FAFAFA)
  └─ AppBarLayout (bg=#00897B, fitsSystemWindows)
       ├─ Toolbar (bg=#00897B, 含 filter_chip 白色文字+箭头)
       └─ 搜索栏容器 (bg=#00897B, 内含白色圆角搜索框)
  └─ content_container (FrameLayout, scrolling_view_behavior)
       ├─ RecyclerView
       └─ empty_state
  └─ FAB
```

**新布局结构：**
```
LinearLayout (vertical, bg=#FAFAFA, fitsSystemWindows=true)
  ├─ header 区 (LinearLayout)
  │    ├─ 左列 (vertical)
  │    │    ├─ 标题行: "全部笔记 ▼" (26sp bold black) ← 可点击切筛选
  │    │    └─ 副标题: "32 条笔记" (14sp #9E9E9E)
  │    └─ 右列: ⋮ overflow ImageView (ic_more_vert)
  ├─ 搜索栏 (独立圆角 pill, bg=#F5F5F5, 搜索图标+hint)
  ├─ 筛选面板 (RecyclerView, 默认 GONE)   ← 点标题时显示，替代旧 PopupWindow
  ├─ 笔记列表 RecyclerView              ← 筛选面板显示时 GONE
  ├─ 空状态 View
  ├─ FAB (bg=#007DFF)
  └─ 底部导航栏 (笔记 | 待办)
```

**标题行为：**
- 默认："全部笔记 ▼"，副标题 "N 条笔记"
- 选中笔记本："哈哈哈 ▼"，副标题 "1 条笔记 | 我的笔记"（条数 + 所属文件夹名）
- 选中文件夹："旅游 ▼"，副标题 "3 条笔记"
- 选中收藏："我的收藏 ▼"，副标题 "N 条笔记"
- 选中已删除："最近删除 ▼"，副标题 "N 条笔记"
- 点击标题 → ▼ 变 ▲，搜索栏+列表隐藏，筛选面板显示
- 再次点击或选中某项 → ▲ 变 ▼，筛选面板隐藏，搜索栏+列表恢复

### 2.3 筛选面板（替代 NotebookFilterPopupWindow）

**旧方案：** `PopupWindow` 弹出小窗，280dp 宽。

**新方案：** 同页内嵌面板，点标题时 visibility 切换（`VISIBLE` / `GONE`），占满宽度。

**面板结构（对标华为截图 "全部笔记点开的顶部筛选弹窗.jpg"）：**

| 行 | 图标 | 文字 | 右侧 | 说明 |
|---|---|---|---|---|
| 伪项 | ic_note_tab | 全部笔记 | 数字 | 选中态：左侧蓝色竖条 + 蓝色背景行 |
| 伪项 | ic_uncategorized | 未分类 | 数字 | |
| 伪项 | ic_star_outline | 我的收藏 | 数字 | |
| 伪项 | ic_delete | 最近删除 | 数字 | |
| 分隔线 | — | — | — | 1dp #EEEEEE |
| section header | — | 文件夹 | "管理" (蓝色链接) | 点"管理" → FolderManagerActivity |
| FolderHead | ic_folder | 文件夹名 | ∧/∨ | 点击展开/折叠 |
| Notebook | 色块书本 | 笔记本名 | 数字 | 缩进 40dp，点击选中并收起面板 |

**计数逻辑：** 每项右侧显示该筛选条件下的笔记数。`NoteRepository.count(filter)` 新增 suspend 方法，返回 `Int`。

**选中态视觉：** 选中行左侧 4dp 宽蓝色竖条 + 行背景 #E3F2FD（淡蓝），文字变蓝。

### 2.4 笔记卡片样式

**当前：** `MaterialCardView` with `elevation=2dp`, `cornerRadius=10dp`, white bg, `primary_light` ripple。

**改为：**
- `elevation=0dp`（扁平）
- `strokeWidth=0.5dp, strokeColor=#E8E8E8`（极淡边框）
- `cornerRadius=10dp`（保持）
- 白色背景
- 卡片内 `paddingHorizontal=16dp, paddingVertical=14dp`

**笔记本颜色标识：**
- 归属到有颜色笔记本的笔记，卡片背景设为笔记本颜色的 ~8% alpha 淡化版
- 未分类笔记卡片保持白色
- 实现：`NoteListAdapter.onBindViewHolder` 中根据 `note.notebookId` 查 `NotebookRepository` 获取颜色，用 `Color.argb(20, r, g, b)` 生成淡化色

### 2.5 笔记本背景色

**当前：** 列表页背景始终 #FAFAFA。

**改为（对标华为 "切换笔记本后的背景样式.jpg"）：**
- 浏览"全部笔记"/"未分类"/"收藏"/"已删除" → 背景保持 #FAFAFA
- 浏览特定笔记本 → 背景变为笔记本颜色的 ~10% alpha 淡化版
- 浏览特定文件夹 → 背景保持 #FAFAFA（文件夹无颜色属性）
- 实现：`NoteListActivity` 切换 filter 时，根据 `ListFilter.Notebook(id)` 查颜色设 `rootLayout.setBackgroundColor(tintColor)`

### 2.6 底部导航栏

**结构：** 不使用 `BottomNavigationView`（避免引入 Fragment/Navigation 依赖），直接用 `LinearLayout`。

```xml
<LinearLayout  <!-- 底部导航栏 -->
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:orientation="horizontal"
    android:background="@color/white"
    android:elevation="8dp">

    <LinearLayout  <!-- 笔记 tab -->
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:src="@drawable/ic_note_tab" />  <!-- 蓝色 -->
        <TextView android:text="笔记" />                    <!-- 蓝色 -->
    </LinearLayout>

    <LinearLayout  <!-- 待办 tab -->
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:gravity="center"
        android:orientation="vertical">
        <ImageView android:src="@drawable/ic_todo_tab" />  <!-- 灰色 -->
        <TextView android:text="待办" />                    <!-- 灰色 -->
    </LinearLayout>
</LinearLayout>
```

- 笔记 tab：蓝色图标 + 蓝色文字（固定选中态）
- 待办 tab：灰色图标 + 灰色文字，点击 → Toast "待办功能（M14 实现）"
- 图标：`ic_note_tab`（线框文档图标，新建 24dp vector）、`ic_todo_tab`（勾选圆圈，新建 24dp vector）

### 2.7 Overflow ⋮ 菜单

**当前：** Toolbar menu 中一个 sort 图标。

**改为：** header 区右上角 ⋮ `ImageView`，点击弹出 `PopupMenu`。M13a 仅含"排序方式"一项。后续 M13d 追加"宫格视图"/"批量删除"。

### 2.8 排序 BottomSheet 微调

**当前 `dialog_sort_picker.xml`：** 标题 + RadioGroup 2 项，选中即关闭。

**改为（对标华为 "排序方式弹窗.jpg"）：**
- 保持标题"排序方式" + RadioGroup 2 项
- 底部新增"取消"蓝色文字按钮（居中），点击关闭 Sheet
- RadioButton `colorControlActivated` 改蓝色

### 2.9 删除确认 BottomSheet 简化

**当前 `DeleteConfirmBottomSheet`：** title + message + confirm/cancel 按钮（竖排或横排，参数化）。

**改为（对标华为 "删除确认 Sheet.jpg"）：**
- 单行提示文字（如"是否删除此笔记？"）居中
- 下方横排两按钮：取消（蓝色文字）| 分隔线 | 删除（蓝色文字）
- 去掉独立 title 行（提示文字即标题）
- `DeleteConfirmBottomSheet` 构造参数简化：`message + confirmLabel + onConfirm`，去掉 `title` 参数

### 2.10 新建文件夹/笔记本 Sheet 按钮蓝化

**当前：** "取消"/"保存" 按钮使用 `?attr/colorPrimary`（旧绿色）。

**改为：** 色彩体系切蓝后自动跟随，无需额外代码改动。仅确认布局结构不变。

## 3. 涉及文件

| 类别 | 文件 | 改动 |
|------|------|------|
| 色彩 | `res/values/colors.xml` | primary/primary_dark/primary_light 三色值改蓝 |
| 主题 | `res/values/themes.xml` | statusBarColor 改白、windowLightStatusBar=true |
| 列表布局 | `res/layout/activity_note_list.xml` | 整体重写：去 CoordinatorLayout/AppBarLayout，改 LinearLayout 大标题结构 |
| 列表逻辑 | `NoteListActivity.kt` | 标题/副标题/overflow/筛选面板切换/笔记本背景色/底部导航 |
| 筛选面板 | 新文件：`FilterPanelAdapter.kt` | 筛选面板的 RecyclerView adapter（伪项+文件夹树+计数） |
| 筛选面板 | `res/layout/item_filter_pseudo.xml` | 加右侧计数 TextView + 左侧蓝色选中条 |
| 筛选面板 | `res/layout/item_filter_notebook.xml` | 加右侧计数 TextView |
| 旧筛选 | `NotebookFilterPopupWindow.kt` | 删除（不再使用） |
| 旧筛选布局 | `res/layout/popup_notebook_filter.xml` | 删除 |
| 数据层 | `NoteRepository.kt` | 新增 `count(filter): Int` suspend 方法 |
| 卡片布局 | `res/layout/item_note_card.xml` | elevation=0, strokeWidth=0.5dp, strokeColor=#E8E8E8 |
| 卡片逻辑 | `NoteListAdapter.kt` | onBindViewHolder 加笔记本颜色淡化背景 |
| 底部导航 | `activity_note_list.xml` 内 | 底部 LinearLayout 2-tab |
| 导航图标 | `res/drawable/ic_note_tab.xml` | 新建 24dp vector |
| 导航图标 | `res/drawable/ic_todo_tab.xml` | 新建 24dp vector |
| Overflow | `res/menu/menu_note_list_toolbar.xml` | 改名或重写，⋮ 菜单含排序 |
| 排序 Sheet | `res/layout/dialog_sort_picker.xml` | 加底部"取消"按钮 |
| 排序逻辑 | `NoteListActivity.kt` | "取消"按钮点击关闭 sheet |
| 删除确认 | `res/layout/dialog_delete_confirm.xml` | 简化为单行提示 + 横排取消/删除 |
| 删除确认 | `DeleteConfirmBottomSheet.kt` | 去掉 title 参数，简化布局绑定 |
| strings | `res/values/strings.xml` | 新增：底部导航文字、筛选面板文字、待办占位 Toast |

## 4. 不动的东西

- **数据层核心：** Folder/Notebook/Note 实体、FolderRepository、NotebookRepository（除 count 方法外）
- **编辑器：** NoteEditorActivity、EditorPresenter、所有 BlockView — M13b 处理
- **工具栏：** TextToolbarView、HandwritingToolbarView — M13b 处理
- **样式 Sheet：** StylePickerBottomSheet — M13c 处理
- **手写系统：** HandwritingOverlayView、BrushPainter、StrokeEraser
- **音频系统：** AudioRecorder、AudioPlayer、AudioRecordingBottomSheet
- **撤销系统：** EditHistoryManager、所有 Command 子类
- **FolderManagerActivity：** M13a 不动管理页视觉（仅色彩跟随主题自动变蓝）
- **NotebookPickerPopupWindow：** 保留（编辑器用），M13b 再处理
- **NewFolder/NewNotebook BottomSheet：** 仅按钮颜色跟随主题自动变蓝，无需代码改动

## 5. 验证（真机）

- 列表页：白底大标题 + 副标题条数 + ⋮ 菜单，无绿色 AppBar
- 状态栏：白色背景 + 深色系统图标
- 筛选面板：点标题 ▼ 展开全屏面板，选中项蓝色高亮 + 计数，选中后收起
- 笔记卡片：扁平无阴影，淡边框
- 笔记本颜色：选中笔记本 → 整页背景淡化色 + 卡片淡化色
- 底部导航：笔记蓝色选中 + 待办灰色，待办点击 Toast
- FAB：蓝色
- 排序 Sheet：有"取消"按钮
- 删除确认：单行提示 + 横排按钮
- 编辑器/手写/管理页：仅色彩跟随变蓝，布局不变（M13b 再处理）
- 全部 137 项测试 PASSED

## 6. 后续里程碑

| 轮次 | 范围 | 依赖 |
|------|------|------|
| M13b | 编辑器重设计（白底顶栏 + 浏览/编辑态 + 底部动作栏 + 工具栏文字标签） | M13a 色彩基础 |
| M13c | 样式增强（对齐 + 列表 + 字号滑块 + 背景纹理） | M13b 编辑器 |
| M13d | 宫格视图 + 批量删除 | M13a 列表页 |
| M13e | 分享功能（分享为图片/文本/文档） | M13b 编辑器 |
