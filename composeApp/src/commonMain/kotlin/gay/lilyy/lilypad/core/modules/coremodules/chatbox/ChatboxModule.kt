package gay.lilyy.lilypad.core.modules.coremodules.chatbox

import gay.lilyy.lilypad.core.modules.Module

abstract class ChatboxModule: Module() {
    /**
     * The order in which the chatbox is built, relative to other modules.
     * If multiple modules have the same order, the order in which they are added to the modules list will be used.
     */
    open val chatboxBuildOrder = 0

    /**
     * Called to build the chatbox.
     * @return A string to be appended to the chatbox.
     */
    open fun onChatboxBuild(): String? {
        return null
    }
}