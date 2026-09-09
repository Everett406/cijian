package com.cijian.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cijian.app.core.Segmenter
import com.cijian.app.core.Word2VecEngine
import com.cijian.app.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

sealed interface EngineState {
    data class Loading(val progress: Float) : EngineState
    data object Ready : EngineState
    data class Error(val message: String) : EngineState
}

class CiJianApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engine = Word2VecEngine()

    private val _state = mutableStateOf<EngineState>(EngineState.Loading(0f))
    val state: EngineState by _state

    val store: Store by lazy { Store(this) }

    val segmenter: Segmenter? by lazy {
        if (engine.size > 0) Segmenter(engine.words.toSet()) else null
    }

    override fun onCreate() {
        super.onCreate()
        loadModel()
    }

    fun loadModel() {
        _state.value = EngineState.Loading(0f)
        appScope.launch {
            try {
                engine.load(this@CiJianApplication) { p ->
                    _state.value = EngineState.Loading(p * 0.95f)
                }
                _state.value = EngineState.Ready
            } catch (e: Exception) {
                _state.value = EngineState.Error(e.message ?: "模型加载失败")
            }
        }
    }
}

val Context.ciJian: CiJianApplication
    get() = applicationContext as CiJianApplication
