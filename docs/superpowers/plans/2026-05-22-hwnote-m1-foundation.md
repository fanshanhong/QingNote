# M1 · 基础工程 实施计划（详细版）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建一个能 build 通过、能在 Android 设备上启动并显示空白主屏 + FAB 的最小 Android App 骨架。

**Architecture:** 单 `:app` 模块 Gradle 项目，Kotlin DSL，应用 `Theme.MaterialComponents.Light.NoActionBar` 主题。`App` 是 `Application` 子类，`NoteListActivity` 是入口（暂只显示空白布局）。FileProvider 已配置好 authority 与 `xml/file_paths.xml`，供后续 M5 拍照功能使用。

**Tech Stack:** Kotlin 2.0.20 · AGP 8.5.2 · Gradle 8.9 · compileSdk 34 / minSdk 24 / targetSdk 34 · AppCompat / RecyclerView / Material / ConstraintLayout / Lifecycle / Coroutines / Glide

**配套文档：**
- PRD：`docs/superpowers/specs/2026-05-22-hwnote-design.md`
- 高层计划：`docs/superpowers/plans/2026-05-22-hwnote-implementation.md`
- 进度看板：`docs/superpowers/STATUS.md`

---

## 前置条件

- 已安装 Android Studio Hedgehog (2023.1.1) 或更新；或本地有 `gradle` CLI（≥ 8.9）+ Android SDK
- ANDROID_HOME 环境变量已设置
- 至少有一台 Android 模拟器或真机（API ≥ 24）
- 工作目录：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/`（以下简称项目根）

---

## 任务概览

| # | 任务 | 产出 |
|---|------|------|
| 1 | 生成项目骨架 + 初始化 git | `gradlew`/`gradle/wrapper/` + `.gitignore` |
| 2 | 配置根 Gradle 文件 | `settings.gradle.kts` / `build.gradle.kts` / `gradle.properties` |
| 3 | 配置 `app/build.gradle.kts` | app 模块构建脚本 + 全部依赖 |
| 4 | 编写 `AndroidManifest.xml` | Application + Activity + FileProvider |
| 5 | 创建 res/values 资源 | colors / strings / themes / dimens |
| 6 | 创建 file_paths + 启动图标 | `xml/file_paths.xml` + `mipmap-*/ic_launcher` |
| 7 | 编写 `App.kt`（Application） | 单例钩子（M2 接入 NoteRepository） |
| 8 | 编写 `NoteListActivity` 占位 | 入口 Activity + 主屏布局 |
| 9 | 占位 ProGuard + 测试目录骨架 | `proguard-rules.pro` + `src/test/kotlin` |
| 10 | 命令行构建验证 | `:app:assembleDebug` 通过 |
| 11 | 安装真机验证 | 启动后看到主屏 + FAB |
| 12 | git 首次提交 | 包含所有文档 + M1 代码 |

---

## Task 1: 生成项目骨架 + 初始化 git

**Files:**
- Create: `.gitignore`
- Create: `gradlew` / `gradlew.bat` / `gradle/wrapper/gradle-wrapper.jar` / `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/` 空目录

> **关于 gradle wrapper 的产生方式**：`gradle-wrapper.jar` 是二进制文件，不能用文本工具写。两种做法：
> - **A（推荐）**：本地有 `gradle` CLI ≥ 8.9，直接 `gradle wrapper --gradle-version 8.9 --distribution-type bin`
> - **B**：用 Android Studio "New Project" 向导先生成一个空 Empty Activity 项目，把 `gradlew` / `gradlew.bat` / `gradle/` 复制过来；其它文件按本计划替换

- [ ] **Step 1: 检查工具**

```bash
gradle --version    # 期望：≥ 8.9，如果没安装走 Android Studio 向导路线
java --version      # 期望：JDK 17
```

- [ ] **Step 2: 生成 gradle wrapper（路线 A）**

在项目根目录执行：

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote
gradle wrapper --gradle-version 8.9 --distribution-type bin
```

