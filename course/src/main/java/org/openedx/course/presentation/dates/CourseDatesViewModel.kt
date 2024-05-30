package org.openedx.course.presentation.dates

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openedx.core.BaseViewModel
import org.openedx.core.R
import org.openedx.core.UIMessage
import org.openedx.core.config.Config
import org.openedx.core.data.storage.CorePreferences
import org.openedx.core.domain.model.Block
import org.openedx.core.domain.model.CourseBannerType
import org.openedx.core.domain.model.CourseDateBlock
import org.openedx.core.domain.model.CourseStructure
import org.openedx.core.extension.getSequentialBlocks
import org.openedx.core.extension.getVerticalBlocks
import org.openedx.core.extension.isInternetError
import org.openedx.core.presentation.settings.calendarsync.CalendarSyncDialogType
import org.openedx.core.presentation.settings.calendarsync.CalendarSyncUIState
import org.openedx.core.system.CalendarManager
import org.openedx.core.system.ResourceManager
import org.openedx.core.system.notifier.CalendarSyncEvent.CheckCalendarSyncEvent
import org.openedx.core.system.notifier.CalendarSyncEvent.CreateCalendarSyncEvent
import org.openedx.core.system.notifier.CourseDatesShifted
import org.openedx.core.system.notifier.CourseLoading
import org.openedx.core.system.notifier.CourseNotifier
import org.openedx.core.system.notifier.RefreshDates
import org.openedx.course.domain.interactor.CourseInteractor
import org.openedx.course.presentation.CourseAnalytics
import org.openedx.course.presentation.CourseAnalyticsEvent
import org.openedx.course.presentation.CourseAnalyticsKey
import org.openedx.course.presentation.CourseRouter
import org.openedx.core.R as CoreR

