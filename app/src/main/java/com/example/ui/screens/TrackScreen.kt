package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.data.model.DeliveryAlert
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.ui.UiState
import com.example.ui.components.LiveGpsRouteMapCanvas
import com.example.ui.components.StatusPill
import com.example.util.formatCents
import com.example.util.formatTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackScreen(
  ordersState: UiState<List<Order>>,
  alerts: List<DeliveryAlert>,
  hasNotificationPermission: Boolean,
  onRequestNotificationPermission: () -> Unit,
  onCancelOrder: (String) -> Unit,
  onSubmitFeedback: (String, Int, List<String>, String, () -> Unit) -> Unit,
  onBrowseMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var feedbackOrder by remember { mutableStateOf<Order?>(null) }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Text("Live Order Tracking", style = MaterialTheme.typography.headlineMedium)
      Text(
        text = "Real-time GPS courier position and automated delivery alerts.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (!hasNotificationPermission) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
          shape = RoundedCornerShape(16.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
          ) {
            Icon(
              Icons.Rounded.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Enable Delivery Push Alerts", style = MaterialTheme.typography.titleSmall)
              Text(
                "Receive real-time kitchen & courier arrival alerts.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            TextButton(onClick = onRequestNotificationPermission) {
              Text("Allow")
            }
          }
        }
      }
    }

    if (alerts.isNotEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(16.dp),
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent Delivery Alerts", style = MaterialTheme.typography.titleSmall)
            alerts.take(3).forEach { alert ->
              val timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(alert.timestampMillis))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(alert.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                  Text(alert.message, style = MaterialTheme.typography.bodySmall)
                }
                Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }

    when (ordersState) {
      is UiState.Loading -> {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
      }
      is UiState.Error -> {
        item {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
              text = ordersState.message,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }
      is UiState.Success -> {
        val activeOrders = ordersState.data.filter {
          it.orderStatus != OrderStatus.CANCELLED &&
            (it.orderStatus != OrderStatus.DELIVERED || !it.hasFeedback)
        }
        if (activeOrders.isEmpty()) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              shape = RoundedCornerShape(20.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = CircleShape,
                  modifier = Modifier.size(60.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      Icons.Rounded.DeliveryDining,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(32.dp),
                    )
                  }
                }
                Text("No active deliveries", style = MaterialTheme.typography.titleMedium)
                Text(
                  "Place an order from Discover to watch live courier GPS routing and step-by-step kitchen progress.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onBrowseMenu, modifier = Modifier.padding(top = 4.dp)) {
                  Text("Browse Menu")
                }
              }
            }
          }
        } else {
          items(activeOrders, key = { it.id }) { order ->
            ActiveOrderTrackingCard(
              order = order,
              onCancel = { onCancelOrder(order.id) },
              onRate = { feedbackOrder = order },
            )
          }
        }
      }
    }
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
private fun ActiveOrderTrackingCard(
  order: Order,
  onCancel: () -> Unit,
  onRate: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(22.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("tracking_order_card_${order.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Order #${order.id.takeLast(5).uppercase()}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = formatTimestamp(order.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        StatusPill(order.orderStatus)
      }

      LiveGpsRouteMapCanvas(order = order)

      OrderProgressStepper(currentStatus = order.orderStatus)

      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Courier: ${order.courierName}", style = MaterialTheme.typography.titleSmall)
          Text("Drop-off: ${order.deliveryAddressLabel} — ${order.deliveryAddressText}", style = MaterialTheme.typography.bodySmall)
          Text("Items: ${order.itemsSummary}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(
            text = "Paid ${formatCents(order.totalCents)} via ${order.paymentLabel}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      if (order.orderStatus == OrderStatus.PLACED) {
        OutlinedButton(
          onClick = onCancel,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Cancel Order")
        }
      } else if (order.orderStatus == OrderStatus.DELIVERED && !order.hasFeedback) {
        Button(
          onClick = onRate,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("rate_order_button_${order.id}"),
        ) {
          Icon(Icons.Rounded.RateReview, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Rate & Share Feedback")
        }
      }
    }
  }
}

@Composable
private fun OrderProgressStepper(currentStatus: OrderStatus) {
  val steps = listOf(
    OrderStatus.PLACED to "Placed",
    OrderStatus.PREPARING to "Kitchen",
    OrderStatus.READY_FOR_PICKUP to "Pickup",
    OrderStatus.OUT_FOR_DELIVERY to "En Route",
    OrderStatus.DELIVERED to "Arrived",
  )
  val activeIndex = steps.indexOfFirst { it.first == currentStatus }.coerceAtLeast(0)

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    steps.forEachIndexed { idx, (_, label) ->
      val done = idx <= activeIndex
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
        Surface(
          color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = CircleShape,
          modifier = Modifier.size(28.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            if (done) {
              Icon(
                Icons.Rounded.Check,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
              )
            } else {
              Text(
                text = (idx + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        Spacer(Modifier.height(4.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackDialog(
  order: Order,
  onDismiss: () -> Unit,
  onSubmit: (Int, List<String>, String) -> Unit,
) {
  var rating by remember { mutableIntStateOf(5) }
  var selectedTags by remember { mutableStateOf(setOf("Hot & Fresh")) }
  var comment by remember { mutableStateOf("") }
  val availableTags = listOf("Hot & Fresh", "Fast Delivery", "Great Packaging", "Friendly Courier", "Accurate Order")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Rate Order #${order.id.takeLast(5).uppercase()}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
        ) {
          (1..5).forEach { star ->
            IconButton(onClick = { rating = star }) {
              Icon(
                imageVector = if (star <= rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "Rate $star stars",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp),
              )
            }
          }
        }
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          availableTags.forEach { tag ->
            val selected = tag in selectedTags
            FilterChip(
              selected = selected,
              onClick = {
                selectedTags = if (selected) selectedTags - tag else selectedTags + tag
              },
              label = { Text(tag) },
            )
          }
        }
        OutlinedTextField(
          value = comment,
          onValueChange = { comment = it },
          label = { Text("Comments (optional)") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmit(rating, selectedTags.toList(), comment) },
        modifier = Modifier.testTag("submit_feedback_button"),
      ) {
        Text("Submit Review")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
