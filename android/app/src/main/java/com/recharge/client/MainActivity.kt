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
                            override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val target = pendingRazorpayTarget
        val expectedOrderId = pendingRazorpayOrderId
        val returnedOrderId = paymentData?.orderId.orEmpty()
        pendingRazorpayTarget = null
        pendingRazorpayOrderId = null

        if (target == null) {
            walletPaymentViewModel.paymentFailed("Payment result received without a known checkout context. Please refresh your wallet or history.")
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
                orderId = returnedOrderId.ifBlank { expectedOrderId },
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
    val homeUser by homeViewModel.user.collectAsState()
    val homeWallet by homeViewModel.wallet.collectAsState()
    val homeLoading by homeViewModel.loading.collectAsState()
    val homeError by homeViewModel.error.collectAsState()
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
            "wallet" -> { rechargeHistoryViewModel.refreshAll(); homeViewModel.load() }
            "marketplace" -> Unit
            "car-rental" -> rentalViewModel.clearCarSearch()
            "rental-bookings" -> rentalViewModel.loadBookings()
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
        AddMoneyDialog(
            paymentState,
            { showFundingDialog = false; paymentViewModel.reset() },
            paymentViewModel::createOrder,
            paymentViewModel::reset,
            availableBalance = homeWallet?.availableBalance ?: BigDecimal.ZERO
        )
    }

    if (sideNav) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                Spacer(Modifier.height(8.dp))
                destinations.forEach { d -> ColoredNavigationRailItem(d, currentRoute, { navigateToTopLevel(nav, d.route) }) }
            }
            AppNavHost(
                nav = nav,
                currentRoute = currentRoute,
                homeViewModel = homeViewModel,
                profileViewModel = profileViewModel,
                rechargeViewModel = rechargeViewModel,
                rechargeHistoryViewModel = rechargeHistoryViewModel,
                rentalViewModel = rentalViewModel,
                walletViewModel = walletViewModel,
                historyState = historyState,
                homeUser = homeUser,
                homeWallet = homeWallet,
                homeLoading = homeLoading,
                homeError = homeError,
                profileState = profileState,
                rechargeState = rechargeState,
                rentalState = rentalState,
                walletUiState = walletUiState,
                showFundingDialogSetter = { showFundingDialog = it },
                paymentViewModel = paymentViewModel,
                highlightTransactionId = highlightTransactionId,
                authLogout = { authViewModel.logout() },
                onChooseContact = onChooseContact,
                modifier = Modifier.weight(1f
            ))
        }
    } else {
        Scaffold(bottomBar = { BottomNavigationBar(nav, destinations) }) { inner ->
            AppNavHost(
                nav = nav,
                currentRoute = currentRoute,
                homeViewModel = homeViewModel,
                profileViewModel = profileViewModel,
                rechargeViewModel = rechargeViewModel,
                rechargeHistoryViewModel = rechargeHistoryViewModel,
                rentalViewModel = rentalViewModel,
                walletViewModel = walletViewModel,
                historyState = historyState,
                homeUser = homeUser,
                homeWallet = homeWallet,
                homeLoading = homeLoading,
                homeError = homeError,
                profileState = profileState,
                rechargeState = rechargeState,
                rentalState = rentalState,
                walletUiState = walletUiState,
                showFundingDialogSetter = { showFundingDialog = it },
                paymentViewModel = paymentViewModel,
                highlightTransactionId = highlightTransactionId,
                authLogout = { authViewModel.logout() },
                onChooseContact = onChooseContact,
                modifier = Modifier.padding(inner
            ))
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
    homeUser: com.recharge.client.core.model.CurrentUserResponse?, homeWallet: com.recharge.client.core.model.WalletResponse?, homeLoading: Boolean, homeError: String?,
    profileState: ProfileUiState, rechargeState: RechargeUiState, rentalState: RentalUiState, walletUiState: WalletUiState,
    showFundingDialogSetter: (Boolean) -> Unit, paymentViewModel: WalletPaymentViewModel, highlightTransactionId: String?,
    authLogout: () -> Unit, onChooseContact: () -> Unit, modifier: Modifier = Modifier
) {
    NavHost(navController = nav, startDestination = "home", modifier = modifier.fillMaxSize()) {
        composable("home") {
            HomeScreen(
                user = homeUser,
                wallet = homeWallet,
                loading = homeLoading,
                commission = historyState.commission,
                latestRecharge = historyState.items.firstOrNull(),
                error = homeError,
                isVisible = currentRoute == "home",
                onRefresh = { homeViewModel.load(); rechargeHistoryViewModel.loadCommission() },
                onRefreshBalance = homeViewModel::refreshWallet,
                onRefreshEarnings = rechargeHistoryViewModel::loadCommission,
                onRecharge = { navigateToTopLevel(nav, "recharge") },
                onAddMoney = { paymentViewModel.reset(); showFundingDialogSetter(true) },
                onWithdraw = walletViewModel::withdraw,
                onClearWithdrawMessage = walletViewModel::clearWithdrawMessage,
                walletUiState = walletUiState,
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
    { rechargeViewModel.dismissResult(); homeViewModel.load(); rechargeHistoryViewModel.refreshAll(); navigateToTopLevel(nav, "home") },
    { paymentViewModel.reset(); showFundingDialogSetter(true) },
    rechargeViewModel::refreshWallet,
    rechargeViewModel::clear
)
        }
        composable("wallet") {
            WalletScreen(
                wallet = homeWallet,
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
            ProfileScreen(profileState, rentalState.vendor, profileViewModel::load, rentalViewModel::loadVendor, profileViewModel::save, profileViewModel::removePhoto, authLogout, homeViewModel::load, { nav.navigate("rental-vendor") }, currentRoute == "profile")
        }
        composable("marketplace") {
            MarketplaceScreen(onBack = { nav.popBackStack() }, onCarRental = { nav.navigate("car-rental") })
        }
        composable("car-rental") {
            CarRentalMarketplaceScreen(
                state = rentalState,
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
            val car = rentalState.cars.firstOrNull { it.id == carId }
            if (car != null) {
                RentalBookingScreen(
                    car = car,
                    state = rentalState,
                    wallet = homeViewModel.wallet.collectAsState().value,
                    onQuote = rentalViewModel::quoteBooking,
                    onBack = { nav.popBackStack() },
                    onAddMoney = { paymentViewModel.reset(); showFundingDialogSetter(true) },
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
                    rentalState.vendorCars.firstOrNull { it.id == id }
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
