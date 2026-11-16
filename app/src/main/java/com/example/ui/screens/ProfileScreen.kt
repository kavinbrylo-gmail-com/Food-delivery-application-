package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DeliveryAddress
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.data.model.UserProfile
import com.example.ui.UiState
import com.example.ui.components.StatusPill
import com.example.util.GpsFix
import com.example.util.formatCents
import com.example.util.formatTimestamp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
  displayName: String,
  email: String,
  isAdmin: Boolean,
  profileState: UiState<UserProfile?>,
  addressesState: UiState<List<DeliveryAddress>>,
  paymentsState: UiState<List<PaymentMethod>>,
  ordersState: UiState<List<Order>>,
  liveGpsFix: GpsFix?,
  onDetectGps: () -> Unit,
  onSaveAddress: (DeliveryAddress) -> Unit,
  onDeleteAddress: (String) -> Unit,
  onSavePayment: (PaymentMethod) -> Unit,
  onDeletePayment: (String) -> Unit,
  onUpdateAlertPrefs: (Boolean, Boolean, Boolean) -> Unit,
  onReorder: (Order) -> Unit,
  onSubmitFeedback: (String, Int, List<String>, String, () -> Unit) -> Unit,
  onSignOut: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var showAddAddress by remember { mutableStateOf(false) }
  var showAddPayment by remember { mutableStateOf(false) }
  var feedbackOrder by remember { mutableStateOf<Order?>(null) }

  val profile = (profileState as? UiState.Success)?.data
  val addresses = (addressesState as? UiState.Success)?.data ?: emptyList()
  val payments = (paymentsState as? UiState.Success)?.data ?: emptyList()
  val orders = (ordersState as? UiState.Success)?.data ?: emptyList()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // User Identity Header
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(22.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(54.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = displayName.take(1).uppercase().ifBlank { "B" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
          Spacer(Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(displayName, style = MaterialTheme.typography.titleMedium)
            Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Surface(
              color = if (isAdmin) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = RoundedCornerShape(50),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
              ) {
                if (isAdmin) {
                  Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(13.dp),
                  )
                  Spacer(Modifier.width(4.dp))
                }
                Text(
                  text = if (isAdmin) "Restaurant Manager" else "Verified Customer",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isAdmin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
          IconButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("sign_out_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Sign out")
          }
        }
      }
    }

    // Sub-tabs
    item {
      SecondaryTabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("History") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Addresses") })
        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Payments") })
        Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Alerts") })
      }
    }

    when (selectedTab) {
      0 -> {
        if (orders.isEmpty()) {
          item {
            EmptyProfileCard(
              title = "No past orders yet",
              subtitle = "Your completed receipts and reorder shortcuts will appear here.",
            )
          }
        } else {
          items(orders, key = { it.id }) { order ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              shape = RoundedCornerShape(18.dp),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column {
                    Text("Order #${order.id.takeLast(5).uppercase()}", style = MaterialTheme.typography.titleSmall)
                    Text(formatTimestamp(order.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  StatusPill(order.orderStatus)
                }
                Text(order.itemsSummary, style = MaterialTheme.typography.bodyMedium)
                Text(
                  text = "${formatCents(order.totalCents)} • ${order.paymentLabel}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = { onReorder(order) },
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reorder")
                  }
                  if (order.orderStatus == OrderStatus.DELIVERED && !order.hasFeedback) {
                    Button(
                      onClick = { feedbackOrder = order },
                      modifier = Modifier.weight(1f),
                    ) {
                      Text("Rate Meal")
                    }
                  }
                }
              }
            }
          }
        }
      }

      1 -> {
        item {
          Button(
            onClick = { showAddAddress = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("add_address_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add Delivery Address")
          }
        }
        if (addresses.isEmpty()) {
          item {
            EmptyProfileCard(
              title = "No saved addresses",
              subtitle = "Add your home or office coordinates for 1-tap GPS checkout.",
            )
          }
        } else {
          items(addresses, key = { it.id }) { addr ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              shape = RoundedCornerShape(16.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
              ) {
                Icon(Icons.Rounded.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(addr.label, style = MaterialTheme.typography.titleSmall)
                    if (addr.isDefault) {
                      Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                        Text(
                          "DEFAULT",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.secondary,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                      }
                    }
                  }
                  Text("${addr.street}, ${addr.city}", style = MaterialTheme.typography.bodySmall)
                  Text(
                    String.format(Locale.US, "GPS %.4f, %.4f", addr.lat, addr.lng),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { onDeleteAddress(addr.id) }) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete address")
                }
              }
            }
          }
        }
      }

      2 -> {
        item {
          Button(
            onClick = { showAddPayment = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("add_payment_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add Payment Method")
          }
        }
        if (payments.isEmpty()) {
          item {
            EmptyProfileCard(
              title = "No saved payment methods",
              subtitle = "Add a card or digital wallet for fast multi-method checkout.",
            )
          }
        } else {
          items(payments, key = { it.id }) { pm ->
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              shape = RoundedCornerShape(16.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
              ) {
                Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                      text = if (pm.type == "card") "${pm.brand} •••• ${pm.last4}" else pm.brand,
                      style = MaterialTheme.typography.titleSmall,
                    )
                    if (pm.isDefault) {
                      Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                        Text(
                          "DEFAULT",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.secondary,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                      }
                    }
                  }
                  Text(
                    text = "${pm.cardholderName}${pm.expiry?.let { " • Exp $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                IconButton(onClick = { onDeletePayment(pm.id) }) {
                  Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove payment method")
                }
              }
            }
          }
        }
      }

      3 -> {
        item {
          val statusAlerts = profile?.orderStatusAlerts ?: true
          val arrivalAlerts = profile?.courierArrivalAlerts ?: true
          val promoAlerts = profile?.promoAlerts ?: false

          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
              Text("Automated Push Notifications", style = MaterialTheme.typography.titleMedium)
              AlertToggleRow(
                title = "Order Status Updates",
                subtitle = "Kitchen prep, pickup, and completed delivery notifications.",
                checked = statusAlerts,
                onCheckedChange = { onUpdateAlertPrefs(it, arrivalAlerts, promoAlerts) },
              )
              AlertToggleRow(
                title = "Courier Arrival Alerts",
                subtitle = "GPS proximity notification when your courier is minutes away.",
                checked = arrivalAlerts,
                onCheckedChange = { onUpdateAlertPrefs(statusAlerts, it, promoAlerts) },
              )
              AlertToggleRow(
                title = "Chef's Seasonal Drops",
                subtitle = "Weekly menu specials and limited-stock dishes.",
                checked = promoAlerts,
                onCheckedChange = { onUpdateAlertPrefs(statusAlerts, arrivalAlerts, it) },
              )
            }
          }
        }
      }
    }
  }

  if (showAddAddress) {
    AddAddressDialog(
      liveGpsFix = liveGpsFix,
      onDetectGps = onDetectGps,
      onDismiss = { showAddAddress = false },
      onSave = { addr ->
        onSaveAddress(addr)
        showAddAddress = false
      },
    )
  }

  if (showAddPayment) {
    AddPaymentDialog(
      defaultName = displayName,
      onDismiss = { showAddPayment = false },
      onSave = { pm ->
        onSavePayment(pm)
        showAddPayment = false
      },
    )
  }

  feedbackOrder?.let { targetOrder ->
    FeedbackDialog(
      order = targetOrder,
      onDismiss = { feedbackOrder = null },
      onSubmit = { rating, tags, comment ->
        onSubmitFeedback(targetOrder.id, rating, tags, comment) {
          feedbackOrder = null
        }
      },
    )
  }
}

