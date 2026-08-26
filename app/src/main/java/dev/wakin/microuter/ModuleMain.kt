package dev.wakin.microuter

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class ModuleMain : XposedModule() {
    companion object {
        const val TAG = "MicRouter"
    }

    private var capturedContext: Context? = null
    private var router: SystemMicrophoneRouter? = null

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val preferences = getRemotePreferences(RouteStore.PREFS)
        val microphoneRouter = SystemMicrophoneRouter { priority, message ->
            log(priority, TAG, message)
        }
        router = microphoneRouter

        runCatching {
            val audioServiceClass = param.classLoader.loadClass("com.android.server.audio.AudioService")

            audioServiceClass.declaredConstructors.forEach { constructor ->
                hook(constructor).intercept { chain ->
                    chain.args.filterIsInstance<Context>().firstOrNull()?.let { capturedContext = it }
                    chain.proceed()
                }
            }

            audioServiceClass.declaredMethods
                .filter { it.name == "systemReady" && it.parameterCount == 0 }
                .forEach { systemReady ->
                    hook(systemReady).intercept { chain ->
                        val result = chain.proceed()
                        val context = capturedContext ?: contextFromAudioService(chain.thisObject)
                        if (context == null) {
                            log(Log.ERROR, TAG, "AudioService context unavailable; global routing was not started")
                        } else {
                            microphoneRouter.start(context, preferences)
                        }
                        result
                    }
                }
        }.onFailure {
            log(Log.ERROR, TAG, "AudioService system hook failed: $it")
        }
    }

    private fun contextFromAudioService(audioService: Any): Context? = runCatching {
        generateSequence(audioService.javaClass) { it.superclass }
            .mapNotNull { type ->
                type.declaredFields.firstOrNull { field ->
                    Context::class.java.isAssignableFrom(field.type)
                }
            }
            .firstOrNull()
            ?.also { it.isAccessible = true }
            ?.get(audioService) as? Context
    }.getOrNull()
}