class CourseDatesViewModel(
    val courseId: String,
    courseTitle: String,
    private val enrollmentMode: String,
    private val courseNotifier: CourseNotifier,
    private val interactor: CourseInteractor,
    private val calendarManager: CalendarManager,
    private val resourceManager: ResourceManager,
    private val corePreferences: CorePreferences,
    private val courseAnalytics: CourseAnalytics,
    private val config: Config,
    val courseRouter: CourseRouter
) : BaseViewModel() {

    var isSelfPaced = true

    private val _uiState = MutableLiveData<DatesUIState>(DatesUIState.Loading)
    val uiState: LiveData<DatesUIState>
        get() = _uiState

    private val _uiMessage = MutableSharedFlow<UIMessage>()
    val uiMessage: SharedFlow<UIMessage>
        get() = _uiMessage.asSharedFlow()

    private val _calendarSyncUIState = MutableStateFlow(
        CalendarSyncUIState(
            isCalendarSyncEnabled = isCalendarSyncEnabled(),
            calendarTitle = calendarManager.getCourseCalendarTitle(courseTitle),
            isSynced = false,
        )
    )
    val calendarSyncUIState: StateFlow<CalendarSyncUIState> =
        _calendarSyncUIState.asStateFlow()

    private var courseBannerType: CourseBannerType = CourseBannerType.BLANK
    private var courseStructure: CourseStructure? = null

    val isCourseExpandableSectionsEnabled get() = config.isCourseDropdownNavigationEnabled()

    init {
        viewModelScope.launch {
            courseNotifier.notifier.collect { event ->
                when (event) {
                    is CheckCalendarSyncEvent -> {
                        _calendarSyncUIState.update { it.copy(isSynced = event.isSynced) }
                    }

                    is RefreshDates -> {
                        loadingCourseDatesInternal()
                    }
                }
            }
        }

        loadingCourseDatesInternal()
        updateAndFetchCalendarSyncState()
    }

    private fun loadingCourseDatesInternal() {
        viewModelScope.launch {
            try {
                courseStructure = interactor.getCourseStructure(courseId = courseId)
                isSelfPaced = courseStructure?.isSelfPaced ?: false
                val datesResponse = interactor.getCourseDates(courseId = courseId)
                if (datesResponse.datesSection.isEmpty()) {
                    _uiState.value = DatesUIState.Empty
                } else {
                    _uiState.value = DatesUIState.Dates(datesResponse)
                    courseBannerType = datesResponse.courseBanner.bannerType
                    checkIfCalendarOutOfDate()
                }
            } catch (e: Exception) {
                if (e.isInternetError()) {
                    _uiMessage.emit(UIMessage.SnackBarMessage(resourceManager.getString(CoreR.string.core_error_no_connection)))
                } else {
                    _uiMessage.emit(UIMessage.SnackBarMessage(resourceManager.getString(CoreR.string.core_error_unknown_error)))
                }
            } finally {
                courseNotifier.send(CourseLoading(false))
            }
        }
    }

    fun resetCourseDatesBanner(onResetDates: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                interactor.resetCourseDates(courseId = courseId)
                loadingCourseDatesInternal()
                courseNotifier.send(CourseDatesShifted)
                onResetDates(true)
            } catch (e: Exception) {
                if (e.isInternetError()) {
                    _uiMessage.emit(UIMessage.SnackBarMessage(resourceManager.getString(CoreR.string.core_error_no_connection)))
                } else {
                    _uiMessage.emit(UIMessage.SnackBarMessage(resourceManager.getString(R.string.core_dates_shift_dates_unsuccessful_msg)))
                }
                onResetDates(false)
            }
        }
    }

    fun getVerticalBlock(blockId: String): Block? {
        return try {
            courseStructure?.blockData?.getVerticalBlocks()
                ?.find { it.descendants.contains(blockId) }
        } catch (e: Exception) {
            null
        }
    }

    fun getSequentialBlock(blockId: String): Block? {
        return try {
            courseStructure?.blockData?.getSequentialBlocks()
                ?.find { it.descendants.contains(blockId) }
        } catch (e: Exception) {
            null
        }
    }

    fun handleCalendarSyncState(isChecked: Boolean) {
        logCalendarSyncToggle(isChecked)
        setCalendarSyncDialogType(
            when {
                isChecked && calendarManager.hasPermissions() -> CalendarSyncDialogType.SYNC_DIALOG
                isChecked -> CalendarSyncDialogType.PERMISSION_DIALOG
                else -> CalendarSyncDialogType.UN_SYNC_DIALOG
            }
        )
    }

    private fun updateAndFetchCalendarSyncState(): Boolean {
        val isCalendarSynced = calendarManager.isCalendarExists(
            calendarTitle = _calendarSyncUIState.value.calendarTitle
        )
        _calendarSyncUIState.update { it.copy(isSynced = isCalendarSynced) }
        return isCalendarSynced
    }

    private fun setCalendarSyncDialogType(dialog: CalendarSyncDialogType) {
        val value = _uiState.value
        if (value is DatesUIState.Dates) {
            viewModelScope.launch {
                courseNotifier.send(
                    CreateCalendarSyncEvent(
                        courseDates = value.courseDatesResult.datesSection.values.flatten(),
                        dialogType = dialog.name,
                        checkOutOfSync = false,
                    )
                )
            }
        }
    }

    private fun checkIfCalendarOutOfDate() {
        val value = _uiState.value
        if (value is DatesUIState.Dates) {
            viewModelScope.launch {
                courseNotifier.send(
                    CreateCalendarSyncEvent(
                        courseDates = value.courseDatesResult.datesSection.values.flatten(),
                        dialogType = CalendarSyncDialogType.NONE.name,
                        checkOutOfSync = true,
                    )
                )
            }
        }
    }

    private fun isCalendarSyncEnabled(): Boolean {
        val calendarSync = corePreferences.appConfig.courseDatesCalendarSync
        return calendarSync.isEnabled && ((calendarSync.isSelfPacedEnabled && isSelfPaced) ||
                (calendarSync.isInstructorPacedEnabled && !isSelfPaced))
    }

    fun logPlsBannerViewed() {
        logPLSBannerEvent(CourseAnalyticsEvent.PLS_BANNER_VIEWED)
    }

    fun logPlsShiftButtonClicked() {
        logPLSBannerEvent(CourseAnalyticsEvent.PLS_SHIFT_BUTTON_CLICKED)
    }

    fun logPlsShiftDates(isSuccess: Boolean) {
        logPLSBannerEvent(CourseAnalyticsEvent.PLS_SHIFT_DATES_SUCCESS, isSuccess)
    }

    fun logCourseComponentTapped(isSupported: Boolean, block: CourseDateBlock) {
        val params = buildMap<String, Any> {
            put(CourseAnalyticsKey.BLOCK_ID.key, block.blockId)
            put(CourseAnalyticsKey.BLOCK_TYPE.key, block.dateType)
            put(CourseAnalyticsKey.LINK.key, block.link)
            put(CourseAnalyticsKey.SUPPORTED.key, isSupported)
        }

        logDatesEvent(CourseAnalyticsEvent.DATES_COURSE_COMPONENT_CLICKED, params)
    }

    private fun logCalendarSyncToggle(isChecked: Boolean) {
        logDatesEvent(
            CourseAnalyticsEvent.DATES_CALENDAR_SYNC_TOGGLE,
            buildMap {
                put(
                    CourseAnalyticsKey.ACTION.key,
                    if (isChecked) CourseAnalyticsKey.ON.key else CourseAnalyticsKey.OFF.key
                )
            }
        )
    }

    private fun logDatesEvent(
        event: CourseAnalyticsEvent,
        param: Map<String, Any> = emptyMap(),
    ) {
        courseAnalytics.logEvent(
            event = event.eventName,
            params = buildMap {
                put(CourseAnalyticsKey.NAME.key, event.biValue)
                put(CourseAnalyticsKey.COURSE_ID.key, courseId)
                put(CourseAnalyticsKey.ENROLLMENT_MODE.key, enrollmentMode)
                put(
                    CourseAnalyticsKey.PACING.key,
                    if (isSelfPaced) CourseAnalyticsKey.SELF_PACED.key
                    else CourseAnalyticsKey.INSTRUCTOR_PACED.key
                )
                putAll(param)
            }
        )
    }

    private fun logPLSBannerEvent(
        event: CourseAnalyticsEvent,
        isSuccess: Boolean? = null,
    ) {
        courseAnalytics.logEvent(
            event = event.eventName,
            params = buildMap {
                put(CourseAnalyticsKey.NAME.key, event.biValue)
                put(CourseAnalyticsKey.CATEGORY.key, CourseAnalyticsKey.COURSE_DATES.key)
                put(CourseAnalyticsKey.COURSE_ID.key, courseId)
                put(CourseAnalyticsKey.ENROLLMENT_MODE.key, enrollmentMode)
                put(CourseAnalyticsKey.BANNER_TYPE.key, courseBannerType.name)
                put(CourseAnalyticsKey.SCREEN_NAME.key, CourseAnalyticsKey.COURSE_DATES.key)
                isSuccess?.let { put(CourseAnalyticsKey.SUCCESS.key, it) }
            }
        )
    }
}
