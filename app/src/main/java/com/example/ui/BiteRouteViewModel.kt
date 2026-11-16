package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CartItem
import com.example.data.model.DeliveryAddress
import com.example.data.model.DeliveryAlert
import com.example.data.model.InventoryItem
import com.example.data.model.MenuItem
import com.example.data.model.Order
import com.example.data.model.OrderReview
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.UserProfile
import com.example.data.repository.BiteRouteRepository
import com.example.util.GpsFix
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class BiteRouteViewModel(
  private val repository: BiteRouteRepository,
  val currentUserId: String,
  val userDisplayName: String,
  val userEmail: String,
  private val emailVerified: Boolean,
  val userPhotoUrl: String?,
) : ViewModel() {

  private fun <T> Flow<T>.toUiStateFlow(label: String): StateFlow<UiState<T>> =
    map<T, UiState<T>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing $label", rootError)
        emit(UiState.Error(rootError.message ?: "Failed to load $label"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  private val _isAdmin = MutableStateFlow(false)
  val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

  val userProfile: StateFlow<UiState<UserProfile?>> =
    repository.observeUserProfile(currentUserId).toUiStateFlow("profile")

  val addresses: StateFlow<UiState<List<DeliveryAddress>>> =
    repository.observeAddresses(currentUserId).toUiStateFlow("addresses")

  val paymentMethods: StateFlow<UiState<List<PaymentMethod>>> =
    repository.observePaymentMethods(currentUserId).toUiStateFlow("payment methods")

  val menuItems: StateFlow<UiState<List<MenuItem>>> =
    repository.observeMenuItems().toUiStateFlow("menu")

  val userOrders: StateFlow<UiState<List<Order>>> =
    repository.observeUserOrders(currentUserId).toUiStateFlow("orders")

  val userReviews: StateFlow<UiState<List<OrderReview>>> =
    repository.observeUserReviews(currentUserId).toUiStateFlow("reviews")

  val adminOrders: StateFlow<UiState<List<Order>>> =
    _isAdmin
      .flatMapLatest { admin ->
        if (admin) repository.observeAllOrdersAdmin() else flowOf(emptyList())
      }
      .toUiStateFlow("all orders")

  val adminInventory: StateFlow<UiState<List<InventoryItem>>> =
    _isAdmin
      .flatMapLatest { admin ->
        if (admin) repository.observeInventory() else flowOf(emptyList())
      }
      .toUiStateFlow("inventory")

  val adminReviews: StateFlow<UiState<List<OrderReview>>> =
    _isAdmin
      .flatMapLatest { admin ->
        if (admin) repository.observeAllReviewsAdmin() else flowOf(emptyList())
      }
      .toUiStateFlow("all reviews")

  private val _cart = MutableStateFlow<Map<String, CartItem>>(emptyMap())
  val cart: StateFlow<Map<String, CartItem>> = _cart.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _selectedCategory = MutableStateFlow("All")
  val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

  private val _liveGpsFix = MutableStateFlow<GpsFix?>(null)
  val liveGpsFix: StateFlow<GpsFix?> = _liveGpsFix.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val snackbarEvents = _snackbarEvents.asSharedFlow()

  private val _alertEvents = MutableSharedFlow<DeliveryAlert>(extraBufferCapacity = 8)
  val alertEvents = _alertEvents.asSharedFlow()

  private val _alertHistory = MutableStateFlow<List<DeliveryAlert>>(emptyList())
  val alertHistory: StateFlow<List<DeliveryAlert>> = _alertHistory.asStateFlow()

  private val knownOrderStatuses = mutableMapOf<String, String>()
  private var initialOrdersLoaded = false

  val cartCount: StateFlow<Int> =
    cart
      .map { map -> map.values.sumOf { it.quantity } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

  val cartSubtotalCents: StateFlow<Long> =
    cart
      .map { map -> map.values.sumOf { it.menuItem.priceCents * it.quantity } }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0L)

  val filteredMenuItems: StateFlow<List<MenuItem>> =
    combine(menuItems, searchQuery, selectedCategory) { menuState, query, category ->
      val items = (menuState as? UiState.Success)?.data ?: emptyList()
      items.filter { item ->
        val catMatch = category == "All" || item.category.equals(category, ignoreCase = true)
        val q = query.trim()
        val queryMatch =
          q.isBlank() ||
            item.name.contains(q, ignoreCase = true) ||
            item.description.contains(q, ignoreCase = true) ||
            item.category.contains(q, ignoreCase = true)
        catMatch && queryMatch
      }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

  init {
    viewModelScope.launch {
      repository.ensureUserProfile(userDisplayName, userEmail, userPhotoUrl)
      val admin = repository.checkIsAdmin(currentUserId, userEmail, emailVerified)
      _isAdmin.value = admin
      if (admin) {
        repository.seedStarterCatalogIfEmpty()
      }
    }

    // Monitor live order status transitions for automated push alerts & in-app feed
    viewModelScope.launch {
      combine(userOrders, userProfile) { ordersState, profileState ->
        ordersState to (profileState as? UiState.Success)?.data
      }.collect { (ordersState, profile) ->
        if (ordersState is UiState.Success) {
          val orders = ordersState.data
          if (!initialOrdersLoaded) {
            orders.forEach { knownOrderStatuses[it.id] = it.status }
            initialOrdersLoaded = true
          } else {
            orders.forEach { order ->
              val prev = knownOrderStatuses[order.id]
              if (prev != null && prev != order.status) {
                knownOrderStatuses[order.id] = order.status
                val statusEnum = order.orderStatus
                val statusAlertsEnabled = profile?.orderStatusAlerts ?: true
                val arrivalAlertsEnabled = profile?.courierArrivalAlerts ?: true
                val shouldNotify = when (statusEnum) {
                  OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED -> arrivalAlertsEnabled || statusAlertsEnabled
                  else -> statusAlertsEnabled
                }
                if (shouldNotify) {
                  val alert = DeliveryAlert(
                    id = "${order.id}_${order.status}_${System.currentTimeMillis()}",
                    orderId = order.id,
                    title = "Order #${order.id.takeLast(5).uppercase()}: ${statusEnum.displayLabel}",
                    message = when (statusEnum) {
                      OrderStatus.PREPARING -> "Our kitchen is firing ${order.itemsSummary}."
                      OrderStatus.READY_FOR_PICKUP -> "${order.courierName} is picking up your order."
                      OrderStatus.OUT_FOR_DELIVERY -> "${order.courierName} is en route — ETA ${order.etaMinutes} min."
                      OrderStatus.DELIVERED -> "Delivered to ${order.deliveryAddressLabel}! Tap to rate your meal."
                      OrderStatus.CANCELLED -> "Your order has been cancelled."
                      OrderStatus.PLACED -> "Order received by BiteRoute Kitchen."
                    },
                    status = statusEnum,
                    timestampMillis = System.currentTimeMillis(),
                  )
                  _alertHistory.update { listOf(alert) + it.take(19) }
                  _alertEvents.tryEmit(alert)
                }
              } else if (prev == null) {
                knownOrderStatuses[order.id] = order.status
              }
            }
          }
        }
      }
    }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setSelectedCategory(category: String) {
    _selectedCategory.value = category
  }

  fun updateLiveGpsFix(fix: GpsFix) {
    _liveGpsFix.value = fix
  }

  fun addToCart(item: MenuItem) {
    if (!item.isAvailable || item.stockCount <= 0L) {
      _snackbarEvents.tryEmit("${item.name} is currently out of stock.")
      return
    }
    _cart.update { current ->
      val existing = current[item.id]
      val nextQty = ((existing?.quantity ?: 0) + 1).coerceAtMost(item.stockCount.toInt().coerceAtLeast(1))
      current + (item.id to CartItem(item, nextQty))
    }
  }

  fun decrementCart(itemId: String) {
    _cart.update { current ->
      val existing = current[itemId] ?: return@update current
      if (existing.quantity <= 1) {
        current - itemId
      } else {
        current + (itemId to existing.copy(quantity = existing.quantity - 1))
      }
    }
  }

  fun clearCart() {
    _cart.value = emptyMap()
  }

  fun reorder(order: Order) {
    val liveMenu = (menuItems.value as? UiState.Success)?.data ?: emptyList()
    val newCart = mutableMapOf<String, CartItem>()
    order.items.forEach { line ->
      val matched = liveMenu.firstOrNull { it.id == line.menuItemId || it.name == line.name }
      if (matched != null && matched.isAvailable && matched.stockCount > 0) {
        newCart[matched.id] = CartItem(matched, line.quantity.toInt().coerceAtLeast(1))
      }
    }
    if (newCart.isEmpty()) {
      _snackbarEvents.tryEmit("Items from that order are currently unavailable.")
    } else {
      _cart.value = newCart
      _snackbarEvents.tryEmit("Added ${newCart.values.sumOf { it.quantity }} items to your bag.")
    }
  }

  fun placeOrder(
    address: DeliveryAddress,
    paymentMethod: PaymentMethod,
    tipCents: Long,
    onPlaced: (String) -> Unit,
  ) {
    val items = _cart.value.values.toList()
    if (items.isEmpty()) return
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.placeOrder(
        customerName = userDisplayName,
        address = address,
        paymentMethod = paymentMethod,
        cartItems = items,
        tipCents = tipCents,
      )
      _isBusy.value = false
      res.onSuccess { orderId ->
        _cart.value = emptyMap()
        _snackbarEvents.tryEmit("Order placed! Live GPS tracking started.")
        onPlaced(orderId)
      }.onFailure { err ->
        _snackbarEvents.tryEmit(err.localizedMessage ?: "Failed to place order.")
      }
    }
  }

  fun cancelOrder(orderId: String) {
    viewModelScope.launch {
      repository.cancelOrder(orderId)
        .onSuccess { _snackbarEvents.tryEmit("Order cancelled.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Unable to cancel order.") }
    }
  }

  fun saveAddress(address: DeliveryAddress) {
    val current = (addresses.value as? UiState.Success)?.data ?: emptyList()
    viewModelScope.launch {
      _isBusy.value = true
      repository.saveAddress(address, current)
        .onSuccess { _snackbarEvents.tryEmit("Address saved.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to save address.") }
      _isBusy.value = false
    }
  }

  fun deleteAddress(addressId: String) {
    viewModelScope.launch {
      repository.deleteAddress(addressId)
        .onSuccess { _snackbarEvents.tryEmit("Address removed.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to remove address.") }
    }
  }

  fun savePaymentMethod(method: PaymentMethod) {
    val current = (paymentMethods.value as? UiState.Success)?.data ?: emptyList()
    viewModelScope.launch {
      _isBusy.value = true
      repository.savePaymentMethod(method, current)
        .onSuccess { _snackbarEvents.tryEmit("Payment method saved.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to save payment method.") }
      _isBusy.value = false
    }
  }

  fun deletePaymentMethod(methodId: String) {
    viewModelScope.launch {
      repository.deletePaymentMethod(methodId)
        .onSuccess { _snackbarEvents.tryEmit("Payment method removed.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to remove payment method.") }
    }
  }

  fun updateAlertPreferences(
    orderStatusAlerts: Boolean,
    courierArrivalAlerts: Boolean,
    promoAlerts: Boolean,
  ) {
    viewModelScope.launch {
      repository.updateAlertPreferences(orderStatusAlerts, courierArrivalAlerts, promoAlerts)
        .onSuccess { _snackbarEvents.tryEmit("Alert preferences updated.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to update preferences.") }
    }
  }

  fun submitFeedback(
    orderId: String,
    rating: Int,
    tags: List<String>,
    comment: String,
    onSuccess: () -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.submitOrderFeedback(orderId, userDisplayName, rating, tags, comment)
        .onSuccess {
          _snackbarEvents.tryEmit("Thank you for your feedback!")
          onSuccess()
        }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to submit review.") }
      _isBusy.value = false
    }
  }

  // --- MANAGER ADMIN ACTIONS ---
  fun advanceOrder(order: Order) {
    viewModelScope.launch {
      repository.adminAdvanceOrder(order)
        .onSuccess { _snackbarEvents.tryEmit("Order #${order.id.takeLast(5).uppercase()} updated.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to advance order.") }
    }
  }

  fun updateCourierTelemetry(order: Order, progressFraction: Double, etaMinutes: Long) {
    viewModelScope.launch {
      repository.adminUpdateCourierTelemetry(order, progressFraction, etaMinutes)
        .onSuccess { _snackbarEvents.tryEmit("Courier GPS coordinates updated.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Telemetry update failed.") }
    }
  }

  fun saveMenuItem(item: MenuItem) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.saveMenuItem(item)
        .onSuccess { _snackbarEvents.tryEmit("Menu item saved.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to save menu item.") }
      _isBusy.value = false
    }
  }

  fun deleteMenuItem(itemId: String) {
    viewModelScope.launch {
      repository.deleteMenuItem(itemId)
        .onSuccess { _snackbarEvents.tryEmit("Menu item removed.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to delete item.") }
    }
  }

  fun saveInventoryItem(item: InventoryItem) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.saveInventoryItem(item)
        .onSuccess { _snackbarEvents.tryEmit("Inventory SKU saved.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to save SKU.") }
      _isBusy.value = false
    }
  }

  fun adjustInventoryStock(skuId: String, newQty: Long) {
    viewModelScope.launch {
      repository.adjustInventoryStock(skuId, newQty)
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to adjust stock.") }
    }
  }

  fun seedStarterCatalog() {
    viewModelScope.launch {
      _isBusy.value = true
      repository.seedStarterCatalogIfEmpty()
        .onSuccess { _snackbarEvents.tryEmit("Starter catalog & inventory synced.") }
        .onFailure { _snackbarEvents.tryEmit(it.localizedMessage ?: "Failed to seed catalog.") }
      _isBusy.value = false
    }
  }

  private companion object {
    const val TAG = "BiteRouteVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
