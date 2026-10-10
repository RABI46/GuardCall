package com.guardcall

import android.app.Application
import com.guardcall.data.BlocklistStore
import com.guardcall.data.HistoryStore
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class GuardCallApp : Application() {
    val blocklistStore by lazy { BlocklistStore(this) }
    val historyStore by lazy { HistoryStore(File(filesDir, HistoryStore.FILE_NAME)) }
    // Bounded, independent from screening. Drop history work rather than delay responses.
    val historyExecutor by lazy {
        ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(32), ThreadPoolExecutor.DiscardPolicy())
    }
}
