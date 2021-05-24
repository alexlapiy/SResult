package config

object Builds {
    const val MIN_VERSION = 19
    const val COMPILE_VERSION = 30
    const val TARGET_VERSION = 30
    const val BUILD_TOOLS = "30.0.2"
    const val APP_ID = "com.rasalexman.sresultexample"

    val codeDirs = arrayListOf(
        "src/main/kotlin"
    )

    object App {
        const val VERSION_CODE = 10001
        const val VERSION_NAME = "10001"
    }

    object SResult {
        const val VERSION_CODE = 10001
        const val VERSION_NAME = "10001"
    }
}