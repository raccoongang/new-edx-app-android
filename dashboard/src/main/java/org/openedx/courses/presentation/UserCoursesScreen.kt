package org.openedx.courses.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.openedx.core.UIMessage
import org.openedx.core.domain.model.Certificate
import org.openedx.core.domain.model.CourseSharingUtmParameters
import org.openedx.core.domain.model.CoursewareAccess
import org.openedx.core.domain.model.DashboardCourseList
import org.openedx.core.domain.model.EnrolledCourse
import org.openedx.core.domain.model.EnrolledCourseData
import org.openedx.core.domain.model.Pagination
import org.openedx.core.domain.model.Progress
import org.openedx.core.ui.HandleUIMessage
import org.openedx.core.ui.theme.OpenEdXTheme
import org.openedx.core.ui.theme.appColors
import org.openedx.core.ui.theme.appShapes
import org.openedx.core.ui.theme.appTypography
import org.openedx.core.utils.TimeUtils
import org.openedx.courses.domain.model.UserCourses
import org.openedx.dashboard.R
import java.util.Date

@Composable
fun UsersCourseScreen(
    viewModel: UserCoursesViewModel,
    onItemClick: (EnrolledCourse) -> Unit,
) {
    val updating by viewModel.updating.observeAsState(false)
    val uiMessage by viewModel.uiMessage.collectAsState(null)
    val uiState by viewModel.uiState.observeAsState(UserCoursesUIState.Loading)

    UsersCourseScreen(
        uiMessage = uiMessage,
        uiState = uiState,
        updating = updating,
        apiHostUrl = viewModel.apiHostUrl,
        onSwipeRefresh = viewModel::updateCoursed,
        onItemClick = onItemClick
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun UsersCourseScreen(
    uiMessage: UIMessage?,
    uiState: UserCoursesUIState,
    updating: Boolean,
    apiHostUrl: String,
    onSwipeRefresh: () -> Unit,
    onItemClick: (EnrolledCourse) -> Unit,
) {
    val scaffoldState = rememberScaffoldState()
    val pullRefreshState = rememberPullRefreshState(refreshing = updating, onRefresh = { onSwipeRefresh })
    val scrollState = rememberLazyListState()

    Scaffold(
        scaffoldState = scaffoldState,
        modifier = Modifier.fillMaxSize(),
        backgroundColor = MaterialTheme.appColors.background
    ) { paddingValues ->
        HandleUIMessage(uiMessage = uiMessage, scaffoldState = scaffoldState)

        Surface(
            modifier = Modifier.padding(paddingValues),
            color = MaterialTheme.appColors.background
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState),
            ) {
                when (uiState) {
                    is UserCoursesUIState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.appColors.primary
                        )
                    }

                    is UserCoursesUIState.Courses -> {
                        UserCourses(
                            modifier = Modifier.fillMaxSize(),
                            userCourses = uiState.userCourses,
                            apiHostUrl = apiHostUrl,
                            scrollState = scrollState
                        )
                    }

                    is UserCoursesUIState.Empty -> {
                        EmptyState()
                    }
                }

                PullRefreshIndicator(
                    updating,
                    pullRefreshState,
                    Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun UserCourses(
    modifier: Modifier = Modifier,
    userCourses: UserCourses,
    scrollState: LazyListState,
    apiHostUrl: String
) {
    LazyColumn(
        modifier = modifier,
        state = scrollState
    ) {
        if (userCourses.primary != null) {
            item {
                PrimaryCourseCard(
                    primaryCourse = userCourses.primary,
                    apiHostUrl = apiHostUrl
                )
            }
        }
    }
}

@Composable
private fun PrimaryCourseCard(
    primaryCourse: EnrolledCourse,
    apiHostUrl: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        backgroundColor = MaterialTheme.appColors.background,
        shape = MaterialTheme.appShapes.courseImageShape,
        elevation = 4.dp
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(apiHostUrl + primaryCourse.course.courseImage)
                    .error(org.openedx.core.R.drawable.core_no_image_course)
                    .placeholder(org.openedx.core.R.drawable.core_no_image_course)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = primaryCourse.progress.numPointsEarned.toFloat(),
                color = MaterialTheme.appColors.primary,
                backgroundColor = MaterialTheme.appColors.divider
            )
            PrimaryCourseTitle(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                primaryCourse = primaryCourse
            )
        }
    }
}

@Composable
fun PrimaryCourseTitle(
    modifier: Modifier = Modifier,
    primaryCourse: EnrolledCourse
) {
    Column(
        modifier = modifier
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = primaryCourse.course.org,
            style = MaterialTheme.appTypography.labelMedium,
            color = MaterialTheme.appColors.textFieldHint
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = primaryCourse.course.name,
            style = MaterialTheme.appTypography.titleLarge,
            color = MaterialTheme.appColors.textDark
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.appTypography.labelMedium,
            color = MaterialTheme.appColors.textFieldHint,
            text = stringResource(
                R.string.dashboard_course_date,
                TimeUtils.getCourseFormattedDate(
                    LocalContext.current,
                    Date(),
                    primaryCourse.auditAccessExpires,
                    primaryCourse.course.start,
                    primaryCourse.course.end,
                    primaryCourse.course.startType,
                    primaryCourse.course.startDisplay
                )
            )
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(185.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.dashboard_ic_empty),
                contentDescription = null,
                tint = MaterialTheme.appColors.textFieldBorder
            )
            Spacer(Modifier.height(16.dp))
            Text(
                modifier = Modifier
                    .testTag("txt_empty_state_description")
                    .fillMaxWidth(),
                text = stringResource(id = R.string.dashboard_you_are_not_enrolled),
                color = MaterialTheme.appColors.textPrimaryVariant,
                style = MaterialTheme.appTypography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val mockCourse = EnrolledCourse(
    auditAccessExpires = Date(),
    created = "created",
    certificate = Certificate(""),
    mode = "mode",
    isActive = true,
    progress = Progress.DEFAULT_PROGRESS,
    course = EnrolledCourseData(
        id = "id",
        name = "Course name",
        number = "",
        org = "Org",
        start = Date(),
        startDisplay = "",
        startType = "",
        end = Date(),
        dynamicUpgradeDeadline = "",
        subscriptionId = "",
        coursewareAccess = CoursewareAccess(
            true,
            "",
            "",
            "",
            "",
            ""
        ),
        media = null,
        courseImage = "",
        courseAbout = "",
        courseSharingUtmParameters = CourseSharingUtmParameters("", ""),
        courseUpdates = "",
        courseHandouts = "",
        discussionUrl = "",
        videoOutline = "",
        isSelfPaced = false,
    )
)

private val mockPagination = Pagination(10, "", 4, "1")
private val mockDashboardCourseList = DashboardCourseList(
    pagination = mockPagination,
    courses = listOf(mockCourse, mockCourse, mockCourse, mockCourse, mockCourse, mockCourse)
)

private val mockUserCourses = UserCourses(
    enrollments = mockDashboardCourseList,
    primary = mockCourse
)

@Preview
@Composable
private fun UsersCourseScreenPreview() {
    OpenEdXTheme {
        UsersCourseScreen(
            uiState = UserCoursesUIState.Courses(mockUserCourses),
            apiHostUrl = "",
            uiMessage = null,
            updating = false,
            onSwipeRefresh = { },
            onItemClick = { }
        )
    }
}