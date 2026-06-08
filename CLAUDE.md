# CLAUDE.md — HwNote 项目指令

## 语言与交互

- **中文输出**：所有对话、文档、注释说明均使用中文（代码标识符、commit message 动词部分除外）
- **Subagent-Driven 自动采用**：需要执行实施计划时，直接选择 Subagent-Driven Development，无需向用户确认执行方式

## Agent 接手路径

1. **MEMORY.md**（自动加载）— 项目概况、用户偏好、历史决策
2. **docs/superpowers/STATUS.md** — 进度全貌、里程碑表、当前状态
3. **docs/superpowers/specs/** — 设计意图、PRD、技术方案
4. **代码** — `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/`

## 技术栈约束

- Kotlin + MVC + XML View，**禁止 Jetpack Compose**
- 允许：AppCompat / RecyclerView / Material / ConstraintLayout / Lifecycle（仅 `lifecycleScope`）
- 禁止：ViewModel / LiveData / Room / Navigation / WorkManager / Hilt / Compose
- DB：`SQLiteOpenHelper` 直接使用，不用 Room
- JSON：Android 内置 `org.json`
- 图片：Glide 基础 API（无 kapt compiler）
- 协程只用 `lifecycleScope`，禁止 `CoroutineScope(Dispatchers.Main)`

## 版本锁定（不可修改）

| 项 | 版本 |
|---|---|
| AGP | 8.11.2 |
| Kotlin | 2.0.21 |
| Gradle | 8.14.3 |
| compileSdk / targetSdk | 36 |
| minSdk | 24 |
| JDK（运行 Gradle） | 17 |

如遇库兼容问题，调整库版本适配现有工具链，**不要反过来改工具链**。

## 构建命令

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 11) \
  ./gradlew :app:assembleDebug :app:test
```

工作目录：`code/HuaWeiNote/`

## Git 约定

- **Commit message**：中文动宾短语，如 `fix: 修复清单聚焦问题`、`feat(m12): 加文件夹层级`
- **禁止** `Co-Authored-By` 尾部标记
- **Stage 指定文件**：`git add <file1> <file2>`，禁止 `git add -A` 或 `git add .`
- 在 master 分支直接提交（除非用户另行指定分支）

## 代码规范

- `sealed class` 的 `when` 必须用**表达式形式 + 显式列举所有子类**，禁止 `else -> Unit`
- FQN 出现两次及以上就 import
- 默认不写注释；只在 WHY 不明显时加一行短注释
- APP 未上线，不需要考虑历史数据兼容

## 开发流程

- 每个里程碑先写**详细实施计划**（≤15 个任务），获得用户确认后再动代码
- 新功能需经过：brainstorm → spec → 用户审阅 spec → writing-plans → 用户审阅 plan → subagent-driven-development
- 写大文件（>500 行）时分 4-6 次写入，避免单次输出过长

## 关键路径速查

| 用途 | 路径 |
|---|---|
| 代码根 | `code/HuaWeiNote/app/src/main/java/com/fan/hwnote/app/` |
| 资源 | `code/HuaWeiNote/app/src/main/res/` |
| 测试 | `code/HuaWeiNote/app/src/test/java/com/fan/hwnote/app/` |
| PRD + 设计文档 | `docs/superpowers/specs/` |
| 已归档计划 | `docs/superpowers/plans/archived/` |
| 进度看板 | `docs/superpowers/STATUS.md` |
| 参考截图 | `docs/superpowers/references/huawei-note/` |
