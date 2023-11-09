package org.openedx.auth.presentation.signin

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openedx.auth.R
import org.openedx.auth.data.model.LoginType
import org.openedx.auth.domain.interactor.AuthInteractor
import org.openedx.auth.presentation.AuthAnalytics
import org.openedx.auth.presentation.sso.FacebookAuthHelper
import org.openedx.auth.presentation.sso.GoogleAuthHelper
import org.openedx.auth.presentation.sso.MicrosoftAuthHelper
import org.openedx.core.BaseViewModel
import org.openedx.core.BuildConfig
import org.openedx.core.SingleEventLiveData
import org.openedx.core.UIMessage
import org.openedx.core.Validator
import org.openedx.core.config.Config
import org.openedx.core.data.storage.CorePreferences
import org.openedx.core.extension.isInternetError
import org.openedx.core.system.EdxError
import org.openedx.core.system.ResourceManager
import org.openedx.core.system.notifier.AppUpgradeEvent
import org.openedx.core.system.notifier.AppUpgradeNotifier
import org.openedx.core.utils.Logger
import org.openedx.core.R as CoreRes

class SignInViewModel(
    private val config: Config,
    private val interactor: AuthInteractor,
    private val resourceManager: ResourceManager,
    private val preferencesManager: CorePreferences,
    private val validator: Validator,
    private val appUpgradeNotifier: AppUpgradeNotifier,
    private val analytics: AuthAnalytics,
    private val facebookAuthHelper: FacebookAuthHelper,
    private val googleAuthHelper: GoogleAuthHelper,
    private val microsoftAuthHelper: MicrosoftAuthHelper,
) : BaseViewModel() {

    val isLogistrationEnabled get() = config.isPreLoginExperienceEnabled()

    private val logger = Logger("SignInViewModel")

    private val _uiState = MutableStateFlow(
        SignInUIState(shouldShowSocialLogin = BuildConfig.FF_SHOW_SOCIAL_LOGIN)
    )
    internal val uiState: StateFlow<SignInUIState> = _uiState

    private val _uiMessage = SingleEventLiveData<UIMessage>()
    val uiMessage: LiveData<UIMessage>
        get() = _uiMessage

    private val _appUpgradeEvent = MutableLiveData<AppUpgradeEvent>()
    val appUpgradeEvent: LiveData<AppUpgradeEvent>
        get() = _appUpgradeEvent

    init {
        collectAppUpgradeEvent()
    }

    fun login(username: String, password: String) {
        if (!validator.isEmailValid(username)) {
            _uiMessage.value =
                UIMessage.SnackBarMessage(resourceManager.getString(R.string.auth_invalid_email))
            return
        }
        if (!validator.isPasswordValid(password)) {
            _uiMessage.value =
                UIMessage.SnackBarMessage(resourceManager.getString(R.string.auth_invalid_password))
            return
        }

        _uiState.update { it.copy(showProgress = true) }
        viewModelScope.launch {
            try {
                interactor.login(username, password)
                _uiState.update { it.copy(loginSuccess = true) }
                setUserId()
                analytics.userLoginEvent(LoginType.PASSWORD.methodName)
            } catch (e: Exception) {
                if (e is EdxError.InvalidGrantException) {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(CoreRes.string.core_error_invalid_grant))
                } else if (e.isInternetError()) {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(CoreRes.string.core_error_no_connection))
                } else {
                    _uiMessage.value =
                        UIMessage.SnackBarMessage(resourceManager.getString(CoreRes.string.core_error_unknown_error))
                }
            }
            _uiState.update { it.copy(showProgress = false) }
        }
    }

    private fun collectAppUpgradeEvent() {
        viewModelScope.launch {
            appUpgradeNotifier.notifier.collect { event ->
                _appUpgradeEvent.value = event
            }
        }
    }

    fun signInGoogle(activityContext: Activity) {
        _uiState.update { it.copy(showProgress = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    googleAuthHelper.signIn(activityContext)
                }
            }.getOrNull()?.let {
                exchangeToken(it, LoginType.GOOGLE)
            } ?: onUnknownError()
        }
    }

    fun signInFacebook(fragment: Fragment) {
        _uiState.update { it.copy(showProgress = true) }
        viewModelScope.launch {
            runCatching {
                facebookAuthHelper.signIn(fragment)
            }.onFailure {
                logger.e { "Facebook auth error: $it" }
            }.getOrNull()?.let {
                exchangeToken(it, LoginType.FACEBOOK)
            } ?: onUnknownError()
        }
    }

    fun signInMicrosoft(activityContext: Activity) {
        _uiState.update { it.copy(showProgress = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    microsoftAuthHelper.signIn(activityContext)
                }
            }.onFailure {
                logger.e { "Microsoft auth error: $it" }
            }.getOrNull()?.let {
                exchangeToken(it, LoginType.MICROSOFT)
            } ?: onUnknownError()
        }
    }

    fun signUpClickedEvent() {
        analytics.signUpClickedEvent()
    }

    fun forgotPasswordClickedEvent() {
        analytics.forgotPasswordClickedEvent()
    }

    override fun onCleared() {
        super.onCleared()
        facebookAuthHelper.clear()
    }

    private suspend fun exchangeToken(token: String?, loginType: LoginType) {
        runCatching {
            interactor.loginSocial(token, loginType)
        }.onFailure { error ->
            logger.e { "Social login error: $error" }
            onUnknownError()
        }.onSuccess {
            logger.d { "Social login (${loginType.methodName}) success" }
            _uiState.update { it.copy(loginSuccess = true) }
            setUserId()
            analytics.userLoginEvent(loginType.methodName)
            _uiState.update { it.copy(showProgress = false) }
        }
    }

    private fun onUnknownError(message: (() -> String)? = null) {
        message?.let {
            logger.e { it() }
        }
        _uiMessage.value = UIMessage.SnackBarMessage(
            resourceManager.getString(CoreRes.string.core_error_unknown_error)
        )
        _uiState.update { it.copy(showProgress = false) }
    }

    private fun setUserId() {
        preferencesManager.user?.let {
            analytics.setUserIdForSession(it.id)
        }
    }
}
