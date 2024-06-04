package org.openedx.profile.presentation.calendar

import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openedx.core.BaseViewModel
import org.openedx.core.data.storage.CalendarPreferences
import org.openedx.core.system.connection.NetworkConnection
import org.openedx.profile.presentation.ProfileRouter
import org.openedx.profile.system.CalendarManager
import org.openedx.profile.system.CalendarSyncServiceInitiator
import org.openedx.profile.system.notifier.CalendarCreated
import org.openedx.profile.system.notifier.CalendarNotifier
import org.openedx.profile.system.notifier.CalendarSyncFailed
import org.openedx.profile.system.notifier.CalendarSynced
import org.openedx.profile.system.notifier.CalendarSyncing

class CalendarViewModel(
    private val calendarSyncServiceInitiator: CalendarSyncServiceInitiator,
    private val calendarManager: CalendarManager,
    private val calendarPreferences: CalendarPreferences,
    private val calendarNotifier: CalendarNotifier,
    private val networkConnection: NetworkConnection,
    private val profileRouter: ProfileRouter
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(
        CalendarUIState(
            isCalendarExist = calendarPreferences.calendarId != CalendarManager.CALENDAR_DOES_NOT_EXIST,
            calendarData = null,
            calendarSyncState = if (networkConnection.isOnline()) CalendarSyncState.SYNCED else CalendarSyncState.OFFLINE,
            isCalendarSyncEnabled = calendarPreferences.isCalendarSyncEnabled,
            isRelativeDateEnabled = calendarPreferences.isRelativeDateEnabled
        )
    )
    val uiState: StateFlow<CalendarUIState>
        get() = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            calendarNotifier.notifier.collect { calendarEvent ->
                when (calendarEvent) {
                    CalendarCreated -> {
                        calendarSyncServiceInitiator.startSyncCalendarService()
                        _uiState.update { it.copy(isCalendarExist = true) }
                        getCalendarData()
                    }

                    CalendarSyncing -> {
                        _uiState.update { it.copy(calendarSyncState = CalendarSyncState.SYNCHRONIZATION) }
                    }

                    CalendarSynced -> {
                        _uiState.update { it.copy(calendarSyncState = CalendarSyncState.SYNCED) }
                    }

                    CalendarSyncFailed -> {
                        _uiState.update { it.copy(calendarSyncState = CalendarSyncState.SYNC_FAILED) }
                    }
                }
            }
        }

        getCalendarData()
    }

    fun setUpCalendarSync(permissionLauncher: ActivityResultLauncher<Array<String>>) {
        permissionLauncher.launch(calendarManager.permissions)
    }

    fun setCalendarSyncEnabled(isEnabled: Boolean) {
        calendarPreferences.isCalendarSyncEnabled = isEnabled
        _uiState.update { it.copy(isCalendarSyncEnabled = isEnabled) }
        if (isEnabled) {
            calendarSyncServiceInitiator.startSyncCalendarService()
        }
    }

    fun setRelativeDateEnabled(isEnabled: Boolean) {
        calendarPreferences.isRelativeDateEnabled = isEnabled
        _uiState.update { it.copy(isRelativeDateEnabled = isEnabled) }
    }

    fun navigateToCoursesToSync(fragmentManager: FragmentManager) {
        profileRouter.navigateToCoursesToSync(fragmentManager)
    }

    private fun getCalendarData() {
        if (calendarManager.hasPermissions()) {
            val calendarData = calendarManager.getCalendarData(calendarId = calendarPreferences.calendarId)
            _uiState.update { it.copy(calendarData = calendarData) }
        }
    }
}
