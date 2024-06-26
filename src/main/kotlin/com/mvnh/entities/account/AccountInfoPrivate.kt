package com.mvnh.entities.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountInfoPrivate(
    @SerialName("account_id") val accountID: String,
    val token: String,
    val nickname: String,
    @SerialName("visible_name") val visibleName: AccountVisibleName?,
    val about: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val email: String,
    @SerialName("music_preferences") val musicPreferences: List<String>? = null,
    @SerialName("other_preferences") val otherPreferences: List<String>? = null,
    @SerialName("last_tracks") val lastTracks: AccountLastTracks? = null,
    @SerialName("friends") val friends: List<String>? = null,
    @SerialName("created_at") val createdAt: String
)
