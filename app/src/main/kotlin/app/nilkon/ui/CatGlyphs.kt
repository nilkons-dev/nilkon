package app.nilkon.ui

/** Cat-button glyphs, paw and whiskers copied from the desktop player. */
val BTN_ICONS: Map<String, String> = mapOf(
    "shuffle" to """<path d="M16 3h5v5l-1.8-1.8-4.4 4.4-1.4-1.4 4.4-4.4zM3 6h4.5l9.6 12H21v2h-5.9L6.5 8H3zM3 18h3.5l2-2.5 1.4 1.7L7.5 20H3zm13.6-4.6 1.4-1.4 3 3V21h-5l1.8-1.8z"/>""",
    "prev" to """<rect x="5" y="6" width="2.4" height="12" rx="1"/><path d="M19 6.5v11L9.5 12z"/>""",
    "next" to """<rect x="16.6" y="6" width="2.4" height="12" rx="1"/><path d="M5 6.5v11L14.5 12z"/>""",
    "play" to """<path d="M8 5v14l11-7z"/>""",
    "pause" to """<path d="M7 5h3.5v14H7zm6.5 0H17v14h-3.5z"/>""",
    "repeat" to """<path d="M7 7h10v3l4-4-4-4v3H5v6h2zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2z"/>""",
)
const val PAW_SVG = """<ellipse cx="12" cy="16" rx="6" ry="5"/><circle cx="5" cy="9" r="2.4"/><circle cx="9.5" cy="4.5" r="2.4"/><circle cx="14.5" cy="4.5" r="2.4"/><circle cx="19" cy="9" r="2.4"/>"""
const val WHISK_PATH = "M3 6l9 4M2 12h10M3 18l9-4M91 6l-9 4M92 12H82M91 18l-9-4"