@Composable
private fun AlertToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
private fun EmptyProfileCard(title: String, subtitle: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun AddAddressDialog(
  liveGpsFix: GpsFix?,
  onDetectGps: () -> Unit,
  onDismiss: () -> Unit,
  onSave: (DeliveryAddress) -> Unit,
) {
  var label by remember { mutableStateOf("Home") }
  var street by remember(liveGpsFix) { mutableStateOf(liveGpsFix?.streetHint ?: "") }
  var apartment by remember { mutableStateOf("") }
  var city by remember(liveGpsFix) { mutableStateOf(liveGpsFix?.cityHint ?: "San Francisco, CA") }
  var instructions by remember { mutableStateOf("Leave at front door") }
  var isDefault by remember { mutableStateOf(true) }

  val lat = liveGpsFix?.lat ?: 37.7812
  val lng = liveGpsFix?.lng ?: -122.4112

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Save Delivery Address") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onDetectGps, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            if (liveGpsFix != null) {
              String.format(Locale.US, "GPS Locked: %.4f, %.4f", lat, lng)
            } else {
              "Use Current GPS Location"
            }
          )
        }
        OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (Home, Work)") }, singleLine = true)
        OutlinedTextField(value = street, onValueChange = { street = it }, label = { Text("Street Address") }, singleLine = true)
        OutlinedTextField(value = apartment, onValueChange = { apartment = it }, label = { Text("Apt / Suite (Optional)") }, singleLine = true)
        OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City") }, singleLine = true)
        OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text("Drop-off Notes") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(checked = isDefault, onCheckedChange = { isDefault = it })
          Spacer(Modifier.width(8.dp))
          Text("Set as default address", style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (street.trim().length >= 2 && city.trim().isNotEmpty()) {
            onSave(
              DeliveryAddress(
                label = label.ifBlank { "Home" },
                street = street.trim(),
                apartment = apartment.takeIf { it.isNotBlank() },
                city = city.trim(),
                instructions = instructions.takeIf { it.isNotBlank() },
                lat = lat,
                lng = lng,
                isDefault = isDefault,
              )
            )
          }
        },
        enabled = street.trim().length >= 2 && city.trim().isNotEmpty(),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun AddPaymentDialog(
  defaultName: String,
  onDismiss: () -> Unit,
  onSave: (PaymentMethod) -> Unit,
) {
  var type by remember { mutableStateOf("card") }
  var brand by remember { mutableStateOf("Visa") }
  var last4 by remember { mutableStateOf("") }
  var expiry by remember { mutableStateOf("09/29") }
  var holder by remember { mutableStateOf(defaultName) }
  var isDefault by remember { mutableStateOf(true) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Payment Method") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("card" to "Card", "wallet" to "Google Pay", "cash" to "Cash").forEach { (key, label) ->
            FilterChip(
              selected = type == key,
              onClick = {
                type = key
                brand = when (key) {
                  "wallet" -> "Google Pay"
                  "cash" -> "Cash"
                  else -> "Visa"
                }
              },
              label = { Text(label) },
            )
          }
        }
        if (type == "card") {
          OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("Card Network (Visa, Mastercard)") }, singleLine = true)
          OutlinedTextField(
            value = last4,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) last4 = it },
            label = { Text("Last 4 Digits") },
            singleLine = true,
          )
          OutlinedTextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Expiry (MM/YY)") }, singleLine = true)
        }
        OutlinedTextField(value = holder, onValueChange = { holder = it }, label = { Text("Cardholder / Account Name") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Switch(checked = isDefault, onCheckedChange = { isDefault = it })
          Spacer(Modifier.width(8.dp))
          Text("Set as default payment", style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      val valid = holder.isNotBlank() && (type != "card" || last4.length == 4)
      Button(
        onClick = {
          onSave(
            PaymentMethod(
              type = type,
              brand = brand.ifBlank { "Card" },
              last4 = if (type == "card") last4 else "",
              expiry = if (type == "card") expiry.takeIf { it.isNotBlank() } else null,
              cardholderName = holder.trim(),
              isDefault = isDefault,
            )
          )
        },
        enabled = valid,
      ) {
        Text("Save Method")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
