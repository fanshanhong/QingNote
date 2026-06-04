package com.fan.hwnote.app.controller.list

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * M12 T5：NoteListActivity SharedPreferences 兼容性测试。
 *
 * 旧的 KEY_FILTER_TYPE 可能存有 "CATEGORY"/"UNCATEGORIZED"（M11 之前），
 * 新版 loadFilter 必须把这些值降级到 "ALL" 而非崩溃。
 *
 * 同时 "FOLDER" 持久化值必须能正常启动 Activity。
 */
@RunWith(RobolectricTestRunner::class)
class ListFilterPersistenceTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase("hwnote.db")
        ctx.getSharedPreferences("hwnote_settings", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun `legacy CATEGORY filter type does not crash on startup`() {
        // 旧版本会把 "CATEGORY" + filter_category_id 写到 prefs，新版应静默降级为 All
        ctx.getSharedPreferences("hwnote_settings", Context.MODE_PRIVATE)
            .edit().putString("filter_type", "CATEGORY").putLong("filter_category_id", 7L).apply()

        val controller = Robolectric.buildActivity(NoteListActivity::class.java)
        val activity = controller.create().get()
        check(activity != null)
        controller.destroy()
    }

    @Test
    fun `FOLDER filter type does not crash on startup`() {
        ctx.getSharedPreferences("hwnote_settings", Context.MODE_PRIVATE)
            .edit().putString("filter_type", "FOLDER").putLong("filter_folder_id", 1L).apply()

        val controller = Robolectric.buildActivity(NoteListActivity::class.java)
        val activity = controller.create().get()
        check(activity != null)
        controller.destroy()
    }
}