预期：生成 `gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`、`gradle/wrapper/gradle-wrapper.properties`。

> 如果 `gradle` CLI 不可用，改用 Android Studio 创建一个空项目（Empty Views Activity，Kotlin，Min SDK 24，包名 `com.fan.hwnote.app`），把生成的 `gradlew` / `gradlew.bat` / `gradle/` 整个目录复制到本项目根；该 AS 项目用完即可删除。

- [ ] **Step 3: 验证 gradle wrapper 可用**

```bash
./gradlew --version
```

预期输出包含 `Gradle 8.9` 和 `Kotlin: 1.9.x`（这一步只验证 wrapper 本身能跑；具体 Kotlin 版本由我们的 build.gradle.kts 定）。

- [ ] **Step 4: 初始化 git 仓库**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote
git init
git config user.name "<你的名字>"      # 可选，使用全局配置则跳过
git config user.email "<你的邮箱>"
```

- [ ] **Step 5: 写 `.gitignore`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/.gitignore`

```gitignore
# Gradle
.gradle/
build/
local.properties
gradle/wrapper/gradle-wrapper.jar.sha256

# Android Studio / IntelliJ
.idea/
*.iml
*.ipr
*.iws
captures/

# Kotlin
.kotlin/

# macOS
.DS_Store

# Build outputs
*.apk
*.aab
*.ap_
*.dex

# Logs
*.log

# Keystore（MVP 不会有真实 keystore，但预留）
*.jks
*.keystore

# Claude Code 工作文件保留：docs/superpowers/*、CLAUDE.md
```

- [ ] **Step 6: 创建 `app/` 目录骨架**

```bash
mkdir -p app/src/main/kotlin/com/fan/hwnote/app/controller/list
mkdir -p app/src/main/res/{values,xml,layout,drawable,mipmap-mdpi,mipmap-hdpi,mipmap-xhdpi,mipmap-xxhdpi,mipmap-xxxhdpi}
mkdir -p app/src/test/kotlin/com/fan/hwnote/app
```

预期：以上目录都存在但为空（除 `app/src/main/res/values` 等子目录）。

---

## Task 2: 配置根 Gradle 文件

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`

- [ ] **Step 1: 写 `settings.gradle.kts`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HwNote"
include(":app")
```

- [ ] **Step 2: 写根 `build.gradle.kts`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
}
```

- [ ] **Step 3: 写 `gradle.properties`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/gradle.properties`

```properties
# JVM
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8

# Gradle
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false

# AndroidX
android.useAndroidX=true
android.nonTransitiveRClass=true

# Kotlin
kotlin.code.style=official
```

- [ ] **Step 4: 更新 `gradle/wrapper/gradle-wrapper.properties`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/gradle/wrapper/gradle-wrapper.properties`

确认包含（如果 Task 1 已生成则核对版本）：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 5: 验证 Gradle 配置可解析**

```bash
./gradlew tasks --no-configuration-cache
```

预期：列出 Gradle 任务（含 build / clean 等），**没有**报错说找不到 `:app` 模块（因为还没配置 app/build.gradle.kts，会报警告但不致命）。

---

## Task 3: 配置 `app/build.gradle.kts`

**Files:**
- Create: `app/build.gradle.kts`

- [ ] **Step 1: 写 app 模块构建脚本**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fan.hwnote.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fan.hwnote.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // AndroidX
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 图片：Glide 基础 API（不引 compiler/kapt）
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 测试（M2 之后开始用）
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
```

- [ ] **Step 2: 验证 app 模块可解析**

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

预期：打印依赖树，包含 appcompat、material、glide 等。**会因为缺 AndroidManifest.xml 而报错**——下一步补上。

---

## Task 4: 编写 `AndroidManifest.xml`

**Files:**
- Create: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: 写 Manifest**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 拍照权限：M5 才会真正请求；这里仅声明 -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- 相机硬件特性：声明为非必需，避免无相机设备无法安装 -->
    <uses-feature
        android:name="android.hardware.camera"
        android:required="false" />

    <application
        android:name=".App"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.HwNote">

        <activity
            android:name=".controller.list.NoteListActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- FileProvider：M5 拍照功能用，先配好不影响构建 -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="com.fan.hwnote.app.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>

