package gay.lilyy.lilypad.core.modules.modules.avatarpresets

import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import com.illposed.osc.OSCMessage
import gay.lilyy.lilypad.core.modules.Module
import gay.lilyy.lilypad.core.modules.Modules
import gay.lilyy.lilypad.core.modules.coremodules.gamestorage.GameStorage
import gay.lilyy.lilypad.core.osc.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class Preset(
    var name: String,
    var parameters: MutableMap<String, Parameter> = mutableMapOf()
)

@Serializable
data class Avatar(
    var name: String,
    val presets: MutableList<Preset> = mutableListOf()
)

@Serializable
data class AvatarPresetsConfig(
    val users: MutableMap<String, MutableMap<String, Avatar>> = mutableMapOf()
)

@Suppress("unused")
class AvatarPresets : Module<AvatarPresetsConfig>() {
    override val name = "Avatar Presets"

    override val configClass = AvatarPresetsConfig::class

    override val hasSettingsUI = true

    private suspend fun savePreset(index: Int) {
        if (Modules.Core.config!!.logs.debug) Napier.d("Saving preset $index")
        val rootNode = OSCQJson.getNode("/")
        if (rootNode === null) {
            if (Modules.Core.config!!.logs.errors) Napier.e("Failed to get root node")
            return
        }
        val parameters = mutableMapOf<String, Parameter>()

        fun recurseNode(node: ParameterNode) {
            if (node.contents != null) {
                for (content in node.contents) {
                    recurseNode(content.value)
                }
            } else {
                if (node.access !== Access.READ_WRITE) return
                if (node.value === null || node.value.isEmpty()) return
                parameters[node.fullPath] = node.value.first()
            }
        }
        recurseNode(rootNode)

        val gs = Modules.get<GameStorage>("GameStorage")!!
        config!!.users[gs.curUserId.value!!]!![gs.curAvatarId.value!!]!!.presets[index].parameters = parameters
        saveConfig()
    }

    private fun loadPreset(preset: Preset) {
        if (Modules.Core.config!!.logs.debug) Napier.d("Loading preset ${preset.name}")
        for (parameter in preset.parameters) {
            OSCSender.send(OSCMessage(parameter.key, listOf(parameter.value.any())))
        }
    }

    @Composable
    override fun onSettingsUI() {
        val gs = Modules.get<GameStorage>("GameStorage")!!
        val curAvatarId by remember { gs.curAvatarId }
        val curUserId by remember { gs.curUserId }

        val saveLoadScope = rememberCoroutineScope()

        if (curUserId === null) {
            Text(
                "Please log in to VRChat to use Avatar Presets",
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.h6
            )
            return
        }
        if (curAvatarId === null) {
            Text(
                "Loading your avatar...",
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.h6
            )
            return
        }
        if (config!!.users[curUserId] === null) {
            config!!.users[curUserId!!] = mutableMapOf()
        }

        val user = config!!.users[curUserId!!]!!
        if (user[curAvatarId] === null) {
            user[curAvatarId!!] = Avatar(curAvatarId!!)
        }

        var curAvatarName by remember { mutableStateOf(user[curAvatarId]!!.name) }

        TextField(
            value = curAvatarName,
            onValueChange = {
                user[curAvatarId]!!.name = it
                curAvatarName = it
                saveConfig()
            },
            label = { Text("Avatar Name") }
        )

        val presets = user[curAvatarId]!!.presets.toMutableStateList()

        for (preset in presets) {
            var presetName by remember { mutableStateOf(preset.name) }
            var enabled by remember { mutableStateOf(false) }
            Button(
                onClick = { enabled = !enabled }
            ) {
                Text(presetName)
            }
            if (enabled) {
                TextField(
                    value = presetName,
                    onValueChange = {
                        presetName = it
                        preset.name = it
                        config!!.users[curUserId]!![curAvatarId]!!.presets[presets.indexOf(preset)].name = it
                        saveConfig()
                    },
                    label = { Text("Preset Name") }
                )

                Button(
                    onClick = {
                        saveLoadScope.launch {
                            savePreset(config!!.users[curUserId]!![curAvatarId]!!.presets.indexOf(preset))
                        }
                    }
                ) {
                    Text("Save Preset")
                }

                Button(
                    onClick = {
                        saveLoadScope.launch {
                            loadPreset(preset)
                        }
                    }
                ) {
                    Text("Load Preset")
                }

                Button(
                    onClick = {
                        presets.remove(preset)
                        config!!.users[curUserId]!![curAvatarId]!!.presets.remove(preset)
                        saveConfig()
                    }
                ) {
                    Text("Delete Preset")
                }
            }
        }

        Button(
            onClick =
            {
                val preset = Preset("Preset ${presets.size + 1}")
                user[curAvatarId]!!.presets.add(preset)
                presets.add(preset)
                saveConfig()
                saveLoadScope.launch {
                    savePreset(presets.indexOf(preset))
                }
            }
        )
        {
            Text("New Preset")
        }
    }
}