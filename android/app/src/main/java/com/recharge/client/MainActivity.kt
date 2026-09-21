package com.recharge.client

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.recharge.client.core.model.PaymentOrderResponse
import com.recharge.client.core.theme.AppColors
import com.recharge.client.core.theme.RechargeTheme
import com.recharge.client.core.viewmodel.*
import com.recharge.client.features.auth.ForgotPasswordScreen
import com.recharge.client.features.auth.LoginScreen
import com.recharge.client.features.auth.RegisterScreen
import com.recharge.client.features.home.HomeScreen
import com.recharge.client.features.profile.ProfileScreen
import com.recharge.client.features.recharge.RechargeHistoryScreen
import com.recharge.client.features.recharge.RechargeScreen
import com.recharge.client.features.wallet.AddMoneyDialog
import com.recharge.client.features.wallet.WalletScreen
import com.recharge.client.core.payment.PayUCheckoutBridge
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val walletPaymentViewModel: WalletPaymentViewModel by viewModels()
    private val rechargeViewModel: RechargeViewModel by viewModels()
    private var rechargeGatewayVerifier: ((String, String, String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Checkout.preload(applicationContext)
        setContent { RechargeTheme { AppRoot(::startRazorpayCheckout, ::startGatewayRechargeCheckout, walletPaymentViewModel, rechargeViewModel = rechargeViewModel) } }
    }

    private fun startRazorpayCheckout(order: PaymentOrderResponse) {
        try {
            val checkout = Checkout().apply { setKeyID(order.keyId) }
            val options = JSONObject().apply {
                put("key", order.keyId); put("order_id", order.orderId); put("currency", order.currency);
                put("amount", order.amount.movePointRight(2).longValueExact()); put("name", "mPay");
                put("description", if (order.provider.equals("payu", true)) "Recharge payment" else "Wallet add money"); put("theme.color", "#F59E0B")
            }
            checkout.open(this, options)
        } catch (e: Exception) { walletPaymentViewModel.paymentFailed(e.message ?: "Unable to open payment checkout") }
    }

    private fun startGatewayRechargeCheckout(order: PaymentOrderResponse, rechargeViewModel: RechargeViewModel) {
        try {
            if (order.provider.equals("payu", true)) {
                PayUCheckoutBridge.open(
                    activity = this,
                    amount = order.amount.setScale(2).toPlainString(),
                    isProduction = order.checkoutParams["isProduction"]?.toBooleanStrictOrNull() ?: false,
                    productInfo = order.checkoutParams["productInfo"] ?: "Mobile recharge",
                    key = order.keyId,
                    phone = order.checkoutParams["phone"].orEmpty(),
                    transactionId = order.orderId,
                    firstName = order.checkoutParams["firstName"] ?: "mPay",
                    email = order.checkoutParams["email"] ?: "customer@mpay.local",
                    surl = order.checkoutParams["surl"].orEmpty(),
                    furl = order.checkoutParams["furl"].orEmpty(),
                    userCredential = order.checkoutParams["userCredential"].orEmpty(),
                    callback = object : PayUCheckoutBridge.Callback {
                        override fun onPaymentSuccess(response: Any?) {
                            val payuResponse = PayUCheckoutBridge.getResponseValue(response, "CP_PAYU_RESPONSE")
                            val parsed = runCatching { JSONObject(payuResponse.orEmpty()) }.getOrNull()
                            val txnId = parsed?.optString("txnid").orEmpty().ifBlank { order.orderId }
                            val mihpayid = parsed?.optString("mihpayid").orEmpty()
                            val hash = parsed?.optString("hash").orEmpty()
                            rechargeViewModel.verifyGatewayPayment("payu", mihpayid, txnId, hash)
                        }

                        override fun onPaymentFailure(response: Any?) {
                            val payuResponse = PayUCheckoutBridge.getResponseValue(response, "CP_PAYU_RESPONSE")
                            val message = runCatching { JSONObject(payuResponse.orEmpty()).optString("error_Message") }
                                .getOrNull()?.takeIf { it.isNotBlank() }
                            rechargeViewModel.gatewayPaymentFailed(message ?: "PayU payment failed")
                        }

                        override fun onPaymentCancel(isTxnInitiated: Boolean) {
                            if (isTxnInitiated) {
                                rechargeViewModel.verifyGatewayPayment("payu", null, order.orderId, null)
                            } else {
                                rechargeViewModel.gatewayPaymentFailed("PayU payment was cancelled")
                            }
                        }

                        override fun onError(message: String?) {
                            rechargeViewModel.gatewayPaymentFailed(message ?: "PayU checkout error")
                        }

                        override fun onGenerateHash(
                            hashName: String,
                            hashString: String,
                            callback: PayUCheckoutBridge.PayUHashCallback
                        ) {
                            rechargeViewModel.generatePayUHash(hashName, hashString) { hash ->
                                callback.onHashGenerated(hash)
                            }
                        }
                    }
                )
            } else {
                            rechargeViewModel.gatewayPaymentFailed("PayU payment was cancelled")
                        }
                    }

                    override fun onError(errorResponse: ErrorResponse) {
                        rechargeViewModel.gatewayPaymentFailed(errorResponse.errorMessage)
                    }

                    override fun generateHash(
                        valueMap: HashMap<String, String?>,
                        hashGenerationListener: PayUHashGenerationListener
                    ) {
                        val hashName = valueMap[PayUCheckoutProConstants.CP_HASH_NAME].orEmpty()
                        val hashString = valueMap[PayUCheckoutProConstants.CP_HASH_STRING].orEmpty()
                        if (hashName.isBlank() || hashString.isBlank()) {
                            rechargeViewModel.gatewayPaymentFailed("PayU requested an invalid payment hash")
                            return
                        }
                        rechargeViewModel.generatePayUHash(hashName, hashString) { hash ->
                            val hashMap = HashMap<String, String?>()
                            hashMap[hashName] = hash
                            hashGenerationListener.onHashGenerated(hashMap)
                        }
                    }

                    override fun setWebViewProperties(webView: android.webkit.WebView?, bank: Any?) = Unit
                })
            } else {
                val checkout = Checkout().apply { setKeyID(order.keyId) }
                val options = JSONObject().apply {
                    put("key", order.keyId); put("order_id", order.orderId); put("currency", order.currency);
                    put("amount", order.amount.movePointRight(2).longValueExact()); put("name", "mPay");
                    put("description", "Mobile recharge"); put("theme.color", "#F59E0B")
                }
                rechargeGatewayVerifier = { paymentId, orderId, signature ->
                    rechargeViewModel.verifyGatewayPayment("razorpay", paymentId, orderId, signature)
                }
                checkout.open(this, options)
            }
        } catch (e: Exception) {
            rechargeViewModel.gatewayPaymentFailed(e.message)
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val rechargeVerifier = rechargeGatewayVerifier
        if (rechargeVerifier != null) {
            rechargeGatewayVerifier = null
            rechargeVerifier(
                razorpayPaymentId.orEmpty(),
                paymentData?.orderId.orEmpty(),
                paymentData?.signature.orEmpty()
            )
        } else {
            walletPaymentViewModel.verifyPayment(
                razorpayPaymentId.orEmpty(),
                paymentData?.orderId.orEmpty(),
                paymentData?.signature.orEmpty()
            )
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val rechargeVerifier = rechargeGatewayVerifier
        if (rechargeVerifier != null) {
            rechargeGatewayVerifier = null
            rechargeViewModel.gatewayPaymentFailed(response?.takeIf { it.isNotBlank() } ?: "Payment failed (code $code)")
        } else {
            walletPaymentViewModel.paymentFailed(response?.takeIf { it.isNotBlank() } ?: "Payment failed (code $code)")
        }
    }
}

private sealed class AuthRoute { data object Login : AuthRoute(); data object Register : AuthRoute(); data object ForgotPassword : AuthRoute() }
private data class TopLevelDestination(val route: String, val label: String, val icon: ImageVector, val tint: Color)

@Composable
private fun AppRoot(
    startRazorpay: (PaymentOrderResponse) -> Unit,
    startGatewayRecharge: (PaymentOrderResponse, RechargeViewModel) -> Unit,
    paymentViewModel: WalletPaymentViewModel,
    authViewModel: AuthViewModel = viewModel(), homeViewModel: HomeViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(), rechargeViewModel: RechargeViewModel = viewModel(),
    rechargeHistoryViewModel: RechargeHistoryViewModel = viewModel(),
    walletViewModel: WalletViewModel = viewModel(),
    passwordResetViewModel: PasswordResetViewModel = viewModel()
) {
    val authState by authViewModel.state.collectAsState()
    val passwordResetState by passwordResetViewModel.state.collectAsState()
    val paymentState by paymentViewModel.state.collectAsState()
    val rechargeState by rechargeViewModel.state.collectAsState()
    val historyState by rechargeHistoryViewModel.state.collectAsState()
    val profileState by profileViewModel.state.collectAsState()
    val walletUiState by walletViewModel.state.collectAsState()
    var authRoute by rememberSaveable { mutableStateOf("login") }
    var showFundingDialog by rememberSaveable { mutableStateOf(false) }
    var highlightTransactionId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        if (authState is AuthUiState.Authenticated) {
            homeViewModel.load(); profileViewModel.load(); rechargeHistoryViewModel.refreshAll()
            passwordResetViewModel.clear()
            authRoute = "login"
        }
    }

    LaunchedEffect(passwordResetState) {
        if (passwordResetState is PasswordResetUiState.Success) {
            passwordResetViewModel.clear()
            authRoute = "login"
            authViewModel.clearError()
        }
    }

    LaunchedEffect(rechargeState.gatewayOrder) {
        rechargeState.gatewayOrder?.let { order ->
            startGatewayRecharge(order, rechargeViewModel)
        }
    }

    LaunchedEffect(paymentState) {
        when (paymentState) {
            is PaymentUiState.OrderCreated -> startRazorpay((paymentState as PaymentUiState.OrderCreated).order)
            is PaymentUiState.Success -> { homeViewModel.load(); profileViewModel.load(); showFundingDialog = false; paymentViewModel.reset() }
            else -> Unit
        }
    }

    if (authState !is AuthUiState.Authenticated) {
        when (authRoute) {
            "login" -> LoginScreen(
                authState = authState,
                onLogin = { mobile, password -> authViewModel.login(mobile, password) },
                onSignUp = { authViewModel.clearError(); authRoute = "register" },
                onForgotPassword = { authViewModel.clearError(); passwordResetViewModel.clear(); authRoute = "forgot-password" }
            )
            "register" -> RegisterScreen(
                authState = authState,
                onRegister = { name, email, mobile, password -> authViewModel.register(name, email, mobile, password) },
                onBack = { authViewModel.clearError(); authRoute = "login" }
            )
            else -> ForgotPasswordScreen(
                state = passwordResetState,
                onRequestOtp = passwordResetViewModel::requestOtp,
                onReset = passwordResetViewModel::resetPassword,
                onBack = { passwordResetViewModel.clear(); authRoute = "login" }
            )
        }
        return
    }

    val nav = rememberNavController()
    val destinations = remember {
        listOf(
            TopLevelDestination("home", "Home", Icons.Default.Home, Color(0xFFF59E0B)),
            TopLevelDestination("recharge", "Recharge", Icons.Default.PhoneAndroid, Color(0xFFFB7185)),
            TopLevelDestination("wallet", "Wallet", Icons.Default.AccountBalanceWallet, Color(0xFF22C55E)),
            TopLevelDestination("profile", "Profile", Icons.Default.Person, Color(0xFF8B5CF6))
        )
    }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val configuration = LocalConfiguration.current
    val sideNav = configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            "wallet" -> { rechargeHistoryViewModel.refreshAll(); homeViewModel.load() }
            else -> if (currentRoute != "recharge" && currentRoute != "recharge-history") highlightTransactionId = null
        }
    }

    LaunchedEffect(walletUiState.withdrawSuccess) {
        if (walletUiState.withdrawSuccess != null) homeViewModel.load()
    }

    LaunchedEffect(rechargeState.action) {
        when (val action = rechargeState.action) {
            is RechargeActionState.Success -> {
                highlightTransactionId = action.response.transactionId
                rechargeHistoryViewModel.refreshAll()
                homeViewModel.load()
                rechargeViewModel.clear()
                navigateToTopLevel(nav, "wallet")
            }
            is RechargeActionState.Failure -> { homeViewModel.load(); rechargeHistoryViewModel.refreshAll() }
            else -> Unit
        }
    }

    if (showFundingDialog) {
        AddMoneyDialog(paymentState, { showFundingDialog = false; paymentViewModel.reset() }, paymentViewModel::createOrder, paymentViewModel::reset)
    }

    if (sideNav) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Spacer(Modifier.height(8.dp))
                destinations.forEach { d -> ColoredNavigationRailItem(d, currentRoute, { navigateToTopLevel(nav, d.route) }) }
            }
            AppNavHost(nav, currentRoute, homeViewModel, profileViewModel, rechargeViewModel, rechargeHistoryViewModel, walletViewModel, historyState, { showFundingDialog = it }, paymentViewModel, highlightTransactionId, { authViewModel.logout() }, Modifier.weight(1f))
        }
    } else {
        Scaffold(bottomBar = { BottomNavigationBar(nav, destinations) }) { inner ->
            AppNavHost(nav, currentRoute, homeViewModel, profileViewModel, rechargeViewModel, rechargeHistoryViewModel, walletViewModel, historyState, { showFundingDialog = it }, paymentViewModel, highlightTransactionId, { authViewModel.logout() }, Modifier.padding(inner))
        }
    }
}

@Composable
private fun ColoredNavigationRailItem(d: TopLevelDestination, currentRoute: String?, onClick: () -> Unit) {
    NavigationRailItem(
        selected = currentRoute == d.route, onClick = onClick,
        icon = { Icon(d.icon, d.label, tint = if (currentRoute == d.route) d.tint else d.tint.copy(alpha = .58f)) },
        label = { Text(d.label) }
    )
}

@Composable
private fun AppNavHost(
    nav: NavHostController, currentRoute: String?, homeViewModel: HomeViewModel, profileViewModel: ProfileViewModel,
    rechargeViewModel: RechargeViewModel, rechargeHistoryViewModel: RechargeHistoryViewModel, walletViewModel: WalletViewModel, historyState: RechargeHistoryUiState,
    showFundingDialogSetter: (Boolean) -> Unit, paymentViewModel: WalletPaymentViewModel, highlightTransactionId: String?,
    authLogout: () -> Unit, modifier: Modifier = Modifier
) {
    NavHost(navController = nav, startDestination = "home", modifier = modifier.fillMaxSize()) {
        composable("home") {
            HomeScreen(
                user = homeViewModel.user.collectAsState().value,
                wallet = homeViewModel.wallet.collectAsState().value,
                loading = homeViewModel.loading.collectAsState().value,
                commission = historyState.commission,
                latestRecharge = historyState.items.firstOrNull { it.status.equals("SUCCESS", true) },
                error = homeViewModel.error.collectAsState().value,
                isVisible = currentRoute == "home",
                onRefresh = { homeViewModel.load(); rechargeHistoryViewModel.loadCommission() },
                onRefreshBalance = homeViewModel::refreshWallet,
                onRefreshEarnings = rechargeHistoryViewModel::loadCommission,
                onRecharge = { navigateToTopLevel(nav, "recharge") },
                onAddMoney = { paymentViewModel.reset(); showFundingDialogSetter(true) },
                onRechargeHistory = { navigateToTopLevel(nav, "recharge-history") }
            )
        }
        composable("recharge") {
            RechargeScreen(
    rechargeViewModel.state.collectAsState().value,
    profileViewModel.state.collectAsState().value.user?.commissionRate,
    rechargeViewModel::setMobile,
    rechargeViewModel::detectAndLoad,
    rechargeViewModel::refreshPlans,
    rechargeViewModel::selectPlan,
    rechargeViewModel::executeSelectedPlan,
    rechargeViewModel::startGatewayRechargePayment,
    rechargeViewModel::dismissResult,
    { paymentViewModel.reset(); showFundingDialogSetter(true) },
    rechargeViewModel::refreshWallet,
    rechargeViewModel::clear
)
        }
        composable("wallet") {
            WalletScreen(
                wallet = homeViewModel.wallet.collectAsState().value,
                loading = homeViewModel.loading.collectAsState().value,
                commission = historyState.commission,
                commissionLoading = historyState.commissionLoading,
                walletUiState = walletViewModel.state.collectAsState().value,
                onRefresh = homeViewModel::load,
                onRefreshBalance = homeViewModel::refreshWallet,
                onAddMoney = { paymentViewModel.reset(); showFundingDialogSetter(true) },
                onViewRechargeHistory = { navigateToTopLevel(nav, "recharge-history") },
                onRefreshCommission = rechargeHistoryViewModel::loadCommission,
                onSelectWalletHistoryFilter = walletViewModel::selectFilter,
                onSetWalletHistoryToday = walletViewModel::setToday,
                onSetWalletHistoryLast7 = walletViewModel::setLast7Days,
                onSetWalletHistoryMonth = walletViewModel::setThisMonth,
                onSetWalletHistoryCustom = walletViewModel::setCustom,
                onRefreshWalletHistory = walletViewModel::refreshHistory,
                onLoadMoreWalletHistory = walletViewModel::loadMore,
                onWithdraw = walletViewModel::withdraw,
                onClearWithdrawMessage = walletViewModel::clearWithdrawMessage,
                onOpenWalletDetail = walletViewModel::openDetails,
                onCloseWalletDetail = walletViewModel::closeDetails,
                isVisible = currentRoute == "wallet"
            )
        }
        composable("profile") {
            ProfileScreen(profileViewModel.state.collectAsState().value, profileViewModel::load, profileViewModel::save, profileViewModel::removePhoto, authLogout, homeViewModel::load, currentRoute == "profile")
        }
        composable("recharge-history") {
            RechargeHistoryScreen(
                state = historyState,
                onFilterToday = rechargeHistoryViewModel::setToday,
                onFilterLast7 = rechargeHistoryViewModel::setLast7Days,
                onFilterMonth = rechargeHistoryViewModel::setThisMonth,
                onFilterCustom = rechargeHistoryViewModel::setCustom,
                onRefresh = rechargeHistoryViewModel::refreshHistory,
                onLoadMore = rechargeHistoryViewModel::loadMore,
                onBack = { nav.popBackStack() }
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(nav: NavHostController, destinations: List<TopLevelDestination>) {
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar { destinations.forEach { d ->
        NavigationBarItem(selected = currentRoute == d.route, onClick = { navigateToTopLevel(nav, d.route) }, icon = { Icon(d.icon, d.label, tint = if (currentRoute == d.route) d.tint else d.tint.copy(alpha = .55f)) }, label = { Text(d.label, maxLines = 1, softWrap = false) })
    } }
}

private fun navigateToTopLevel(nav: NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
