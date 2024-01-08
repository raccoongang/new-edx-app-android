package org.openedx.dashboard.presentation.program

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.openedx.core.UIMessage
import org.openedx.core.presentation.catalog.CatalogWebViewScreen
import org.openedx.core.presentation.catalog.WebViewLink
import org.openedx.core.presentation.dialog.alert.ActionDialogFragment
import org.openedx.core.presentation.dialog.alert.InfoDialogFragment
import org.openedx.core.ui.ConnectionErrorView
import org.openedx.core.ui.HandleUIMessage
import org.openedx.core.ui.ToolbarWithBackBtn
import org.openedx.core.ui.WindowSize
import org.openedx.core.ui.WindowType
import org.openedx.core.ui.displayCutoutForLandscape
import org.openedx.core.ui.rememberWindowSize
import org.openedx.core.ui.statusBarsInset
import org.openedx.core.ui.theme.OpenEdXTheme
import org.openedx.core.ui.theme.appColors
import org.openedx.core.ui.windowSizeValue
import org.openedx.dashboard.R
import org.openedx.core.R as coreR
import org.openedx.core.presentation.catalog.WebViewLink.Authority as linkAuthority
import org.openedx.course.R as courseR

class ProgramFragment : Fragment() {

    private val viewModel by viewModel<ProgramViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OpenEdXTheme {
                val windowSize = rememberWindowSize()
                val uiMessage by viewModel.uiMessage.collectAsState(initial = null)
                val showAlert by viewModel.showAlert.collectAsState(initial = false)
                val showLoading by viewModel.showLoading.collectAsState(initial = true)
                val enrollmentSuccess by viewModel.courseEnrollSuccess.collectAsState(initial = "")
                var hasInternetConnection by remember {
                    mutableStateOf(viewModel.hasInternetConnection)
                }

                LaunchedEffect(showAlert) {
                    if (showAlert) {
                        InfoDialogFragment.newInstance(
                            title = context.getString(courseR.string.course_enrollment_error),
                            message = context.getString(
                                courseR.string.course_enrollment_error_message,
                                getString(coreR.string.platform_name)
                            )
                        ).show(
                            requireActivity().supportFragmentManager,
                            InfoDialogFragment::class.simpleName
                        )
                    }
                }

                LaunchedEffect(enrollmentSuccess) {
                    if (enrollmentSuccess.isNotEmpty()) {
                        viewModel.onEnrolledCourseClick(
                            fragmentManager = requireActivity().supportFragmentManager,
                            courseId = enrollmentSuccess,
                        )
                    }
                }

                ProgramInfoScreen(
                    windowSize = windowSize,
                    canShowLoading = showLoading,
                    uiMessage = uiMessage,
                    contentUrl = getInitialUrl(),
                    canShowBackBtn = arguments?.getString(ARG_PATH_ID, "")
                        ?.isNotEmpty() == true,
                    uriScheme = viewModel.uriScheme,
                    hasInternetConnection = hasInternetConnection,
                    checkInternetConnection = {
                        hasInternetConnection = viewModel.hasInternetConnection
                    },
                    onBackClick = {
                        requireActivity().supportFragmentManager.popBackStackImmediate()
                    },
                    onURLClick = { param, type ->
                        when (type) {
                            linkAuthority.ENROLLED_COURSE_INFO -> {
                                viewModel.onEnrolledCourseClick(
                                    fragmentManager = requireActivity().supportFragmentManager,
                                    courseId = param
                                )
                            }

                            linkAuthority.ENROLLED_PROGRAM_INFO -> {
                                viewModel.onProgramCardClick(
                                    fragmentManager = requireActivity().supportFragmentManager,
                                    pathId = param
                                )
                            }

                            linkAuthority.COURSE_INFO -> {
                                viewModel.onViewCourseClick(
                                    fragmentManager = requireActivity().supportFragmentManager,
                                    courseId = param,
                                    infoType = type.name
                                )
                            }

                            linkAuthority.ENROLL -> {
                                viewModel.enrollInACourse(param)
                            }

                            linkAuthority.EXTERNAL -> {
                                ActionDialogFragment.newInstance(
                                    title = getString(coreR.string.core_leaving_the_app),
                                    message = getString(
                                        coreR.string.core_leaving_the_app_message,
                                        getString(coreR.string.platform_name)
                                    ),
                                    url = param,
                                ).show(
                                    requireActivity().supportFragmentManager,
                                    ActionDialogFragment::class.simpleName
                                )
                            }

                            else -> {}
                        }
                    },
                    refreshSessionCookie = {
                        viewModel.refreshCookie()
                    },
                )
            }
        }
    }

    private fun getInitialUrl(): String {
        return arguments?.let { args ->
            val pathId = args.getString(ARG_PATH_ID) ?: ""
            viewModel.programConfig.programDetailUrlTemplate.replace("{$ARG_PATH_ID}", pathId)
        } ?: viewModel.programConfig.programUrl
    }

    companion object {
        private const val ARG_PATH_ID = "path_id"

        fun newInstance(
            pathId: String,
        ): ProgramFragment {
            val fragment = ProgramFragment()
            fragment.arguments = bundleOf(
                ARG_PATH_ID to pathId,
            )
            return fragment
        }
    }
}

