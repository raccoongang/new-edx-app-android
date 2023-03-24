package com.raccoongang.profile.presentation.delete

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.raccoongang.core.R
import com.raccoongang.core.UIMessage
import com.raccoongang.core.ui.*
import com.raccoongang.core.ui.theme.NewEdxTheme
import com.raccoongang.core.ui.theme.appColors
import com.raccoongang.core.ui.theme.appTypography
import com.raccoongang.profile.presentation.ProfileRouter
import com.raccoongang.profile.presentation.edit.EditProfileFragment
import com.raccoongang.profile.presentation.profile.ProfileViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.raccoongang.profile.R as profileR

class DeleteProfileFragment : Fragment() {

    private val viewModel by viewModel<DeleteProfileViewModel>()
    private val logoutViewModel by viewModel<ProfileViewModel>()
    private val router by inject<ProfileRouter>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(logoutViewModel)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            NewEdxTheme {
                val windowSize = rememberWindowSize()

                val uiState by viewModel.uiState.observeAsState(DeleteProfileFragmentUIState.Initial)
                val uiMessage by viewModel.uiMessage.observeAsState()
                val logoutSuccess by logoutViewModel.successLogout.observeAsState(false)

                DeleteProfileScreen(
                    windowSize = windowSize,
                    uiState = uiState,
                    uiMessage = uiMessage,
                    onBackClick = {
                        requireActivity().supportFragmentManager.popBackStack()
                    },
                    onBackToProfileClick = {
                        requireActivity().supportFragmentManager.popBackStack(
                            EditProfileFragment::class.java.simpleName,
                            FragmentManager.POP_BACK_STACK_INCLUSIVE
                        )
                    },
                    onDeleteClick = {
                        viewModel.deleteProfile(it)
                    }
                )

                LaunchedEffect(logoutSuccess) {
                    if (logoutSuccess) {
                        router.restartApp(requireActivity().supportFragmentManager)
                    }
                }
            }
        }
    }

}

@Composable
fun DeleteProfileScreen(
    windowSize: WindowSize,
    uiState: DeleteProfileFragmentUIState,
    uiMessage: UIMessage?,
    onDeleteClick: (String) -> Unit,
    onBackClick: () -> Unit,
    onBackToProfileClick: () -> Unit
) {
    val scaffoldState = rememberScaffoldState()
    val scrollState = rememberScrollState()

    val errorText = if (uiState is DeleteProfileFragmentUIState.Error) {
        uiState.message
    } else {
        null
    }

    var password by rememberSaveable {
        mutableStateOf("")
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        scaffoldState = scaffoldState
    ) { paddingValues ->

        val topBarWidth by remember(key1 = windowSize) {
            mutableStateOf(
                windowSize.windowSizeValue(
                    expanded = Modifier.widthIn(Dp.Unspecified, 560.dp),
                    compact = Modifier
                        .fillMaxWidth()
                )
            )
        }

        val contentWidth by remember(key1 = windowSize) {
            mutableStateOf(
                windowSize.windowSizeValue(
                    expanded = Modifier.widthIn(Dp.Unspecified, 420.dp),
                    compact = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            )
        }

        HandleUIMessage(uiMessage = uiMessage, scaffoldState = scaffoldState)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .statusBarsInset(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = topBarWidth,
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth(),
                        text = stringResource(id = profileR.string.profile_delete_account),
                        color = MaterialTheme.appColors.textPrimary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.appTypography.titleMedium
                    )

                    BackBtn(
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        onBackClick()
                    }
                }

                Column(
                    Modifier
                        .fillMaxHeight()
                        .then(contentWidth)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(48.dp))
                    Image(
                        modifier = Modifier.size(145.dp),
                        painter = painterResource(id = com.raccoongang.profile.R.drawable.profile_delete_box),
                        contentDescription = null,
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = buildAnnotatedString {
                            append(stringResource(id = profileR.string.profile_you_want_to))
                            append(" ")
                            append(stringResource(id = profileR.string.profile_delete_your_account))
                            addStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.appColors.textPrimary
                                ),
                                start = 0,
                                end = stringResource(id = profileR.string.profile_you_want_to).length
                            )
                            addStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.appColors.error
                                ),
                                start = stringResource(id = profileR.string.profile_you_want_to).length + 1,
                                end = this.length
                            )
                        },
                        style = MaterialTheme.appTypography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(id = profileR.string.profile_confirm_action),
                        style = MaterialTheme.appTypography.labelLarge,
                        color = MaterialTheme.appColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(40.dp))
                    NewEdxOutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth(),
                        title = stringResource(id = R.string.core_password),
                        onValueChanged = {
                            password = it
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        keyboardActions = {
                            it.clearFocus()
                            onDeleteClick(password)
                        },
                        errorText = errorText
                    )
                    Spacer(Modifier.height(38.dp))
                    NewEdxButton(
                        text = stringResource(id = profileR.string.profile_yes_delete_account),
                        enabled = uiState !is DeleteProfileFragmentUIState.Loading,
                        backgroundColor = MaterialTheme.appColors.error,
                        onClick = {
                            onDeleteClick(password)
                        }
                    )
                    Spacer(Modifier.height(35.dp))
                    IconText(
                        text = stringResource(id = profileR.string.profile_back_to_profile),
                        painter = painterResource(id = R.drawable.core_ic_back),
                        color = MaterialTheme.appColors.primary,
                        textStyle = MaterialTheme.appTypography.labelLarge,
                        onClick = {
                            onBackToProfileClick()
                        }
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}


@Preview(
    name = "PIXEL_3A_Light",
    device = Devices.PIXEL_3A,
    uiMode = Configuration.UI_MODE_NIGHT_NO
)
@Preview(
    name = "PIXEL_3A_Dark",
    device = Devices.PIXEL_3A,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun DeleteProfileScreenPreview() {
    NewEdxTheme {
        DeleteProfileScreen(
            windowSize = WindowSize(WindowType.Compact, WindowType.Compact),
            uiState = DeleteProfileFragmentUIState.Initial,
            uiMessage = null,
            onBackClick = {},
            onBackToProfileClick = {},
            onDeleteClick = {}
        )
    }
}