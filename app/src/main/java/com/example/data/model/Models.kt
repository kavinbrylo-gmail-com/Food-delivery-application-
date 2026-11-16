package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val displayName: String = "",
  val email: String = "",
  val photoUrl: String? = null,
  val orderStatusAlerts: Boolean = true,
  val courierArrivalAlerts: Boolean = true,
  val promoAlerts: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class DeliveryAddress(
  @DocumentId val id: String = "",
  val label: String = "Home",
  val street: String = "",
  val apartment: String? = null,
  val city: String = "",
  val instructions: String? = null,
  val lat: Double = 37.7812,
  val lng: Double = -122.4112,
  val isDefault: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PaymentMethod(
  @DocumentId val id: String = "",
  val type: String = "card", // "card", "wallet", "cash"
  val brand: String = "Visa",
  val last4: String = "",
  val expiry: String? = null,
  val cardholderName: String = "",
  val isDefault: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MenuItem(
  @DocumentId val id: String = "",
  val name: String = "",
  val description: String = "",
  val category: String = "Bowls",
  val priceCents: Long = 0L,
  val prepMinutes: Long = 15L,
  val calories: Long = 500L,
  val rating: Double = 4.8,
  val imageKey: String = "bowl",
  val isAvailable: Boolean = true,
  val stockCount: Long = 20L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class InventoryItem(
  @DocumentId val id: String = "",
  val skuName: String = "",
  val category: String = "Produce",
  val quantityOnHand: Long = 0L,
  val unit: String = "units",
  val reorderThreshold: Long = 10L,
  val supplier: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class OrderLineItem(
  val menuItemId: String = "",
  val name: String = "",
  val quantity: Long = 1L,
  val priceCents: Long = 0L,
)

enum class OrderStatus(val wireValue: String, val displayLabel: String) {
  PLACED("placed", "Order Placed"),
  PREPARING("preparing", "Kitchen Preparing"),
  READY_FOR_PICKUP("ready_for_pickup", "Ready for Courier"),
  OUT_FOR_DELIVERY("out_for_delivery", "Out for Delivery"),
  DELIVERED("delivered", "Delivered"),
  CANCELLED("cancelled", "Cancelled");

  companion object {
    fun fromWire(value: String): OrderStatus =
      entries.firstOrNull { it.wireValue == value } ?: PLACED
  }
}

@IgnoreExtraProperties
data class Order(
  @DocumentId val id: String = "",
  val userId: String = "",
  val customerName: String = "",
  val restaurantName: String = "BiteRoute Kitchen",
  val restaurantLat: Double = 37.7749,
  val restaurantLng: Double = -122.4194,
  val deliveryAddressLabel: String = "Home",
  val deliveryAddressText: String = "",
  val deliveryLat: Double = 37.7812,
  val deliveryLng: Double = -122.4112,
  val courierName: String = "Marco V.",
  val courierLat: Double = 37.7749,
  val courierLng: Double = -122.4194,
  val etaMinutes: Long = 25L,
  val status: String = OrderStatus.PLACED.wireValue,
  val paymentLabel: String = "Visa •••• 4242",
  val itemsSummary: String = "",
  val items: List<OrderLineItem> = emptyList(),
  val subtotalCents: Long = 0L,
  val deliveryFeeCents: Long = 299L,
  val taxCents: Long = 0L,
  val tipCents: Long = 0L,
  val totalCents: Long = 0L,
  val hasFeedback: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  val orderStatus: OrderStatus
    get() = OrderStatus.fromWire(status)
}

@IgnoreExtraProperties
data class OrderReview(
  @DocumentId val id: String = "",
  val orderId: String = "",
  val userId: String = "",
  val customerName: String = "",
  val rating: Long = 5L,
  val tags: List<String> = emptyList(),
  val comment: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

data class CartItem(
  val menuItem: MenuItem,
  val quantity: Int,
)

data class DeliveryAlert(
  val id: String,
  val orderId: String,
  val title: String,
  val message: String,
  val status: OrderStatus,
  val timestampMillis: Long,
)
