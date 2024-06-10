package org.openedx.profile.presentation.calendar

import androidx.annotation.StringRes
import org.openedx.core.R

enum class SyncCourseTab(
    @StringRes
    val title: Int
) {
    SYNCED(R.string.core_synced),
    NOT_SYNCED(R.string.core_not_synced)
}