</manifest>
```

> 备注：`@mipmap/ic_launcher` / `@mipmap/ic_launcher_round` / `@string/app_name` / `@style/Theme.HwNote` / `@xml/file_paths` 在后续任务里创建——构建会等所有资源齐了才通过。

---

## Task 5: 创建 res/values 资源

**Files:**
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/dimens.xml`

- [ ] **Step 1: 写 `colors.xml`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/res/values/colors.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 主色（PRD §5.1） -->
    <color name="primary">#00897B</color>
    <color name="primary_dark">#00695C</color>
    <color name="primary_light">#E0F2F1</color>

    <!-- 强调与状态 -->
    <color name="accent_star">#FFB300</color>
    <color name="error">#E53935</color>

    <!-- 文本 -->
    <color name="text_primary">#212121</color>
    <color name="text_secondary">#666666</color>
    <color name="text_hint">#9E9E9E</color>

    <!-- 背景 -->
    <color name="bg_window">#FAFAFA</color>
    <color name="bg_card">#FFFFFF</color>
    <color name="divider">#EEEEEE</color>

    <!-- 通用 -->
    <color name="white">#FFFFFF</color>
    <color name="black">#000000</color>
</resources>
```

- [ ] **Step 2: 写 `strings.xml`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/res/values/strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">备忘录</string>

    <!-- 列表页占位（M3 会换成正式文案） -->
    <string name="list_empty_title">还没有笔记</string>
    <string name="list_empty_subtitle">点击右下角 + 新建一条</string>
    <string name="fab_new_note_cd">新建笔记</string>

    <!-- 通用 -->
    <string name="action_ok">确定</string>
    <string name="action_cancel">取消</string>
    <string name="action_delete">删除</string>

    <!-- M1 占位 toast -->
    <string name="toast_new_note_placeholder">新建笔记（待实现）</string>
</resources>
```

- [ ] **Step 3: 写 `themes.xml`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/res/values/themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">

    <style name="Theme.HwNote" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primary_dark</item>
        <item name="colorAccent">@color/primary</item>
        <item name="android:colorBackground">@color/bg_window</item>
        <item name="android:textColorPrimary">@color/text_primary</item>
        <item name="android:textColorSecondary">@color/text_secondary</item>

        <!-- 状态栏 -->
        <item name="android:statusBarColor">@color/primary_dark</item>
        <item name="android:windowLightStatusBar" tools:targetApi="m">false</item>
    </style>

</resources>
```

- [ ] **Step 4: 写 `dimens.xml`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/res/values/dimens.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 间距 -->
    <dimen name="spacing_xs">4dp</dimen>
    <dimen name="spacing_s">8dp</dimen>
    <dimen name="spacing_m">12dp</dimen>
    <dimen name="spacing_l">16dp</dimen>
    <dimen name="spacing_xl">24dp</dimen>

    <!-- 圆角 -->
    <dimen name="radius_card">10dp</dimen>

    <!-- 字号 -->
    <dimen name="text_title">16sp</dimen>
    <dimen name="text_body">14sp</dimen>
    <dimen name="text_caption">12sp</dimen>
    <dimen name="text_hint">11sp</dimen>

    <!-- 编辑器字号（M4 用） -->
    <dimen name="editor_text_normal">16sp</dimen>
    <dimen name="editor_text_h2">18sp</dimen>
    <dimen name="editor_text_h1">22sp</dimen>
</resources>
```

---

## Task 6: 创建 file_paths.xml + 启动器图标

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/res/mipmap-*/ic_launcher.png` (× 5 密度) + `ic_launcher_round.png`

- [ ] **Step 1: 写 `file_paths.xml`**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <!-- 笔记文件目录（filesDir/notes/...）-->
    <files-path
        name="note_files"
        path="notes/" />

    <!-- 拍照临时文件（cacheDir/camera/...）-->
    <cache-path
        name="camera_cache"
        path="camera/" />
</paths>
```

