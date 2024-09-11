package gay.lilyy.lilypad.core.modules.coremodules.chatbox

import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import com.illposed.osc.OSCMessage
import gay.lilyy.lilypad.core.modules.Modules
import gay.lilyy.lilypad.core.modules.coremodules.core.Core
import gay.lilyy.lilypad.core.osc.OSCSender
import io.github.aakira.napier.Napier

const val maxLines = 9
const val maxChars = 144

@Suppress("unused")
class Chatbox : ChatboxModule<ChatboxConfig>() {
    override val name = "Chatbox"

    override val configClass = ChatboxConfig::class

    private fun build() {
        if (!config!!.enabled) return
        val modules = Modules.modules.values.filterIsInstance<ChatboxModule<*>>().sortedBy { it.chatboxBuildOrder }

        val outputs = mutableListOf<String>()

        for (module in modules) {
            outputs += module.buildChatbox() ?: continue
        }

        val lines: MutableList<String> = mutableListOf()

        for (output in outputs) {
            // You are limited to 9 lines and 144 characters total. trimByModule will not add that module to the chatbox if it pushes you over the limit. trimByLine will not add any extra lines that go over the limit, but will include the rest of the module
            if (config!!.trimByLine) {
                if (lines.size + output.lines().size > maxLines || lines.sumBy { it.length } + output.length > maxChars) {
                    val remaining = maxLines - lines.size
                    lines += output.lines().take(remaining)
                    break
                }
            } else if (config!!.trimByModule) {
                if (lines.size + output.lines().size > maxLines || lines.sumBy { it.length } + output.length > maxChars) {
                    break
                }
            }

            lines += output
        }

        val chatbox = lines.joinToString("\n")
        if (Modules.get<Core>("Core")!!.config!!.logs.outgoingChatbox) Napier.v(chatbox)
        OSCSender.send(OSCMessage("/chatbox/input", listOf(chatbox)))
    }

    private fun loopBuildChatbox() {
        while (true) {
            build()
            Thread.sleep(config!!.updateInterval.toLong())
        }
    }

    fun clearChatbox(onlyIfDisabled: Boolean = false) {
        if (onlyIfDisabled && config!!.enabled) return
        if (Modules.get<Core>("Core")!!.config!!.logs.outgoingChatbox) Napier.v("Clearing chatbox")
        OSCSender.send(
            OSCMessage(
                "/chatbox/input", listOf(
                    ""
                )
            )
        )
        // Sometimes vrchat just ignores the empty message, so we send it again
        Thread {
            Thread.sleep(250)
            if (onlyIfDisabled && config!!.enabled) return@Thread
                OSCSender.send(
                    OSCMessage(
                        "/chatbox/input", listOf(
                            ""
                        )
                    )
                )
        }.start()
    }

    override val hasSettingsUI = true

    @Composable
    override fun onSettingsUI() {
        var enabled by remember { mutableStateOf(config!!.enabled) }
        var updateInterval by remember { mutableStateOf(config!!.updateInterval) }

        Text("Enabled")
        Checkbox(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                config!!.enabled = it
                if (!it) {
                    clearChatbox()
                }
                saveConfig()
            },
        )

        Text("Update Interval")
        TextField(
            value = updateInterval.toString(),
            onValueChange = { updateInterval = it.toIntOrNull() ?: 0 },
        )
    }

    init {
        init()

        Thread {
            loopBuildChatbox()
        }.start()
    }
}