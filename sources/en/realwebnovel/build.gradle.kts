listOf("en").map { lang ->
  Extension(
    name = "RealWebNovel",
    versionCode = 6,
    libVersion = "1",
    lang = lang,
    description = "",
    nsfw = false,
  )
}.also(::register)
