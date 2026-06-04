package com.fan.hwnote.app

import android.app.Application

/**
 * 单元测试专用 Application：跳过 [App.onCreate] 里的 [com.fan.hwnote.app.model.NoteRepository.purgeExpired]
 * GlobalScope.launch — 那条协程会打开一个永不关闭的 SQLite 连接，跨用例残留导致
 * "table notes already exists" / "Can't downgrade" 偶发失败。测试各自在 @Before 里
 * 调 NoteRepository.init() / CategoryRepository.init()，不依赖 App.onCreate。
 */
class TestApp : Application()
