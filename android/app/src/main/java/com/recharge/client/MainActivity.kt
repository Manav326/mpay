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
import com.recharge.client.features.services.CarRentalComingSoonScreen
import com.recharge.client.features.rental.RentalVendorOnboardingScreen
import com.recharge.client.features.rental.CarRentalMarketplaceScreen
import com.recharge.client.features.rental.RentalVehicleOnboardingScreen
import com.recharge.client.features.wallet.AddMoneyDialog
import com.recharge.client.features.wallet.WalletScreen
import com.recharge.client.core.payment.PayUCheckoutBridge
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    private val contactPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val raw = cursor.getString(0).orEmpty()
                    val digits = raw.filter { it.isDigit() }
                    val normalized = when {
                        digits.length == 10 -> digits
                        digits.length > 10 && digits.endsWith("10") -> digits.takeLast(10)
                        digits.length >= 10 -> digits.takeLast(10)
                        else -> ""
                    }
                    if (normalized.length == 10) rechargeViewModel.setMobile(normalized)
                }
            }
        }
    }
    private val walletPaymentViewModel: WalletPaymentViewModel by viewModels()
    private val rechargeViewModel: RechargeViewModel by viewModels()
    private var rechargeGatewayVerifier: ((String, String, String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Checkout.preload(applicationContext)
        setContent { RechargeTheme { AppRoot(::startWalletPaymentCheckout, ::startGatewayRechargeCheckout, walletPaymentViewModel, { contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, rechargeViewModel = rechargeViewModel) } }
    }

    private fun startWalletPaymentCheckout(order: PaymentOrderResponse) {
        try {
            when {
                order.provider.equals("mock", true) -> {
                    walletPaymentViewModel.verifyPayment("mock", null, order.orderId, null)
                }
                order.provider.equals("payu", true) -> {
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
                    checkout.open(this, options)
                }
            }
        } catch (e: Exception) {
            walletPaymentViewModel.paymentFailed(e.message ?: "Unable to open payment checkout")
        }
    }

    private fun startGatewayRechargeCheckout(order: PaymentOrderResponse, rechargeViewModel: RechargeViewModel) {
        try {
            if (order.provider.equals("payu", true)) {
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
                provider = "razorpay",
                paymentId = razorpayPaymentId,
                orderId = paymentData?.orderId.orEmpty(),
                signature = paymentData?.signature
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
            is PaymentUiState.OrderCreated -> startWalletPayment((paymentState as PaymentUiState.OrderCreated).order)
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
            "car-rental" -> rentalViewModel.loadCars()
            "rental-vendor" -> rentalViewModel.loadVendor()
            "rental-vehicle" -> rentalViewModel.loadVendorVehicles()
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
            }
            is RechargeActionState.Pending -> {
                highlightTransactionId = action.response.transactionId
                rechargeHistoryViewModel.refreshAll()
                homeViewModel.load()
            }
            is RechargeActionState.Failure -> {
                rechargeHistoryViewModel.refreshAll()
                homeViewModel.load()
            }
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
                onCarRental = { nav.navigate("car-rental") }
            )
        }
        composable("recharge") {
            RechargeScreen(
    rechargeViewModel.state.collectAsState().value,
    profileViewModel.state.collectAsState().value.user?.commissionRate,
    rechargeViewModel::setMobile,
    onChooseContact,
    rechargeViewModel::detectAndLoad,
    rechargeViewModel::refreshPlans,
    rechargeViewModel::selectPlan,
    rechargeViewModel::executeSelectedPlan,
    rechargeViewModel::startGatewayRechargePayment,
    { rechargeViewModel.dismissResult(); homeViewModel.load(); rechargeHistoryViewModel.refreshAll(); navigateToTopLevel(nav, "home") },
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
            ProfileScreen(profileViewModel.state.collectAsState().value, profileViewModel::load, profileViewModel::save, profileViewModel::removePhoto, authLogout, homeViewModel::load, { nav.navigate("rental-vendor") }, currentRoute == "profile")
        }
        composable("car-rental") {
            CarRentalMarketplaceScreen(rentalViewModel.state.collectAsState().value, onBack = { nav.popBackStack() }, onBook = { car -> nav.currentBackStackEntry?.savedStateHandle?.set("rental_car_id", car.id); nav.navigate("rental-booking") })
        }
        composable("rental-vendor") {
            RentalVendorOnboardingScreen(rentalViewModel.state.collectAsState().value, rentalViewModel::onboardVendor, onBack = { nav.popBackStack() }, onAddVehicle = { nav.navigate("rental-vehicle") })
        }
        composable("rental-booking") {
            val carId = nav.previousBackStackEntry?.savedStateHandle?.get<String>("rental_car_id")
            val car = rentalViewModel.state.collectAsState().value.cars.firstOrNull { it.id == carId }
            if (car != null) {
                RentalBookingScreen(car = car, state = rentalViewModel.state.collectAsState().value, onQuote = rentalViewModel::quoteBooking, onBack = { nav.popBackStack() }, onConfirm = rentalViewModel::createBooking)
            }
        }
        composable("rental-vehicle") {
            RentalVehicleOnboardingScreen(rentalViewModel.state.collectAsState().value, rentalViewModel::onboardVehicle, onBack = { nav.popBackStack() })
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
