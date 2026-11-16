package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.DeliveryAddress
import com.example.data.repository.BiteRouteRepository
import com.example.ui.BiteRouteViewModel
import com.example.ui.UiState
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.CartSummaryBar
import com.example.ui.components.CheckoutModalSheet
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.ManagerDashboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.TrackScreen
import com.example.ui.theme.BiteRouteTheme
import com.example.util.DeliveryNotificationHelper
import com.example.util.LocationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

object Routes {
  const val DISCOVER = "discover"
  const val TRACK = "track"
  const val PROFILE = "profile"
  const val MANAGER = "manager"
}

private data class NavDestinationItem(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    DeliveryNotificationHelper.ensureChannel(this)
    setContent {
      BiteRouteTheme {
        AppNavigation()
      }
    }
  }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser

  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    androidx.compose.runtime.key(user.uid) {
      BiteRouteAppShell(
        currentUserId = user.uid,
        displayName = user.displayName ?: "BiteRoute Guest",
        email = user.email ?: "",
        emailVerified = user.isEmailVerified,
        photoUrl = user.photoUrl?.toString(),
      )
    }
  }
}

@Composable
private fun BiteRouteAppShell(
  currentUserId: String,
  displayName: String,
  email: String,
  emailVerified: Boolean,
  photoUrl: String?,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  val snackbarHostState = remember { SnackbarHostState() }
  val navController = rememberNavController()

  val viewModel: BiteRouteViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
        val dbId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(dbId)
        BiteRouteViewModel(
          repository = BiteRouteRepository(db),
          currentUserId = currentUserId,
          userDisplayName = displayName,
          userEmail = email,
          emailVerified = emailVerified,
          userPhotoUrl = photoUrl,
        )
      }
    },
  )

  val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
  val menuState by viewModel.menuItems.collectAsStateWithLifecycle()
  val filteredMenu by viewModel.filteredMenuItems.collectAsStateWithLifecycle()
  val cart by viewModel.cart.collectAsStateWithLifecycle()
  val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
  val cartSubtotal by viewModel.cartSubtotalCents.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
  val addressesState by viewModel.addresses.collectAsStateWithLifecycle()
  val paymentsState by viewModel.paymentMethods.collectAsStateWithLifecycle()
  val profileState by viewModel.userProfile.collectAsStateWithLifecycle()
  val userOrdersState by viewModel.userOrders.collectAsStateWithLifecycle()
  val alertHistory by viewModel.alertHistory.collectAsStateWithLifecycle()
  val liveGpsFix by viewModel.liveGpsFix.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

  val adminOrdersState by viewModel.adminOrders.collectAsStateWithLifecycle()
  val adminInventoryState by viewModel.adminInventory.collectAsStateWithLifecycle()
  val adminReviewsState by viewModel.adminReviews.collectAsStateWithLifecycle()

  var showCheckoutSheet by remember { mutableStateOf(false) }
  var hasNotificationPermission by remember {
    mutableStateOf(DeliveryNotificationHelper.hasNotificationPermission(context))
  }

  val notificationLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasNotificationPermission = granted
    if (granted) {
      scope.launch { snackbarHostState.showSnackbar("Delivery status notifications enabled.") }
    }
  }

  val acquireGpsAndSaveIfNeeded: (Boolean) -> Unit = { saveIfEmpty ->
    scope.launch {
      val result = LocationHelper.fetchCurrentGps(context)
      result.onSuccess { fix ->
        viewModel.updateLiveGpsFix(fix)
        val currentAddrs = (addressesState as? UiState.Success)?.data ?: emptyList()
        if (saveIfEmpty && currentAddrs.isEmpty()) {
          viewModel.saveAddress(
            DeliveryAddress(
              label = "Current GPS",
              street = fix.streetHint ?: "Pinned GPS Location",
              city = fix.cityHint ?: "Current Area",
              lat = fix.lat,
              lng = fix.lng,
              isDefault = true,
            )
          )
        } else {
          snackbarHostState.showSnackbar("GPS location locked.")
        }
      }.onFailure { err ->
        snackbarHostState.showSnackbar(err.localizedMessage ?: "Unable to fetch GPS location.")
      }
    }
  }

  val locationLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      acquireGpsAndSaveIfNeeded(true)
    } else {
      scope.launch {
        snackbarHostState.showSnackbar("Location permission denied. You can enter an address manually in Profile.")
      }
    }
  }

  val requestGps: (Boolean) -> Unit = { autoSave ->
    if (LocationHelper.hasLocationPermission(context)) {
      acquireGpsAndSaveIfNeeded(autoSave)
    } else {
      locationLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        )
      )
    }
  }

  LaunchedEffect(Unit) {
    viewModel.snackbarEvents.collect { msg ->
      snackbarHostState.showSnackbar(msg)
    }
  }

  LaunchedEffect(Unit) {
    viewModel.alertEvents.collect { alert ->
      DeliveryNotificationHelper.postOrderAlert(
        context = context,
        orderId = alert.orderId,
        title = alert.title,
        body = alert.message,
      )
      snackbarHostState.showSnackbar(alert.title)
    }
  }

  val navItems = buildList {
    add(NavDestinationItem(Routes.DISCOVER, "Discover", Icons.Rounded.RestaurantMenu, Icons.Outlined.RestaurantMenu))
    add(NavDestinationItem(Routes.TRACK, "Track", Icons.Rounded.DeliveryDining, Icons.Outlined.DeliveryDining))
    add(NavDestinationItem(Routes.PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.Person))
    if (isAdmin) {
      add(NavDestinationItem(Routes.MANAGER, "Manager", Icons.Rounded.AdminPanelSettings, Icons.Outlined.AdminPanelSettings))
    }
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route ?: Routes.DISCOVER

  val navigateToTab: (String) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (!useRail) {
          NavigationBar {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateToTab(item.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                  )
                },
                label = { Text(item.label) },
                modifier = Modifier.testTag("nav_${item.route}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (useRail) {
          NavigationRail {
            navItems.forEach { item ->
              val selected = currentRoute == item.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateToTab(item.route) },
                icon = {
                  Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                  )
                },
                label = { Text(item.label) },
                modifier = Modifier.testTag("rail_${item.route}"),
              )
            }
          }
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxSize(),
          contentAlignment = Alignment.TopCenter,
        ) {
          val addresses = (addressesState as? UiState.Success)?.data ?: emptyList()
          val payments = (paymentsState as? UiState.Success)?.data ?: emptyList()

          NavHost(
            navController = navController,
            startDestination = Routes.DISCOVER,
            modifier = Modifier.widthIn(max = 760.dp),
          ) {
            composable(Routes.DISCOVER) {
              DiscoverScreen(
                menuState = menuState,
                filteredItems = filteredMenu,
                cart = cart,
                searchQuery = searchQuery,
                selectedCategory = selectedCategory,
                addresses = addresses,
                liveGpsFix = liveGpsFix,
                onSearchChange = viewModel::setSearchQuery,
                onSelectCategory = viewModel::setSelectedCategory,
                onAddToCart = viewModel::addToCart,
                onDecrementCart = viewModel::decrementCart,
                onRequestGpsLocation = { requestGps(true) },
                onOpenAddresses = { navigateToTab(Routes.PROFILE) },
              )
            }

            composable(Routes.TRACK) {
              TrackScreen(
                ordersState = userOrdersState,
                alerts = alertHistory,
                hasNotificationPermission = hasNotificationPermission,
                onRequestNotificationPermission = {
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                },
                onCancelOrder = viewModel::cancelOrder,
                onSubmitFeedback = viewModel::submitFeedback,
                onBrowseMenu = { navigateToTab(Routes.DISCOVER) },
              )
            }

            composable(Routes.PROFILE) {
              ProfileScreen(
                displayName = displayName,
                email = email,
                isAdmin = isAdmin,
                profileState = profileState,
                addressesState = addressesState,
                paymentsState = paymentsState,
                ordersState = userOrdersState,
                liveGpsFix = liveGpsFix,
                onDetectGps = { requestGps(false) },
                onSaveAddress = viewModel::saveAddress,
                onDeleteAddress = viewModel::deleteAddress,
                onSavePayment = viewModel::savePaymentMethod,
                onDeletePayment = viewModel::deletePaymentMethod,
                onUpdateAlertPrefs = viewModel::updateAlertPreferences,
                onReorder = { order ->
                  viewModel.reorder(order)
                  navigateToTab(Routes.DISCOVER)
                },
                onSubmitFeedback = viewModel::submitFeedback,
                onSignOut = {
                  signOutUser(credentialManager = credentialManager, scope = scope)
                },
              )
            }

            if (isAdmin) {
              composable(Routes.MANAGER) {
                ManagerDashboardScreen(
                  ordersState = adminOrdersState,
                  menuState = menuState,
                  inventoryState = adminInventoryState,
                  reviewsState = adminReviewsState,
                  onAdvanceOrder = viewModel::advanceOrder,
                  onUpdateCourierTelemetry = viewModel::updateCourierTelemetry,
                  onSaveMenuItem = viewModel::saveMenuItem,
                  onDeleteMenuItem = viewModel::deleteMenuItem,
                  onSaveInventoryItem = viewModel::saveInventoryItem,
                  onAdjustInventoryStock = viewModel::adjustInventoryStock,
                  onSeedCatalog = viewModel::seedStarterCatalog,
                )
              }
            }
          }

          androidx.compose.animation.AnimatedVisibility(
            visible = cartCount > 0 && currentRoute == Routes.DISCOVER,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .widthIn(max = 600.dp)
              .padding(horizontal = 16.dp, vertical = 12.dp),
          ) {
            CartSummaryBar(
              count = cartCount,
              subtotalCents = cartSubtotal,
              onClick = { showCheckoutSheet = true },
            )
          }

          if (showCheckoutSheet) {
            CheckoutModalSheet(
              cartItems = cart.values.toList(),
              addresses = addresses,
              paymentMethods = payments,
              isBusy = isBusy,
              onDismiss = { showCheckoutSheet = false },
              onAddQuickAddressWithGps = { requestGps(true) },
              onOpenProfilePayments = {
                showCheckoutSheet = false
                navigateToTab(Routes.PROFILE)
              },
              onConfirmOrder = { addr, pm, tip ->
                viewModel.placeOrder(addr, pm, tip) {
                  showCheckoutSheet = false
                  navigateToTab(Routes.TRACK)
                }
              },
            )
          }
        }
      }
    }
  }
}
