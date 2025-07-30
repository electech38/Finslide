package dev.jdtech.jellyfin.models

interface Preference {
    val nameStringResource: Int
    val descriptionStringRes: Int?
    val iconDrawableId: Int?
    val enabled: Boolean
    val dependencies: List<String>
}
