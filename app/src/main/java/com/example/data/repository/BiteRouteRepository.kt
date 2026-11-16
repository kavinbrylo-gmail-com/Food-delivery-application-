package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.DeliveryAddress
import com.example.data.model.InventoryItem
import com.example.data.model.MenuItem
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.OrderReview
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong

class BiteRouteRepository(private val db: FirebaseFirestore) {

  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  private fun <T> Flow<T>.wrapFirestoreErrors(
    operationType: OperationType,
    path: String,
    expectedUid: String? = null,
  ): Flow<T> = catch { error ->
    val firestoreError =
      generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    val exceptionToLog = firestoreError ?: (error as? Exception)
    if (exceptionToLog != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
    throw error
  }

  // --- ADMIN CHECK ---
  suspend fun checkIsAdmin(uid: String, email: String?, emailVerified: Boolean): Boolean {
    if (email == BOOTSTRAP_ADMIN_EMAIL && emailVerified) return true
    return try {
      val doc = db.collection("admins").document(uid).get().await()
      doc.exists()
    } catch (_: Exception) {
      false
    }
  }

  // --- USER PROFILE & PREFERENCES ---
  fun observeUserProfile(userId: String): Flow<UserProfile?> {
    val path = "users/$userId"
    return db.collection("users")
      .document(userId)
      .snapshots()
      .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
      .wrapFirestoreErrors(OperationType.GET, path, userId)
  }

  suspend fun ensureUserProfile(
    displayName: String,
    email: String,
    photoUrl: String?,
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      val snap = docRef.get().await()
      if (!snap.exists()) {
        val payload = buildMap<String, Any> {
          put("userId", uid)
          put("displayName", displayName.ifBlank { "BiteRoute Guest" }.take(80))
          put("email", email.ifBlank { "guest@biteroute.app" }.take(120))
          if (!photoUrl.isNullOrBlank()) put("photoUrl", photoUrl.take(500))
          put("orderStatusAlerts", true)
          put("courierArrivalAlerts", true)
          put("promoAlerts", false)
          put("createdAt", FieldValue.serverTimestamp())
          put("updatedAt", FieldValue.serverTimestamp())
        }
        docRef.set(payload).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun updateAlertPreferences(
    orderStatusAlerts: Boolean,
    courierArrivalAlerts: Boolean,
    promoAlerts: Boolean,
  ): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    return try {
      docRef
        .update(
          mapOf(
            "orderStatusAlerts" to orderStatusAlerts,
            "courierArrivalAlerts" to courierArrivalAlerts,
            "promoAlerts" to promoAlerts,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  // --- SAVED DELIVERY ADDRESSES ---
  fun observeAddresses(userId: String): Flow<List<DeliveryAddress>> {
    val path = "users/$userId/addresses"
    return db.collection("users")
      .document(userId)
      .collection("addresses")
      .snapshots()
      .map { snap ->
        snap.toObjects(DeliveryAddress::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.isDefault }
      }
      .wrapFirestoreErrors(OperationType.LIST, path, userId)
  }

  suspend fun saveAddress(address: DeliveryAddress, existingAddresses: List<DeliveryAddress>): Result<String> {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("addresses")
    val docRef = if (address.id.isBlank()) colRef.document() else colRef.document(address.id)
    return try {
      val batch = db.batch()
      if (address.isDefault) {
        existingAddresses.filter { it.isDefault && it.id != docRef.id }.forEach { other ->
          batch.update(
            colRef.document(other.id),
            mapOf("isDefault" to false, "updatedAt" to FieldValue.serverTimestamp())
          )
        }
      }
      if (address.id.isBlank()) {
        val payload = buildMap<String, Any> {
          put("label", address.label.trim().take(30))
          put("street", address.street.trim().take(150))
          if (!address.apartment.isNullOrBlank()) put("apartment", address.apartment.trim().take(50))
          put("city", address.city.trim().take(80))
          if (!address.instructions.isNullOrBlank()) put("instructions", address.instructions.trim().take(200))
          put("lat", address.lat.coerceIn(-90.0, 90.0))
          put("lng", address.lng.coerceIn(-180.0, 180.0))
          put("isDefault", address.isDefault || existingAddresses.isEmpty())
          put("createdAt", FieldValue.serverTimestamp())
          put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.set(docRef, payload)
      } else {
        val updates = buildMap<String, Any> {
          put("label", address.label.trim().take(30))
          put("street", address.street.trim().take(150))
          if (!address.apartment.isNullOrBlank()) put("apartment", address.apartment.trim().take(50))
          put("city", address.city.trim().take(80))
          if (!address.instructions.isNullOrBlank()) put("instructions", address.instructions.trim().take(200))
          put("lat", address.lat.coerceIn(-90.0, 90.0))
          put("lng", address.lng.coerceIn(-180.0, 180.0))
          put("isDefault", address.isDefault)
          put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.update(docRef, updates)
      }
      batch.commit().await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteAddress(addressId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("addresses").document(addressId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- SAVED PAYMENT METHODS ---
  fun observePaymentMethods(userId: String): Flow<List<PaymentMethod>> {
    val path = "users/$userId/payment_methods"
    return db.collection("users")
      .document(userId)
      .collection("payment_methods")
      .snapshots()
      .map { snap ->
        snap.toObjects(PaymentMethod::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.isDefault }
      }
      .wrapFirestoreErrors(OperationType.LIST, path, userId)
  }

  suspend fun savePaymentMethod(method: PaymentMethod, existing: List<PaymentMethod>): Result<String> {
    val uid = requireUserId()
    val colRef = db.collection("users").document(uid).collection("payment_methods")
    val docRef = if (method.id.isBlank()) colRef.document() else colRef.document(method.id)
    return try {
      val batch = db.batch()
      if (method.isDefault) {
        existing.filter { it.isDefault && it.id != docRef.id }.forEach { other ->
          batch.update(
            colRef.document(other.id),
            mapOf("isDefault" to false, "updatedAt" to FieldValue.serverTimestamp())
          )
        }
      }
      if (method.id.isBlank()) {
        val payload = buildMap<String, Any> {
          put("type", method.type)
          put("brand", method.brand.trim().take(30))
          put("last4", method.last4.trim().take(4))
          if (!method.expiry.isNullOrBlank()) put("expiry", method.expiry.trim().take(10))
          put("cardholderName", method.cardholderName.trim().take(80))
          put("isDefault", method.isDefault || existing.isEmpty())
          put("createdAt", FieldValue.serverTimestamp())
          put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.set(docRef, payload)
      } else {
        val updates = buildMap<String, Any> {
          put("type", method.type)
          put("brand", method.brand.trim().take(30))
          put("last4", method.last4.trim().take(4))
          if (!method.expiry.isNullOrBlank()) put("expiry", method.expiry.trim().take(10))
          put("cardholderName", method.cardholderName.trim().take(80))
          put("isDefault", method.isDefault)
          put("updatedAt", FieldValue.serverTimestamp())
        }
        batch.update(docRef, updates)
      }
      batch.commit().await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deletePaymentMethod(methodId: String): Result<Unit> {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("payment_methods").document(methodId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- MENU ITEMS ---
  fun observeMenuItems(): Flow<List<MenuItem>> {
    val path = "menu_items"
    return db.collection(path)
      .snapshots()
      .map { snap ->
        snap.toObjects(MenuItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.category }
      }
      .wrapFirestoreErrors(OperationType.LIST, path)
  }

  suspend fun saveMenuItem(item: MenuItem): Result<String> {
    requireUserId()
    val colRef = db.collection("menu_items")
    val docRef = if (item.id.isBlank()) colRef.document() else colRef.document(item.id)
    return try {
      if (item.id.isBlank()) {
        val payload = mapOf(
          "name" to item.name.trim().take(80),
          "description" to item.description.trim().take(300),
          "category" to item.category.trim().take(40),
          "priceCents" to item.priceCents.coerceAtLeast(100L),
          "prepMinutes" to item.prepMinutes.coerceIn(1L, 180L),
          "calories" to item.calories.coerceIn(0L, 5000L),
          "rating" to item.rating.coerceIn(0.0, 5.0),
          "imageKey" to item.imageKey.ifBlank { "bowl" },
          "isAvailable" to item.isAvailable,
          "stockCount" to item.stockCount.coerceIn(0L, 10000L),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val updates = mapOf(
          "name" to item.name.trim().take(80),
          "description" to item.description.trim().take(300),
          "category" to item.category.trim().take(40),
          "priceCents" to item.priceCents.coerceAtLeast(100L),
          "prepMinutes" to item.prepMinutes.coerceIn(1L, 180L),
          "calories" to item.calories.coerceIn(0L, 5000L),
          "rating" to item.rating.coerceIn(0.0, 5.0),
          "imageKey" to item.imageKey.ifBlank { "bowl" },
          "isAvailable" to item.isAvailable,
          "stockCount" to item.stockCount.coerceIn(0L, 10000L),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(updates).await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteMenuItem(itemId: String): Result<Unit> {
    requireUserId()
    val docRef = db.collection("menu_items").document(itemId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  // --- INVENTORY (ADMIN ONLY) ---
  fun observeInventory(): Flow<List<InventoryItem>> {
    val path = "inventory"
    return db.collection(path)
      .snapshots()
      .map { snap ->
        snap.toObjects(InventoryItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.quantityOnHand }
      }
      .wrapFirestoreErrors(OperationType.LIST, path)
  }

  suspend fun saveInventoryItem(item: InventoryItem): Result<String> {
    requireUserId()
    val colRef = db.collection("inventory")
    val docRef = if (item.id.isBlank()) colRef.document() else colRef.document(item.id)
    return try {
      if (item.id.isBlank()) {
        val payload = mapOf(
          "skuName" to item.skuName.trim().take(80),
          "category" to item.category.trim().take(40),
          "quantityOnHand" to item.quantityOnHand.coerceIn(0L, 100000L),
          "unit" to item.unit.trim().take(20),
          "reorderThreshold" to item.reorderThreshold.coerceIn(0L, 10000L),
          "supplier" to item.supplier.trim().take(80),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.set(payload).await()
      } else {
        val updates = mapOf(
          "skuName" to item.skuName.trim().take(80),
          "category" to item.category.trim().take(40),
          "quantityOnHand" to item.quantityOnHand.coerceIn(0L, 100000L),
          "unit" to item.unit.trim().take(20),
          "reorderThreshold" to item.reorderThreshold.coerceIn(0L, 10000L),
          "supplier" to item.supplier.trim().take(80),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        docRef.update(updates).await()
      }
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun adjustInventoryStock(skuId: String, newQuantity: Long): Result<Unit> {
    requireUserId()
    val docRef = db.collection("inventory").document(skuId)
    return try {
      docRef
        .update(
          mapOf(
            "quantityOnHand" to newQuantity.coerceIn(0L, 100000L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  // --- ORDERS ---
  fun observeUserOrders(userId: String): Flow<List<Order>> {
    val path = "orders"
    return db.collection(path)
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snap ->
        snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .wrapFirestoreErrors(OperationType.LIST, path, userId)
  }

  fun observeAllOrdersAdmin(): Flow<List<Order>> {
    val path = "orders"
    return db.collection(path)
      .snapshots()
      .map { snap ->
        snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .wrapFirestoreErrors(OperationType.LIST, path)
  }

  suspend fun placeOrder(
    customerName: String,
    address: DeliveryAddress,
    paymentMethod: PaymentMethod,
    cartItems: List<CartItem>,
    tipCents: Long,
  ): Result<String> {
    val uid = requireUserId()
    require(cartItems.isNotEmpty()) { "Cart cannot be empty" }
    val orderRef = db.collection("orders").document()

    val subtotal = cartItems.sumOf { it.menuItem.priceCents * it.quantity }
    val deliveryFee = 299L
    val tax = (subtotal * 0.0875).roundToLong()
    val total = subtotal + deliveryFee + tax + tipCents.coerceAtLeast(0L)

    val paymentLabel = when (paymentMethod.type) {
      "card" -> "${paymentMethod.brand} •••• ${paymentMethod.last4.ifBlank { "0000" }}"
      "wallet" -> paymentMethod.brand
      else -> "Cash on Delivery"
    }
    val summary = cartItems.joinToString(", ") { "${it.quantity}x ${it.menuItem.name}" }.take(500)
    val addressText = listOfNotNull(
      address.street,
      address.apartment?.takeIf { it.isNotBlank() }?.let { "Apt $it" },
      address.city,
    ).joinToString(", ").take(220)

    val lineItemsPayload = cartItems.take(25).map { cart ->
      mapOf(
        "menuItemId" to cart.menuItem.id,
        "name" to cart.menuItem.name,
        "quantity" to cart.quantity.toLong(),
        "priceCents" to cart.menuItem.priceCents,
      )
    }

    val payload = mapOf(
      "userId" to uid,
      "customerName" to customerName.ifBlank { "Customer" }.take(80),
      "restaurantName" to "BiteRoute Kitchen",
      "restaurantLat" to RESTAURANT_LAT,
      "restaurantLng" to RESTAURANT_LNG,
      "deliveryAddressLabel" to address.label.take(30),
      "deliveryAddressText" to addressText,
      "deliveryLat" to address.lat,
      "deliveryLng" to address.lng,
      "courierName" to "Marco V.",
      "courierLat" to RESTAURANT_LAT,
      "courierLng" to RESTAURANT_LNG,
      "etaMinutes" to (cartItems.maxOf { it.menuItem.prepMinutes } + 12L),
      "status" to OrderStatus.PLACED.wireValue,
      "paymentLabel" to paymentLabel.take(60),
      "itemsSummary" to summary,
      "items" to lineItemsPayload,
      "subtotalCents" to subtotal,
      "deliveryFeeCents" to deliveryFee,
      "taxCents" to tax,
      "tipCents" to tipCents.coerceAtLeast(0L),
      "totalCents" to total,
      "hasFeedback" to false,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )

    return try {
      val batch = db.batch()
      batch.set(orderRef, payload)
      // Decrement menu item stock if backed by a persisted Firestore doc
      cartItems.forEach { cart ->
        val itemId = cart.menuItem.id
        if (itemId.isNotBlank() && cart.menuItem.stockCount > 0) {
          val newStock = (cart.menuItem.stockCount - cart.quantity).coerceAtLeast(0L)
          if (newStock < cart.menuItem.stockCount) {
            batch.update(
              db.collection("menu_items").document(itemId),
              mapOf(
                "stockCount" to newStock,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            )
          }
        }
      }
      batch.commit().await()
      Result.success(orderRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, orderRef.path)
      Result.failure(e)
    }
  }

  suspend fun cancelOrder(orderId: String): Result<Unit> {
    requireUserId()
    val docRef = db.collection("orders").document(orderId)
    return try {
      docRef
        .update(
          mapOf(
            "status" to OrderStatus.CANCELLED.wireValue,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminAdvanceOrder(order: Order): Result<Unit> {
    requireUserId()
    val nextStatus = when (order.orderStatus) {
      OrderStatus.PLACED -> OrderStatus.PREPARING
      OrderStatus.PREPARING -> OrderStatus.READY_FOR_PICKUP
      OrderStatus.READY_FOR_PICKUP -> OrderStatus.OUT_FOR_DELIVERY
      OrderStatus.OUT_FOR_DELIVERY -> OrderStatus.DELIVERED
      else -> order.orderStatus
    }
    val progress = when (nextStatus) {
      OrderStatus.PLACED -> 0.0
      OrderStatus.PREPARING -> 0.08
      OrderStatus.READY_FOR_PICKUP -> 0.18
      OrderStatus.OUT_FOR_DELIVERY -> 0.62
      OrderStatus.DELIVERED -> 1.0
      OrderStatus.CANCELLED -> 0.0
    }
    val newLat = order.restaurantLat + (order.deliveryLat - order.restaurantLat) * progress
    val newLng = order.restaurantLng + (order.deliveryLng - order.restaurantLng) * progress
    val newEta = when (nextStatus) {
      OrderStatus.PLACED -> 26L
      OrderStatus.PREPARING -> 19L
      OrderStatus.READY_FOR_PICKUP -> 14L
      OrderStatus.OUT_FOR_DELIVERY -> 7L
      OrderStatus.DELIVERED -> 0L
      OrderStatus.CANCELLED -> 0L
    }
    val docRef = db.collection("orders").document(order.id)
    return try {
      docRef
        .update(
          mapOf(
            "status" to nextStatus.wireValue,
            "courierLat" to newLat,
            "courierLng" to newLng,
            "etaMinutes" to newEta,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun adminUpdateCourierTelemetry(
    order: Order,
    progressFraction: Double,
    etaMinutes: Long,
  ): Result<Unit> {
    requireUserId()
    val p = progressFraction.coerceIn(0.0, 1.0)
    val newLat = order.restaurantLat + (order.deliveryLat - order.restaurantLat) * p
    val newLng = order.restaurantLng + (order.deliveryLng - order.restaurantLng) * p
    val docRef = db.collection("orders").document(order.id)
    return try {
      docRef
        .update(
          mapOf(
            "courierLat" to newLat,
            "courierLng" to newLng,
            "etaMinutes" to etaMinutes.coerceIn(0L, 180L),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      Result.failure(e)
    }
  }

  // --- REVIEWS & FEEDBACK ---
  fun observeUserReviews(userId: String): Flow<List<OrderReview>> {
    val path = "reviews"
    return db.collection(path)
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snap ->
        snap.toObjects(OrderReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .wrapFirestoreErrors(OperationType.LIST, path, userId)
  }

  fun observeAllReviewsAdmin(): Flow<List<OrderReview>> {
    val path = "reviews"
    return db.collection(path)
      .snapshots()
      .map { snap ->
        snap.toObjects(OrderReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .wrapFirestoreErrors(OperationType.LIST, path)
  }

  suspend fun submitOrderFeedback(
    orderId: String,
    customerName: String,
    rating: Int,
    tags: List<String>,
    comment: String,
  ): Result<String> {
    val uid = requireUserId()
    val reviewRef = db.collection("reviews").document()
    val orderRef = db.collection("orders").document(orderId)
    return try {
      val batch = db.batch()
      batch.set(
        reviewRef,
        mapOf(
          "orderId" to orderId,
          "userId" to uid,
          "customerName" to customerName.ifBlank { "Customer" }.take(80),
          "rating" to rating.coerceIn(1, 5).toLong(),
          "tags" to tags.take(6),
          "comment" to comment.trim().take(500),
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        orderRef,
        mapOf(
          "hasFeedback" to true,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(reviewRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, reviewRef.path)
      Result.failure(e)
    }
  }

  // --- ADMIN STARTER CATALOG SEEDING ---
  suspend fun seedStarterCatalogIfEmpty(): Result<Unit> {
    requireUserId()
    return try {
      val existingMenu = db.collection("menu_items").limit(1).get().await()
      val batch = db.batch()
      var hasWrites = false
      if (existingMenu.isEmpty) {
        hasWrites = true
        STARTER_MENU.forEach { item ->
          val ref = db.collection("menu_items").document()
          batch.set(
            ref,
            mapOf(
              "name" to item.name,
              "description" to item.description,
              "category" to item.category,
              "priceCents" to item.priceCents,
              "prepMinutes" to item.prepMinutes,
              "calories" to item.calories,
              "rating" to item.rating,
              "imageKey" to item.imageKey,
              "isAvailable" to item.isAvailable,
              "stockCount" to item.stockCount,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
      val existingInv = db.collection("inventory").limit(1).get().await()
      if (existingInv.isEmpty) {
        hasWrites = true
        STARTER_INVENTORY.forEach { sku ->
          val ref = db.collection("inventory").document()
          batch.set(
            ref,
            mapOf(
              "skuName" to sku.skuName,
              "category" to sku.category,
              "quantityOnHand" to sku.quantityOnHand,
              "unit" to sku.unit,
              "reorderThreshold" to sku.reorderThreshold,
              "supplier" to sku.supplier,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
        }
      }
      if (hasWrites) {
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "menu_items")
      Result.failure(e)
    }
  }

  companion object {
    const val BOOTSTRAP_ADMIN_EMAIL = "kavinbrylo@gmail.com"
    const val RESTAURANT_LAT = 37.7749
    const val RESTAURANT_LNG = -122.4194

    val STARTER_MENU = listOf(
      MenuItem(
        id = "preview_1",
        name = "Miso Salmon Harvest Bowl",
        description = "Caramelized miso-glazed Atlantic salmon, avocado, edamame, pickled watermelon radish & forbidden grains.",
        category = "Bowls",
        priceCents = 1850L,
        prepMinutes = 14L,
        calories = 640L,
        rating = 4.9,
        imageKey = "bowl",
        isAvailable = true,
        stockCount = 28L,
      ),
      MenuItem(
        id = "preview_2",
        name = "Double Wagyu Smash Burger",
        description = "Two seared wagyu patties, aged Tillamook cheddar, charred onion jam, house sauce on toasted brioche with herb fries.",
        category = "Burgers",
        priceCents = 1795L,
        prepMinutes = 16L,
        calories = 890L,
        rating = 4.8,
        imageKey = "burger",
        isAvailable = true,
        stockCount = 22L,
      ),
      MenuItem(
        id = "preview_3",
        name = "Burrata Wood-Fired Margherita",
        description = "72-hour sourdough blistered crust, San Marzano tomato sauce, creamy Pugliese burrata & sweet garden basil.",
        category = "Pizza",
        priceCents = 2100L,
        prepMinutes = 18L,
        calories = 780L,
        rating = 4.9,
        imageKey = "pizza",
        isAvailable = true,
        stockCount = 19L,
      ),
      MenuItem(
        id = "preview_4",
        name = "Spicy Yuzu Tuna Crunch Bowl",
        description = "Sushi-grade ahi tuna, crispy shallots, yuzu kosho dressing, cucumber ribbons & warm sushi rice.",
        category = "Bowls",
        priceCents = 1950L,
        prepMinutes = 12L,
        calories = 590L,
        rating = 4.7,
        imageKey = "bowl",
        isAvailable = true,
        stockCount = 16L,
      ),
      MenuItem(
        id = "preview_5",
        name = "Truffle Mushroom Sourdough Pie",
        description = "Roasted cremini & maitake mushrooms, fontina crema, thyme, garlic confit & black truffle drizzle.",
        category = "Pizza",
        priceCents = 2250L,
        prepMinutes = 19L,
        calories = 820L,
        rating = 4.8,
        imageKey = "pizza",
        isAvailable = true,
        stockCount = 14L,
      ),
      MenuItem(
        id = "preview_6",
        name = "Crispy Hot Honey Chicken Brioche",
        description = "Buttermilk-brined chicken breast, Calabrian chili honey, dill pickles & fennel slaw.",
        category = "Burgers",
        priceCents = 1550L,
        prepMinutes = 15L,
        calories = 740L,
        rating = 4.7,
        imageKey = "burger",
        isAvailable = true,
        stockCount = 25L,
      ),
    )

    val STARTER_INVENTORY = listOf(
      InventoryItem(
        skuName = "Atlantic Salmon Fillets",
        category = "Seafood",
        quantityOnHand = 42L,
        unit = "portions",
        reorderThreshold = 15L,
        supplier = "Bay Fresh Fisheries",
      ),
      InventoryItem(
        skuName = "72h Sourdough Dough Balls",
        category = "Bakery",
        quantityOnHand = 12L,
        unit = "balls",
        reorderThreshold = 18L,
        supplier = "Tartine Artisan Mill",
      ),
      InventoryItem(
        skuName = "Wagyu Chuck Smash Patties",
        category = "Butchery",
        quantityOnHand = 56L,
        unit = "patties",
        reorderThreshold = 20L,
        supplier = "Marin Heritage Ranch",
      ),
      InventoryItem(
        skuName = "Pugliese Burrata Wheels",
        category = "Dairy",
        quantityOnHand = 9L,
        unit = "wheels",
        reorderThreshold = 12L,
        supplier = "Bellwether Creamery",
      ),
      InventoryItem(
        skuName = "Compostable Kraft Bowls & Lids",
        category = "Packaging",
        quantityOnHand = 240L,
        unit = "sets",
        reorderThreshold = 60L,
        supplier = "EcoPack Supply",
      ),
    )
  }
}
