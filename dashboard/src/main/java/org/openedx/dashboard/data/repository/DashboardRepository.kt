package org.openedx.dashboard.data.repository

import org.openedx.core.data.api.CourseApi
import org.openedx.core.data.storage.CorePreferences
import org.openedx.core.domain.model.DashboardCourseList
import org.openedx.core.domain.model.EnrolledCourse
import org.openedx.courses.domain.model.UserCourses
import org.openedx.dashboard.data.DashboardDao

class DashboardRepository(
    private val api: CourseApi,
    private val dao: DashboardDao,
    private val preferencesManager: CorePreferences
) {

    suspend fun getEnrolledCourses(page: Int): DashboardCourseList {
        val user = preferencesManager.user
        val result = api.getEnrolledCourses(
            username = user?.username ?: "",
            page = page
        )
        preferencesManager.appConfig = result.configs.mapToDomain()

        if (page == 1) dao.clearCachedData()
        dao.insertEnrolledCourseEntity(*result.enrollments.results.map { it.mapToRoomEntity() }
            .toTypedArray())
        return result.enrollments.mapToDomain()
    }

    suspend fun getEnrolledCoursesFromCache(): List<EnrolledCourse> {
        val list = dao.readAllData()
        return list.map { it.mapToDomain() }
    }

    suspend fun getUserCourses(): UserCourses {
        val user = preferencesManager.user
        val result = api.getUserCourses(
            username = user?.username ?: ""
        )
        preferencesManager.appConfig = result.configs.mapToDomain()

        dao.clearCachedData()
        dao.insertEnrolledCourseEntity(*result.enrollments.results.map { it.mapToRoomEntity() }
            .toTypedArray())
        return UserCourses(
            enrollments = result.enrollments.mapToDomain(),
            primary = result.primary?.mapToDomain()
        )
    }
}
