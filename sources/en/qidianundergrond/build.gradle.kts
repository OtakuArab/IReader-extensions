listOf("en").map { lang ->
  Extension(
    name = "QidianUnderground",
    versionCode = 2,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
