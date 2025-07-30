package dev.jdtech.jellyfin.models

enum class FilterBy {
    NONE,
    GENRE,
    YEAR;

    companion object {
        val defaultValue = NONE

        fun fromString(string: String): FilterBy {
            return try {
                valueOf(string)
            } catch (e: IllegalArgumentException) {
                defaultValue
            }
        }
    }
}