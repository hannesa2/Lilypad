package gay.lilyy.lilypad

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform