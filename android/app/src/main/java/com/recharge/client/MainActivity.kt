package com.recharge.client

import android.content.res.Configuration
import android.os.Bundle
import android.provider.ContactsContract
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.recharge.client.features.rental.RentalVendorOnboardingScreen
import com.recharge.client.features.rental.RentalMyBookingsScreen
import com.recharge.client.features.rental.MarketplaceScreen
import com.recharge.client.features.rental.CarRentalMarketplaceScreen
import com.recharge.client.features.rental.RentalVehicleOnboardingScreen
import com.recharge.client.features.rental.RentalBookingScreen
import com.recharge.client.features.wallet.AddMoneyDialog
import com.recharge.client.features.wallet.WalletScreen
import com.recharge.client.core.payment.PayUCheckoutBridge
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject
import java.math.BigDecimal

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.Contacts.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val raw = cursor.getString(0).orEmpty()
                    val contactName = cursor.getString(1).orEmpty().trim()
                    val digits = raw.filter { it.isDigit() }
                    val normalized = when {
                        digits.length == 10 -> digits
                        digits.length > 10 && digits.endsWith("10") -> digits.takeLast(10)
                        digits.length >= 10 -> digits.takeLast(10)
                        else -> ""
                    }
                    if (normalized.length == 10) rechargeViewModel.setMobile(normalized, contactName)
                }
            }
        }
    }
    private val walletPaymentViewModel: WalletPaymentViewModel by viewModels()
    private val rechargeViewModel: RechargeViewModel by viewModels()
    private enum class RazorpayCheckoutTarget { WALLET, RECHARGE }

    companion object {
        private const val STATE_RAZORPAY_TARGET = "razorpay_checkout_target"
        private const val STATE_RAZORPAY_ORDER_ID = "razorpay_checkout_order_id"
    }

    private var pendingRazorpayTarget: RazorpayCheckoutTarget? = null
    private var pendingRazorpayOrderId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRazorpayTarget = savedInstanceState?.getString(STATE_RAZORPAY_TARGET)
            ?.let { runCatching { RazorpayCheckoutTarget.valueOf(it) }.getOrNull() }
        pendingRazorpayOrderId = savedInstanceState?.getString(STATE_RAZORPAY_ORDER_ID)
        Checkout.preload(applicationContext)
        if (BuildConfig.MAPS_API_KEY.isNotBlank() && !com.google.android.libraries.places.api.Places.isInitialized()) {
            com.google.android.libraries.places.api.Places.initializeWithNewPlacesApiEnabled(
                applicationContext,
                BuildConfig.MAPS_API_KEY
            )
        }
        setContent { RechargeTheme { AppRoot(::startWalletPaymentCheckout, ::startGatewayRechargeCheckout, walletPaymentViewModel, { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, rechargeViewModel = rechargeViewModel) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_RAZORPAY_TARGET, pendingRazorpayTarget?.name)
        outState.putString(STATE_RAZORPAY_ORDER_ID, pendingRazorpayOrderId)
        super.onSaveInstanceState(outState)
    }

    private fun startWalletPaymentCheckout(order: PaymentOrderResponse) {
        try {
            when {
                order.provider.equals("mock", true) -> {
                    pendingRazorpayTarget = null
                    pendingRazorpayOrderId = null
                    walletPaymentViewModel.verifyPayment("mock", null, order.orderId, null)
                }
                order.provider.equals("payu", true) -> {
                    pendingRazorpayTarget = null
                    pendingRazorpayOrderId = null
                    PayUCheckoutBridge.open(
                        this,
                        order.amount.setScale(2).toPlainString(),
                        order.checkoutParams["isProduction"]?.toBooleanStrictOrNull() ?: false,
                        order.checkoutParams["productInfo"] ?: "mPay wallet",
                        order.keyId,
                        order.checkoutParams["phone"].orEmpty(),
                        order.orderId,
                        order.checkoutParams["firstName"] ?: "mPay",
                        order.checkoutParams["email"] ?: "customer@mpay.local",
                        order.checkoutParams["surl"].orEmpty(),
                        order.checkoutParams["furl"].orEmpty(),
                        order.checkoutParams["userCredential"].orEmpty(),
                        order.checkoutParams["vasForMobileSdkHash"].orEmpty(),
                        order.checkoutParams["paymentRelatedDetailsHash"].orEmpty(),
                        order.checkoutParams["paymentHash"].orEmpty(),
                        object : PayUCheckoutBridge.Callback {
                            override fun onPaymentSuccess(response: Any?) {
                                val payuResponse = PayUCheckoutBridge.getResponseValue(response, "CP_PAYU_RESPONSE")
                                val parsed = runCatching { JSONObject(payuResponse.orEmpty()) }.getOrNull()
                                val txnId = parsed?.optString("txnid").orEmpty().ifBlank { order.orderId }
                                val mihpayid = parsed?.optString("mihpayid").orEmpty()
                                val hash = parsed?.optString("hash").orEmpty()
                                walletPaymentViewModel.verifyPayment("payu", mihpayid, txnId, hash)
                            }

                            override fun onPaymentFailure(response: Any?) {
                                val payuResponse = PayUCheckoutBridge.getResponseValue(response, "CP_PAYU_RESPONSE")
                                val message = runCatching { JSONObject(payuResponse.orEmpty()).optString("error_Message") }
                                    .getOrNull()?.takeIf { it.isNotBlank() }
                                walletPaymentViewModel.paymentFailed(message ?: "PayU payment failed")
                            }

                            override fun onPaymentCancel(isTxnInitiated: Boolean) {
                                if (isTxnInitiated) {
                                    walletPaymentViewModel.verifyPayment("payu", null, order.orderId, null)
                                } else {
                                    walletPaymentViewModel.paymentFailed("PayU payment was cancelled")
                                }
                            }

                            override fun onError(message: String?) {
                                walletPaymentViewModel.paymentFailed(message ?: "PayU checkout error")
                            }

                            override fun onGenerateHash(
                                hashName: String,
                                hashString: String,
                                postSalt: String?,
                                hashType: String?,
                                callback: PayUCheckoutBridge.PayUHashCallback
                            ) {
                                walletPaymentViewModel.generatePayUHash(hashName, hashString, postSalt, hashType) { hash ->
                                    callback.onHashGenerated(hash)
                                }
                            }
                        }
                    )
                }
                else -> {
                    val checkout = Checkout().apply { setKeyID(order.keyId) }
                    val options = JSONObject().apply {
                        put("key", order.keyId)
                        put("order_id", order.orderId)
                        put("currency", order.currency)
                        put("amount", order.amount.movePointRight(2).longValueExact())
                        put("name", "mPay")
                        put("description", "Wallet add money")
                        put("theme.color", "#F59E0B")
                    }
                    pendingRazorpayTarget = RazorpayCheckoutTarget.WALLET
                    pendingRazorpayOrderId = order.orderId
                    checkout.open(this, options)
                }
            }
        } catch (e: Exception) {
            pendingRazorpayTarget = null
            pendingRazorpayOrderId = null
            walletPaymentViewModel.paymentFailed(e.message ?: "Unable to open payment checkout")
        }
    }

    private fun startGatewayRechargeCheckout(order: PaymentOrderResponse, rechargeViewModel: RechargeViewModel) {
        try {
            if (order.provider.equals("payu", true)) {
                pendingRazorpayTarget = null
                pendingRazorpayOrderId = null
                PayUCheckoutBridge.open(
                    this,
                    order.amount.setScale(2).toPlainString(),
                    order.checkoutParams["isProduction"]?.toBooleanStrictOrNull() ?: false,
                    order.checkoutParams["productInfo"] ?: "Mobile recharge",
                    order.keyId,
                    order.checkoutParams["phone"].orEmpty(),
                    order.orderId,
                    order.checkoutParams["firstName"] ?: "mPay",
                    order.checkoutParams["email"] ?: "customer@mpay.local",
                    order.checkoutParams["surl"].orEmpty(),
                    order.checkoutParams["furl"].orEmpty(),
                    order.checkoutParams["userCredential"].orEmpty(),
                    order.checkoutParams["vasForMobileSdkHash"].orEmpty(),
                    order.checkoutParams["paymentRelatedDetailsHash"].orEmpty(),
                    order.checkoutParams["paymentHash"].orEmpty(),
                    object : PayUCheckoutBridge.Callback {
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
                            postSalt: String?,
                            hashType: String?,
                            callback: PayUCheckoutBridge.PayUHashCallback
                        ) {
                            rechargeViewModel.generatePayUHash(hashName, hashString, postSalt, hashType) { hash ->
                                callback.onHashGenerated(hash)
                            }
                        }
                    }
                )
            } else {
                val checkout = Checkout().apply { setKeyID(order.keyId) }
                val options = JSONObject().apply {
                    put("key", order.keyId)
                    put("order_id", order.orderId)
                    put("currency", order.currency)
                    put("amount", order.amount.movePointRight(2).longValueExact())
                    put("name", "mPay")
                    put("description", "Mobile recharge")
                    put("theme.color", "#F59E0B")
                }
                pendingRazorpayTarget = RazorpayCheckoutTarget.RECHARGE
                pendingRazorpayOrderId = order.orderId
                checkout.open(this, options)
            }
        } catch (e: Exception) {
            pendingRazorpayTarget = null
            pendingRazorpayOrderId = null
            rechargeViewModel.gatewayPaymentFailed(e.message)
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val target = pendingRazorpayTarget
        val expectedOrderId = pendingRazorpayOrderId
        val returnedOrderId = paymentData?.orderId.orEmpty()
        pendingRazorpayTarget = null
        pendingRazorpayOrderId = null

        if (target == null) {
            walletPaymentViewModel.paymentFailed(
                "Payment result received without a known checkout context. Please refresh your wallet or history."
            )
            return
        }

        if (expectedOrderId != null && returnedOrderId.isNotBlank() && expectedOrderId != returnedOrderId) {
            when (target) {
                RazorpayCheckoutTarget.WALLET ->
                    walletPaymentViewModel.paymentFailed("Payment result did not match the active wallet order.")
                RazorpayCheckoutTarget.RECHARGE ->
                    rechargeViewModel.gatewayPaymentFailed("Payment result did not match the active recharge order.")
            }
            return
        }

        when (target) {
            RazorpayCheckoutTarget.WALLET -> walletPaymentViewModel.verifyPayment(
                provider = "razorpay",
                paymentId = razorpayPaymentId,
                orderId = returnedOrderId.ifBlank { expectedOrderId.orEmpty() },
                signature = paymentData?.signature
            )
            RazorpayCheckoutTarget.RECHARGE -> rechargeViewModel.verifyGatewayPayment(
                provider = "razorpay",
                paymentId = razorpayPaymentId,
                orderId = returnedOrderId.ifBlank { expectedOrderId.orEmpty() },
                signature = paymentData?.signature
            )
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val target = pendingRazorpayTarget
        pendingRazorpayTarget = null
        pendingRazorpayOrderId = null
        val message = response?.takeIf { it.isNotBlank() } ?: "Payment failed (code $code)"

        when (target) {
            RazorpayCheckoutTarget.RECHARGE -> rechargeViewModel.gatewayPaymentFailed(message)
            RazorpayCheckoutTarget.WALLET -> walletPaymentViewModel.paymentFailed(message)
            null -> walletPaymentViewModel.paymentFailed("Payment failed, but the checkout context is no longer available.")
        }
    }
}

private sealed class AuthRoute { data object Login : AuthRoute(); data object Register : AuthRoute(); data object ForgotPassword : AuthRoute() }
private data class TopLevelDestination(val route: String, val label: String, val icon: ImageVector, val tint: Color)

@Composable
private fun AppRoot(
    startWalletPayment: (PaymentOrderResponse) -> Unit,
    startGatewayRecharge: (PaymentOrderResponse, RechargeViewModel) -> Unit,
    paymentViewModel: WalletPaymentViewModel,
    onChooseContact: () -> Unit,
    authViewModel: AuthViewModel = viewModel(), homeViewModel: HomeViewModel = viewModel(),
    profileViewModel: ProfileViewModel = viewModel(), rechargeViewModel: RechargeViewModel = viewModel(),
    rechargeHistoryViewModel: RechargeHistoryViewModel = viewModel(),
    rentalViewModel: RentalViewModel = viewModel(),
    walletViewModel: WalletViewModel = viewModel(),
    passwordResetViewModel: PasswordResetViewModel = viewModel()
) {
    val authState by authViewModel.state.collectAsState()
    val passwordResetState by passwordResetViewModel.state.collectAsState()
    val paymentState by paymentViewModel.state.collectAsState()
    val rechargeState by rechargeViewModel.state.collectAsState()
    val historyState by rechargeHistoryViewModel.state.collectAsState()
    val profileState by profileViewModel.state.collectAsState()
    val rentalState by rentalViewModel.state.collectAsState()
    val walletUiState by walletViewModel.state.collectAsState()
    var authRoute by rememberSaveable { mutableStateOf("login") }
    var showFundingDialog by rememberSaveable { mutableStateOf(false) }
    var highlightTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var launchedWalletOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var launchedRechargeOrderId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(authState) {
        if (authState is AuthUiState.Authenticated) {
            rechargeHistoryViewModel.refreshAll()
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
        val order = rechargeState.gatewayOrder ?: return@LaunchedEffect
        if (launchedRechargeOrderId == order.orderId) return@LaunchedEffect
        launchedRechargeOrderId = order.orderId
        startGatewayRecharge(order, rechargeViewModel)
    }

    LaunchedEffect(paymentState) {
        when (paymentState) {
            is PaymentUiState.OrderCreated -> {
                val order = (paymentState as PaymentUiState.OrderCreated).order
                if (launchedWalletOrderId != order.orderId) {
                    launchedWalletOrderId = order.orderId
                    startWalletPayment(order)
                }
            }
            is PaymentUiState.Success -> {
                homeViewModel.refreshWallet()
                showFundingDialog = false
                paymentViewModel.reset()
            }
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
    val logoutAndReset = {
        nav.navigate("home") {
            popUpTo(nav.graph.startDestinationId) {
                inclusive = false
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
        homeViewModel.resetSession()
        profileViewModel.resetSession()
        rechargeViewModel.resetSession()
        rechargeHistoryViewModel.resetSession()
        rentalViewModel.resetSession()
        walletViewModel.resetSession()
        paymentViewModel.resetSession()
        highlightTransactionId = null
        launchedWalletOrderId = null
        launchedRechargeOrderId = null
        showFundingDialog = false
        authViewModel.logout()
    }

    val destinations = remember {
        listOf(
            TopLevelDestination("home", "Home", Icons.Default.Home, AppColors.Primary),
            TopLevelDestination("recharge", "Recharge", Icons.Default.PhoneAndroid, AppColors.Primary),
            TopLevelDestination("wallet", "Wallet", Icons.Default.AccountBalanceWallet, AppColors.Primary),
            TopLevelDestination("profile", "Profile", Icons.Default.Person, AppColors.Primary)
        )
    }
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val configuration = LocalConfiguration.current
    val sideNav = configuration.screenWidthDp >= 600 && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(currentRoute) {
        when (currentRoute) {
            "marketplace" -> Unit
            "car-rental" -> rentalViewModel.clearCarSearch()
            "rental-booking" -> homeViewModel.refreshWallet()
            "rental-vendor" -> rentalViewModel.loadVendor()
            "rental-vehicle" -> rentalViewModel.loadVendorVehicles()
            else -> if (currentRoute != "recharge" && currentRoute != "recharge-history") highlightTransactionId = null
        }
    }

    LaunchedEffect(walletUiState.withdrawSuccess) {
        if (walletUiState.withdrawSuccess != null) homeViewModel.refreshWallet()
    }

    LaunchedEffect(rechargeState.action) {
        when (val action = rechargeState.action) {
            is RechargeActionState.Success -> {
                highlightTransactionId = action.response.transactionId
                rechargeHistoryViewModel.refreshAll()
            }
            is RechargeActionState.Pending -> {
                highlightTransactionId = action.response.transactionId
                rechargeHistoryViewModel.refreshAll()
            }
            is RechargeActionState.Failure -> {
                rechargeHistoryViewModel.refreshAll()
            }
            else -> Unit
        }
    }

    if (showFundingDialog) {
        AddMoneyDialog(
            paymentState,
            { showFundingDialog = false; paymentViewModel.reset() },
            paymentViewModel::createOrder,
            paymentViewModel::reset,
            availableBalance = homeViewModel.wallet.collectAsState().value?.availableBalance ?: BigDecimal.ZERO
        )
    }

    if (sideNav) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Spacer(Modifier.height(8.dp))
                destinations.forEach { d -> ColoredNavigationRailItem(d, currentRoute, { navigateToTopLevel(nav, d.route) }) }
            }
            AppNavHost(nav, currentRoute, homeViewModel, profileViewModel, rechargeViewModel, rechargeHistoryViewModel, rentalViewModel, walletViewModel, historyState, { showFundingDialog = it }, paymentViewModel, highlightTransactionId, { authViewModel.logout() }, onChooseContact, Modifier.weight(1f))
        }
    } else {
        Scaffold(bottomBar = { BottomNavigationBar(nav, destinations) }) { inner ->
            AppNavHost(nav, currentRoute, homeViewModel, profileViewModel, rechargeViewModel, rechargeHistoryViewModel, rentalViewModel, walletViewModel, historyState, { showFundingDialog = it }, paymentViewModel, highlightTransactionId, { authViewModel.logout() }, onChooseContact, Modifier.padding(inner))
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
    rechargeViewModel: RechargeViewModel, rechargeHistoryViewModel: RechargeHistoryViewModel, rentalViewModel: RentalViewModel, walletViewModel: WalletViewModel, historyState: RechargeHistoryUiState,
    showFundingDialogSetter: (Boolean) -> Unit, paymentViewModel: WalletPaymentViewModel, highlightTransactionId: String?,
    authLogout: () -> Unit, onChooseContact: () -> Unit, modifier: Modifier = Modifier
) {
    NavHost(navController = nav, startDestination = "home", modifier = modifier.fillMaxSize()) {
        composable("home") {
            HomeScreen(
                user = homeViewModel.user.collectAsState().value,
                wallet = homeViewModel.wallet.collectAsState().value,
                loading = homeViewModel.loading.collectAsState().value,
                commission = historyState.commission,
                latestRecharge = historyState.items.firstOrNull(),
                error = homeViewModel.error.collectAsState().value,
                isVisible = currentRoute == "home",
                onRefresh = { homeViewModel.load(); rechargeHistoryViewModel.loadCommission() },
                onRefreshBalance = homeViewModel::refreshWallet,
                onRefreshEarnings = rechargeHistoryViewModel::loadCommission,
                onRecharge = { navigateToTopLevel(nav, "recharge") },
                onAddMoney = { paymentViewModel.reset(); showFundingDialogSetter(true) },
                onWithdraw = walletViewModel::withdraw,
                onClearWithdrawMessage = walletViewModel::clearWithdrawMessage,
                walletUiState = walletViewModel.state.collectAsState().value,
                onRechargeHistory = { navigateToTopLevel(nav, "recharge-history") },
                onRentalBookings = { nav.navigate("rental-bookings") },
                onCarRental = { nav.navigate("car-rental") }
            )
        }
        composable("recharge") {
            RechargeScreen(
    rechargeViewModel.state.collectAsState().value,
    profileViewModel.state.collectAsState().value.user?.commissionRate,
    rechargeViewModel::setMobile,
    rechargeViewModel::setRecipientName,
    onChooseContact,
    rechargeViewModel::detectAndLoad,
    rechargeViewModel::refreshPlans,
    rechargeViewModel::selectPlan,
    rechargeViewModel::executeSelectedPlan,
    rechargeViewModel::startGatewayRechargePayment,
    { rechargeViewModel.dismissResult(); navigateToTopLevel(nav, "home") },
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
                onSetHistoryPageSize = walletViewModel::setHistoryPageSize,
                onWalletPreviousPage = { walletViewModel.goToPage(walletViewModel.state.value.page - 1) },
                onWalletNextPage = { walletViewModel.goToPage(walletViewModel.state.value.page + 1) },
                onWithdrawalPreviousPage = { walletViewModel.goToWithdrawalPage(walletViewModel.state.value.withdrawalPage - 1) },
                onWithdrawalNextPage = { walletViewModel.goToWithdrawalPage(walletViewModel.state.value.withdrawalPage + 1) },
                onWithdraw = walletViewModel::withdraw,
                onClearWithdrawMessage = walletViewModel::clearWithdrawMessage,
                onOpenWalletDetail = walletViewModel::openDetails,
                onCloseWalletDetail = walletViewModel::closeDetails,
                isVisible = currentRoute == "wallet"
            )
        }
        composable("profile") {
            ProfileScreen(profileViewModel.state.collectAsState().value, rentalViewModel.state.collectAsState().value.vendor, profileViewModel::load, rentalViewModel::loadVendor, profileViewModel::save, profileViewModel::removePhoto, authLogout, homeViewModel::load, { nav.navigate("rental-vendor") }, currentRoute == "profile")
        }
        composable("marketplace") {
            MarketplaceScreen(onBack = { nav.popBackStack() }, onCarRental = { nav.navigate("car-rental") })
        }
        composable("car-rental") {
            CarRentalMarketplaceScreen(
                state = rentalViewModel.state.collectAsState().value,
                onBack = { nav.popBackStack() },
                onBook = { car, startDate, endDate ->
                    nav.currentBackStackEntry?.savedStateHandle?.apply {
                        set("rental_car_id", car.id)
                        set("rental_start_date", startDate)
                        set("rental_end_date", endDate)
                    }
                    nav.navigate("rental-booking")
                },
                onSearch = rentalViewModel::loadCars,
                onClearFilter = { rentalViewModel.loadCars() }
            )
        }
        composable("rental-vendor") {
            RentalVendorOnboardingScreen(
                rentalViewModel.state.collectAsState().value,
                rentalViewModel::onboardVendor,
                onBack = { nav.popBackStack() },
                onAddVehicle = { nav.navigate("rental-vehicle") },
                onRefreshVehicles = rentalViewModel::loadVendorVehicles,
                onRefreshPayouts = rentalViewModel::loadVendorPayouts,
                onAddVehicleWithCar = { car ->
                    nav.currentBackStackEntry?.savedStateHandle?.set("rental_edit_car_id", car.id)
                    nav.navigate("rental-vehicle")
                },
                onLoadVehicleAvailability = rentalViewModel::loadVehicleUnavailability,
                onTakeVehicleOffMarket = rentalViewModel::takeVehicleOffMarket,
                onRestoreVehicleToMarket = rentalViewModel::restoreVehicleToMarket,
                onLoadVehicleCalendar = rentalViewModel::loadVehicleCalendar,
                onUpdateVendorProfile = rentalViewModel::updateVendor
            )
        }
        composable("rental-booking") {
            val carId = nav.previousBackStackEntry?.savedStateHandle?.get<String>("rental_car_id")
            val car = rentalViewModel.state.collectAsState().value.cars.firstOrNull { it.id == carId }
            if (car != null) {
                RentalBookingScreen(
                    car = car,
                    state = rentalViewModel.state.collectAsState().value,
                    wallet = homeViewModel.wallet.collectAsState().value,
                    onQuote = rentalViewModel::quoteBooking,
                    onBack = { nav.popBackStack() },
                    onAddMoney = { paymentViewModel.reset(); showFundingDialogSetter(true) },
                    onRefreshWallet = homeViewModel::refreshWallet,
                    onConfirm = { request, onDone ->
                        rentalViewModel.createBooking(request) {
                            homeViewModel.refreshWallet()
                            rentalViewModel.loadBookings()
                            onDone()
                        }
                    },
                    initialStart = nav.previousBackStackEntry?.savedStateHandle?.get<String>("rental_start_date"),
                    initialEnd = nav.previousBackStackEntry?.savedStateHandle?.get<String>("rental_end_date")
                )
            }
        }
        composable("rental-bookings") {
            RentalMyBookingsScreen(
                state = rentalViewModel.state.collectAsState().value,
                onRefresh = rentalViewModel::loadBookings,
                onBack = { nav.popBackStack() },
                onCancel = { bookingId, onDone ->
                    rentalViewModel.cancelBooking(bookingId, onDone)
                },
                onWalletRefresh = homeViewModel::refreshWallet
            )
        }
        composable("rental-vehicle") {
            RentalVehicleOnboardingScreen(
                state = rentalViewModel.state.collectAsState().value,
                onSubmit = { request, galleryPhotos, driverPhotoUri, onDone ->
                    rentalViewModel.onboardVehicle(request, galleryPhotos, driverPhotoUri, onDone)
                },
                onBack = { nav.popBackStack() },
                editingCar = nav.previousBackStackEntry?.savedStateHandle?.get<String>("rental_edit_car_id")?.let { id ->
                    rentalViewModel.state.collectAsState().value.vendorCars.firstOrNull { it.id == id }
                },
                onResubmit = { carId, request, galleryPhotos, driverPhotoUri, onDone ->
                    rentalViewModel.resubmitVehicle(carId, request, galleryPhotos, driverPhotoUri, onDone)
                }
            )
        }
        composable("recharge-history") {
            RechargeHistoryScreen(
                state = historyState,
                onFilterToday = rechargeHistoryViewModel::setToday,
                onFilterLast7 = rechargeHistoryViewModel::setLast7Days,
                onFilterMonth = rechargeHistoryViewModel::setThisMonth,
                onFilterCustom = rechargeHistoryViewModel::setCustom,
                onStatusFilter = rechargeHistoryViewModel::setStatus,
                onPageSizeChange = rechargeHistoryViewModel::setPageSize,
                onPreviousPage = { rechargeHistoryViewModel.goToPage(historyState.page - 1) },
                onNextPage = { rechargeHistoryViewModel.goToPage(historyState.page + 1) },
                onRefresh = rechargeHistoryViewModel::refreshHistory,
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
    val startDestinationId = nav.graph.findStartDestination().id
    if (route == "home") {
        nav.popBackStack(startDestinationId, false)
        return
    }
    nav.navigate(route) {
        popUpTo(startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
