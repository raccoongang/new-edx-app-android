package org.openedx.auth.presentation.signin

/**
 * Data class to store UI state of the SignIn screen
 *
 * @param isFacebookAuthEnabled is Facebook auth enabled
 * @param isGoogleAuthEnabled is Google auth enabled
 * @param isMicrosoftAuthEnabled is Microsoft auth enabled
 * @param isSocialAuthEnabled is OAuth buttons visible
 * @param showProgress is progress visible
 * @param loginSuccess is login succeed
 */
internal data class SignInUIState(
    val isFacebookAuthEnabled: Boolean = false,
    val isGoogleAuthEnabled: Boolean = false,
    val isMicrosoftAuthEnabled: Boolean = false,
    val isSocialAuthEnabled: Boolean = false,
    val isBrowserLoginEnabled: Boolean = false,
    val isBrowserRegistrationEnabled: Boolean = false,
    val isLogistrationEnabled: Boolean = false,
    val showProgress: Boolean = false,
    val loginSuccess: Boolean = false,
    val loginFailure: Boolean = false,
)