- [ ] **Step 2: 添加启动器图标占位**

最快方式：从任何已有 Android 项目复制 `mipmap-*/ic_launcher.png` 和 `ic_launcher_round.png`，或用 Android Studio：
- 右键 `app/src/main/res` → New → Image Asset → Launcher Icons (Adaptive and Legacy)
- 默认填充色用 #00897B，前景用文字 "备" 或一个简笔记图案

如果用 AS 自动生成，会同时创建：
- `mipmap-anydpi-v26/ic_launcher.xml`（adaptive icon）
- `mipmap-mdpi/ic_launcher.png` 等 5 个密度
- `drawable/ic_launcher_background.xml`
- `drawable-v24/ic_launcher_foreground.xml`

也可以临时使用纯色占位 PNG（48×48）：

```bash
# macOS 临时占位（用 ImageMagick）
brew install imagemagick   # 如果没装
magick -size 48x48 xc:'#00897B' app/src/main/res/mipmap-mdpi/ic_launcher.png
magick -size 72x72 xc:'#00897B' app/src/main/res/mipmap-hdpi/ic_launcher.png
magick -size 96x96 xc:'#00897B' app/src/main/res/mipmap-xhdpi/ic_launcher.png
magick -size 144x144 xc:'#00897B' app/src/main/res/mipmap-xxhdpi/ic_launcher.png
magick -size 192x192 xc:'#00897B' app/src/main/res/mipmap-xxxhdpi/ic_launcher.png

# round 用同图占位
cp app/src/main/res/mipmap-mdpi/ic_launcher.png app/src/main/res/mipmap-mdpi/ic_launcher_round.png
cp app/src/main/res/mipmap-hdpi/ic_launcher.png app/src/main/res/mipmap-hdpi/ic_launcher_round.png
cp app/src/main/res/mipmap-xhdpi/ic_launcher.png app/src/main/res/mipmap-xhdpi/ic_launcher_round.png
cp app/src/main/res/mipmap-xxhdpi/ic_launcher.png app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
cp app/src/main/res/mipmap-xxxhdpi/ic_launcher.png app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
```

> v2 再做正式图标，MVP 用纯色块即可。

---

## Task 7: 编写 `App.kt`

**Files:**
- Create: `app/src/main/kotlin/com/fan/hwnote/app/App.kt`

- [ ] **Step 1: 写 Application 子类**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/kotlin/com/fan/hwnote/app/App.kt`

```kotlin
package com.fan.hwnote.app

import android.app.Application

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        // M2 完成后在此调用 NoteRepository.init(this)
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
```

> M2 接入数据层后，会在 `onCreate` 末尾加 `NoteRepository.init(this)`。本里程碑保留空实现即可。

---

## Task 8: 编写 `NoteListActivity` 占位 + 主屏布局

**Files:**
- Create: `app/src/main/kotlin/com/fan/hwnote/app/controller/list/NoteListActivity.kt`
- Create: `app/src/main/res/layout/activity_note_list.xml`

- [ ] **Step 1: 写主屏布局**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/res/layout/activity_note_list.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bg_window">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@color/primary"
        android:theme="@style/ThemeOverlay.MaterialComponents.Dark.ActionBar">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="@color/primary"
            app:title="@string/app_name"
            app:titleTextColor="@color/white" />

    </com.google.android.material.appbar.AppBarLayout>

    <!-- M3 会换成 RecyclerView，M1 先用空白占位 -->
    <FrameLayout
        android:id="@+id/content_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <TextView
            android:id="@+id/empty_text"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:gravity="center"
            android:text="@string/list_empty_title"
            android:textColor="@color/text_hint"
            android:textSize="@dimen/text_body" />

    </FrameLayout>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fab_new_note"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|end"
        android:layout_margin="@dimen/spacing_l"
        android:contentDescription="@string/fab_new_note_cd"
        android:src="@android:drawable/ic_input_add"
        app:backgroundTint="@color/primary"
        app:tint="@color/white" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

> `@android:drawable/ic_input_add` 是系统自带 + 号图标，M1 占位足够；M3 换成自定义 `ic_add.xml` vector。

- [ ] **Step 2: 写 NoteListActivity**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/main/kotlin/com/fan/hwnote/app/controller/list/NoteListActivity.kt`