@Composable
private fun ProgramInfoScreen(
    windowSize: WindowSize,
    canShowLoading: Boolean,
    uiMessage: UIMessage?,
    contentUrl: String,
    uriScheme: String,
    canShowBackBtn: Boolean,
    hasInternetConnection: Boolean,
    checkInternetConnection: () -> Unit,
    onBackClick: () -> Unit,
    onURLClick: (String, WebViewLink.Authority) -> Unit,
    refreshSessionCookie: () -> Unit = {},
) {
    val scaffoldState = rememberScaffoldState()
    val configuration = LocalConfiguration.current
    var isLoading by remember { mutableStateOf(canShowLoading) }

    HandleUIMessage(uiMessage = uiMessage, scaffoldState = scaffoldState)

    Scaffold(
        scaffoldState = scaffoldState,
        modifier = Modifier.fillMaxSize(),
        backgroundColor = MaterialTheme.appColors.background
    ) {
        val modifierScreenWidth by remember(key1 = windowSize) {
            mutableStateOf(
                windowSize.windowSizeValue(
                    expanded = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        Modifier.widthIn(Dp.Unspecified, 560.dp)
                    } else {
                        Modifier.widthIn(Dp.Unspecified, 650.dp)
                    },
                    compact = Modifier.fillMaxWidth()
                )
            )
        }

        Column(
            modifier = modifierScreenWidth
                .fillMaxSize()
                .padding(it)
                .statusBarsInset()
                .displayCutoutForLandscape(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolbarWithBackBtn(
                label = stringResource(id = R.string.dashboard_programs),
                canShowBackBtn = canShowBackBtn,
                onBackClick = onBackClick
            )

            Surface {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (hasInternetConnection) {
                        val webView = CatalogWebViewScreen(
                            url = contentUrl,
                            uriScheme = uriScheme,
                            isAllLinksExternal = true,
                            onWebPageLoaded = { isLoading = false },
                            refreshSessionCookie = refreshSessionCookie,
                            onURLClick = onURLClick,
                        )

                        AndroidView(
                            modifier = Modifier
                                .background(MaterialTheme.appColors.background),
                            factory = {
                                webView
                            },
                            update = {
                                webView.loadUrl(contentUrl)
                            }
                        )
                    } else {
                        ConnectionErrorView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(MaterialTheme.appColors.background)
                        ) {
                            checkInternetConnection()
                        }
                    }
                    if (isLoading && hasInternetConnection) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.appColors.primary)
                        }
                    }
                }
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MyProgramsPreview() {
    OpenEdXTheme {
        ProgramInfoScreen(
            windowSize = WindowSize(WindowType.Compact, WindowType.Compact),
            uiMessage = null,
            canShowLoading = false,
            contentUrl = "https://www.example.com/",
            uriScheme = "",
            canShowBackBtn = false,
            hasInternetConnection = false,
            checkInternetConnection = {},
            onBackClick = {},
            onURLClick = { _, _ -> },
        )
    }
}
