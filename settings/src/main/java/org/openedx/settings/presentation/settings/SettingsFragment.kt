package org.openedx.settings.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.openedx.core.ui.rememberWindowSize
import org.openedx.core.ui.theme.OpenEdXTheme
import org.openedx.settings.presentation.ui.SettingsScreen

class SettingsFragment : Fragment() {

    private val viewModel by viewModel<SettingsViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OpenEdXTheme {
                val windowSize = rememberWindowSize()
                val uiState by viewModel.uiState.collectAsState()
                val logoutSuccess by viewModel.successLogout.observeAsState(false)
                val appUpgradeEvent by viewModel.appUpgradeEvent.observeAsState(null)

                SettingsScreen(
                    windowSize = windowSize,
                    uiState = uiState,
                    appUpgradeEvent = appUpgradeEvent,
                    onBackClick = {
                        requireActivity().supportFragmentManager.popBackStack()
                    },
                    onAction = { action ->
                        when (action) {
                            SettingsScreenAction.AppVersionClick -> {
                                viewModel.appVersionClickedEvent(requireContext())
                            }

                            SettingsScreenAction.LogoutClick -> {
                                viewModel.logout()
                            }

                            SettingsScreenAction.PrivacyPolicyClick -> {
                                viewModel.privacyPolicyClicked(
                                    requireParentFragment().parentFragmentManager
                                )
                            }

                            SettingsScreenAction.CookiePolicyClick -> {
                                viewModel.cookiePolicyClicked(
                                    requireParentFragment().parentFragmentManager
                                )
                            }

                            SettingsScreenAction.DataSellClick -> {
                                viewModel.dataSellClicked(
                                    requireParentFragment().parentFragmentManager
                                )
                            }

                            SettingsScreenAction.FaqClick -> viewModel.faqClicked()

                            SettingsScreenAction.SupportClick -> {
                                viewModel.emailSupportClicked(requireContext())
                            }

                            SettingsScreenAction.TermsClick -> {
                                viewModel.termsOfUseClicked(
                                    requireParentFragment().parentFragmentManager
                                )
                            }

                            SettingsScreenAction.VideoSettingsClick -> {
                                viewModel.profileVideoSettingsClicked(
                                    requireParentFragment().parentFragmentManager
                                )
                            }
                        }
                    }
                )


                LaunchedEffect(logoutSuccess) {
                    if (logoutSuccess) {
                        viewModel.restartApp(requireActivity().supportFragmentManager)
                    }
                }
            }
        }
    }
}

internal interface SettingsScreenAction {
    object AppVersionClick : SettingsScreenAction
    object LogoutClick : SettingsScreenAction
    object PrivacyPolicyClick : SettingsScreenAction
    object CookiePolicyClick : SettingsScreenAction
    object DataSellClick : SettingsScreenAction
    object FaqClick : SettingsScreenAction
    object TermsClick : SettingsScreenAction
    object SupportClick : SettingsScreenAction
    object VideoSettingsClick : SettingsScreenAction
}