```kotlin
package com.fan.hwnote.app.controller.list

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fan.hwnote.app.R
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NoteListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        findViewById<FloatingActionButton>(R.id.fab_new_note).setOnClickListener {
            Toast.makeText(this, R.string.toast_new_note_placeholder, Toast.LENGTH_SHORT).show()
        }
    }
}
```

---

## Task 9: 占位 ProGuard + 测试目录骨架

**Files:**
- Create: `app/proguard-rules.pro`
- Create: `app/src/test/kotlin/com/fan/hwnote/app/SmokeTest.kt`

- [ ] **Step 1: 写 ProGuard 占位**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/proguard-rules.pro`

```proguard
# MVP 不开混淆。预留此文件供 release buildType 引用。
# 真要混淆时再补规则（Glide / kotlinx.coroutines 等）。
```

- [ ] **Step 2: 写一个最小 SmokeTest 验证测试链路**

文件：`/Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote/app/src/test/kotlin/com/fan/hwnote/app/SmokeTest.kt`

```kotlin
package com.fan.hwnote.app

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class SmokeTest {

    @Test
    fun `arithmetic still works`() {
        assertEquals(2, 1 + 1)
    }
}
```

- [ ] **Step 3: 跑一下测试链路**

```bash
./gradlew :app:test
```

预期：1 个测试通过。**这一步会顺便验证 JUnit 5 + Gradle 集成正确**。

> 如果失败提示找不到 JUnit Platform，回到 Task 3 检查 `testOptions.unitTests.all { it.useJUnitPlatform() }` 是否漏掉。

---

## Task 10: 命令行构建验证

**Files:** 无新增

- [ ] **Step 1: 清理 + 全量构建**

```bash
cd /Users/yichen/cainiao/AI/ClaudeCode/HuaWeiNote
./gradlew clean
./gradlew :app:assembleDebug
```

预期：
- `BUILD SUCCESSFUL` 输出
- 产物路径：`app/build/outputs/apk/debug/app-debug.apk`
- 有警告可接受，无 ERROR

- [ ] **Step 2: 列出产物**

```bash
ls -lh app/build/outputs/apk/debug/
```

预期：看到 `app-debug.apk`，体积约 4-6 MB（Glide + Material 撑起来的）。

- [ ] **Step 3: 如失败的常见排查**

| 报错 | 可能原因 |
|------|---------|
| `Plugin [id: 'com.android.application', version: '8.5.2'] was not found` | 网络/镜像问题，`gradle.properties` 加 `org.gradle.daemon=false` 后重试 |
| `Could not resolve all files for configuration ':app:debugRuntimeClasspath'` | 同上，依赖下载失败；检查 google() / mavenCentral() 顺序 |
| `Unsupported class file major version` | JDK 版本不对，确保用 JDK 17 |
| `R.layout.activity_note_list 找不到` | layout 文件名拼写不对或没保存 |
| `cannot find symbol class App` | Manifest 里 `android:name=".App"` 与 App.kt 包名/类名不匹配 |

---

## Task 11: 安装真机 / 模拟器验证

**Files:** 无新增

- [ ] **Step 1: 启动模拟器或连真机**

```bash
adb devices
```

预期：列出至少一个 device。

- [ ] **Step 2: 安装 + 启动**

```bash
./gradlew :app:installDebug
adb shell am start -n com.fan.hwnote.app/.controller.list.NoteListActivity
```

预期：
- App 出现在桌面，名称 **备忘录**，图标是绿色块
- 启动后看到顶部绿色 ActionBar（青绿 #00897B）显示 "备忘录"
- 中间有灰色文字 "还没有笔记"
- 右下角绿色圆形 FAB 带 + 号
- 状态栏颜色为深青绿 #00695C

- [ ] **Step 3: 点 FAB 验证**

点右下绿色 FAB → 屏幕底部弹出 toast "新建笔记（待实现）"。

- [ ] **Step 4: logcat 检查无 crash**

```bash
adb logcat -d | grep -E "FATAL|AndroidRuntime" | tail -20
```

预期：没有与 `com.fan.hwnote.app` 相关的 FATAL/AndroidRuntime 行。

---

## Task 12: git 首次提交

**Files:** 无新增

- [ ] **Step 1: 检查工作区状态**

```bash
git status
```

预期：大量未跟踪文件，包括：
- `.gitignore` / `gradlew` / `gradle/`
- `build.gradle.kts` / `settings.gradle.kts` / `gradle.properties`
- `app/` 目录全部内容
- `docs/superpowers/` 全部已写文档
- `CLAUDE.md`（之前下载的）

不应有：
- `.gradle/` / `app/build/` / `local.properties` / `.idea/`（应该被 `.gitignore` 排除）

- [ ] **Step 2: 添加并提交**

```bash
git add .
git status   # 复检一遍 staged 文件
git commit -m "$(cat <<'EOF'
feat(m1): 基础工程骨架

