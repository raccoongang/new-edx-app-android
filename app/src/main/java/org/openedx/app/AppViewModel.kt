package org.openedx.app

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openedx.app.system.notifier.AppNotifier
import org.openedx.app.system.notifier.LogoutEvent
import org.openedx.core.BaseViewModel
import org.openedx.core.SingleEventLiveData
import org.openedx.core.config.Config
import org.openedx.core.data.storage.CorePreferences
import org.openedx.core.utils.FileUtil

class AppViewModel(
    private val config: Config,
    private val notifier: AppNotifier,
    private val room: RoomDatabase,
    private val preferencesManager: CorePreferences,
    private val dispatcher: CoroutineDispatcher,
    private val analytics: AppAnalytics,
) : BaseViewModel() {

    private val _logoutUser = SingleEventLiveData<Unit>()
    val logoutUser: LiveData<Unit>
        get() = _logoutUser

    val isLogistrationEnabled get() = config.isPreLoginExperienceEnabled()

    private var logoutHandledAt: Long = 0

    val isBranchEnabled get() = config.getBranchConfig().enabled
    private val canResetAppDirectory get() = preferencesManager.canResetAppDirectory

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        setUserId()
        if(canResetAppDirectory) {
            resetAppDirectory(owner as Context)
        }
        viewModelScope.launch {
            notifier.notifier.collect { event ->
                if (event is LogoutEvent && System.currentTimeMillis() - logoutHandledAt > 5000) {
                    logoutHandledAt = System.currentTimeMillis()
                    preferencesManager.clear()
                    withContext(dispatcher) {
                        room.clearAllTables()
                    }
                    analytics.logoutEvent(true)
                    _logoutUser.value = Unit
                }
            }
        }
    }

    fun logAppLaunchEvent() {
        analytics.logEvent(
            event = AppAnalyticsEvent.LAUNCH.eventName,
            params = buildMap {
                put(AppAnalyticsKey.NAME.key, AppAnalyticsEvent.LAUNCH.biValue)
            }
        )
    }

    private fun resetAppDirectory(context: Context) {
        FileUtil.deleteOldAppDirectory(context)
        preferencesManager.canResetAppDirectory = false
    }

    private fun setUserId() {
        preferencesManager.user?.let {
            analytics.setUserIdForSession(it.id)
        }
    }
}
