package org.openedx.profile.data.repository

import androidx.room.RoomDatabase
import org.openedx.core.ApiConstants
import org.openedx.profile.data.api.ProfileApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.openedx.core.data.storage.CorePreferences
import org.openedx.profile.data.storage.ProfilePreferences
import java.io.File
import org.openedx.profile.domain.model.Account

class ProfileRepository(
    private val api: ProfileApi,
    private val room: RoomDatabase,
    private val profilePreferences: ProfilePreferences,
    private val corePreferences: CorePreferences,
) {

    suspend fun getAccount(): Account {
        val account = api.getAccount(corePreferences.user?.username!!)
        profilePreferences.profile = account
        return account.mapToDomain()
    }

    fun getCachedAccount() : Account? {
        return profilePreferences.profile?.mapToDomain()
    }

    suspend fun updateAccount(fields: Map<String, Any?>): Account {
        return api.updateAccount(corePreferences.user?.username!!, fields).mapToDomain()
    }

    suspend fun setProfileImage(file: File, mimeType: String) {
        api.setProfileImage(
            corePreferences.user?.username!!,
            "attachment;filename=filename.${file.extension}",
            true,
            file.asRequestBody(mimeType.toMediaType())
        )
    }

    suspend fun deleteProfileImage() {
        api.deleteProfileImage(corePreferences.user?.username!!)
    }

    suspend fun deactivateAccount(password: String) = api.deactivateAccount(password)

    suspend fun logout() {
        api.revokeAccessToken(
            org.openedx.core.BuildConfig.CLIENT_ID,
            corePreferences.refreshToken,
            ApiConstants.TOKEN_TYPE_REFRESH
        )
        corePreferences.clear()
        room.clearAllTables()
    }
}