- Gradle Kotlin DSL（root + :app 模块）
- AndroidManifest：App + NoteListActivity + FileProvider
- 主题色板（#00897B 青绿）+ strings + dimens
- App.kt（Application 子类，预留 NoteRepository 钩子）
- NoteListActivity 占位主屏：Toolbar + 空白容器 + FAB
- 启动器图标占位
- 测试目录骨架 + SmokeTest（验证 JUnit 5 链路）
- PRD / 高层计划 / M1 详细计划 / STATUS

构建已通过 :app:assembleDebug；真机启动验证完成。
EOF
)"
```

- [ ] **Step 3: 验证提交成功**

```bash
git log --oneline -5
```

预期：看到刚才的 commit。

- [ ] **Step 4: 更新 STATUS.md**

把 `docs/superpowers/STATUS.md` 中第 7 行的"7. 代码实施"状态从"🔵 待启动"改为"🟢 进行中（M1 完成）"，并在表格下加一行：

```markdown
## M1 完成（2026-05-22）

- ✅ Gradle 配置 + AndroidManifest + Application + 主题色板
- ✅ NoteListActivity 占位主屏 + FAB
- ✅ FileProvider authority 配好
- ✅ assembleDebug 通过 + 真机启动验证
- ⏭️ 下一步：M2 数据层（含 JVM/Robolectric 单测）
```

提交此变更：

```bash
git add docs/superpowers/STATUS.md
git commit -m "docs: 更新 STATUS 反映 M1 完成"
```

---

## M1 验收（人工 Checklist）

完成所有 Task 后，逐项确认：

- [ ] `./gradlew :app:assembleDebug` 在干净目录下能通过
- [ ] `./gradlew :app:test` 通过（SmokeTest 1 项）
- [ ] APK 安装后能启动，看到绿色顶栏 + "备忘录" + 空白主屏 + 绿色 FAB
- [ ] 状态栏色为 #00695C（深青绿）
- [ ] 点 FAB 弹 toast "新建笔记（待实现）"
- [ ] 包名为 `com.fan.hwnote.app`，App 名为 "备忘录"
- [ ] FileProvider authority `com.fan.hwnote.app.fileprovider` 已声明且 `xml/file_paths.xml` 存在
- [ ] git 仓库已初始化，`.gitignore` 正确排除 `build/` / `.idea/` 等
- [ ] 至少 1 个 commit 含 M1 全部代码 + 已写文档

---

## 不在 M1 内的事

- 数据库 / 文件存储：M2
- RecyclerView / 卡片样式：M3
- 任何编辑器内容：M4-M6
- 自定义启动器图标设计：v2 再做（MVP 用纯色块占位）
- 签名 / Release / 上架：MVP 用 debug 签名足够
