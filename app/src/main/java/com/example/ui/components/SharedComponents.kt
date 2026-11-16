package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CartItem
import com.example.data.model.DeliveryAddress
import com.example.data.model.Order
import com.example.data.model.OrderStatus
import com.example.data.model.PaymentMethod
import com.example.util.formatCents
import com.example.util.haversineMiles
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun StatusPill(status: OrderStatus, modifier: Modifier = Modifier) {
  val (bg, fg) = when (status) {
    OrderStatus.PLACED -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    OrderStatus.PREPARING -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    OrderStatus.READY_FOR_PICKUP -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    OrderStatus.OUT_FOR_DELIVERY -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    OrderStatus.DELIVERED -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
    OrderStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
  }
  Surface(
    color = bg,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Text(
      text = status.displayLabel,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = fg,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun LiveGpsRouteMapCanvas(
  order: Order,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "courier_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 2.1f,
    animationSpec = infiniteRepeatable(
      animation = tween(1400),
      repeatMode = RepeatMode.Restart,
    ),
    label = "pulse_radius",
  )

  val routeProgress = remember(order) {
    val dLat = order.deliveryLat - order.restaurantLat
    val dLng = order.deliveryLng - order.restaurantLng
    val denom = dLat * dLat + dLng * dLng
    if (denom < 1e-10) {
      when (order.orderStatus) {
        OrderStatus.DELIVERED -> 1f
        OrderStatus.OUT_FOR_DELIVERY -> 0.65f
        OrderStatus.READY_FOR_PICKUP -> 0.2f
        else -> 0.05f
      }
    } else {
      val proj =
        ((order.courierLat - order.restaurantLat) * dLat +
          (order.courierLng - order.restaurantLng) * dLng) / denom
      proj.toFloat().coerceIn(0f, 1f)
    }
  }

  val remainingMiles = remember(order) {
    haversineMiles(order.courierLat, order.courierLng, order.deliveryLat, order.deliveryLng)
  }

  val mapBg = MaterialTheme.colorScheme.surfaceContainerHigh
  val roadColor = MaterialTheme.colorScheme.surfaceContainerLow
  val parkColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
  val routeActiveColor = MaterialTheme.colorScheme.primary
  val routePendingColor = MaterialTheme.colorScheme.outline
  val destColor = MaterialTheme.colorScheme.secondary

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(210.dp)
      .clip(RoundedCornerShape(20.dp))
      .background(mapBg),
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val w = size.width
      val h = size.height

      // Urban blocks & parks
      drawRoundRect(
        color = parkColor,
        topLeft = Offset(w * 0.08f, h * 0.14f),
        size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.28f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
      )
      drawRoundRect(
        color = parkColor,
        topLeft = Offset(w * 0.68f, h * 0.56f),
        size = androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.26f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
      )

      // City street grid lines
      for (i in 1..4) {
        val y = h * (i / 5f)
        drawLine(
          color = roadColor,
          start = Offset(0f, y),
          end = Offset(w, y),
          strokeWidth = 6f,
        )
      }
      for (j in 1..5) {
        val x = w * (j / 6f)
        drawLine(
          color = roadColor,
          start = Offset(x, 0f),
          end = Offset(x, h),
          strokeWidth = 6f,
        )
      }

      val start = Offset(w * 0.16f, h * 0.72f)
      val ctrl = Offset(w * 0.48f, h * 0.20f)
      val end = Offset(w * 0.84f, h * 0.34f)

      // Full pending route (dashed)
      val fullPath = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(ctrl.x, ctrl.y, end.x, end.y)
      }
      drawPath(
        path = fullPath,
        color = routePendingColor,
        style = Stroke(
          width = 7f,
          cap = StrokeCap.Round,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f),
        ),
      )

      // Evaluate point on quadratic Bezier at t = routeProgress
      val t = routeProgress
      val invT = 1f - t
      val courierPos = Offset(
        x = invT * invT * start.x + 2f * invT * t * ctrl.x + t * t * end.x,
        y = invT * invT * start.y + 2f * invT * t * ctrl.y + t * t * end.y,
      )

      // Completed route segment approximation via segmented curve
      if (t > 0.01f) {
        val activePath = Path().apply {
          moveTo(start.x, start.y)
          val steps = 24
          for (s in 1..steps) {
            val st = (s / steps.toFloat()) * t
            val sInv = 1f - st
            lineTo(
              sInv * sInv * start.x + 2f * sInv * st * ctrl.x + st * st * end.x,
              sInv * sInv * start.y + 2f * sInv * st * ctrl.y + st * st * end.y,
            )
          }
        }
        drawPath(
          path = activePath,
          color = routeActiveColor,
          style = Stroke(width = 9f, cap = StrokeCap.Round),
        )
      }

      // Kitchen origin marker
      drawCircle(color = Color.White, radius = 16f, center = start)
      drawCircle(color = Color(0xFF1C1917), radius = 10f, center = start)

      // Customer destination pin
      drawCircle(color = destColor.copy(alpha = 0.25f), radius = 24f, center = end)
      drawCircle(color = Color.White, radius = 14f, center = end)
      drawCircle(color = destColor, radius = 9f, center = end)

      // Animated live courier GPS beacon
      if (order.orderStatus != OrderStatus.CANCELLED) {
        drawCircle(
          color = routeActiveColor.copy(alpha = (2.1f - pulseScale).coerceIn(0f, 0.45f)),
          radius = 18f * pulseScale,
          center = courierPos,
        )
        drawCircle(color = Color.White, radius = 17f, center = courierPos)
        drawCircle(color = routeActiveColor, radius = 12f, center = courierPos)
      }
    }

    // Top telemetry HUD pill
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
      shape = RoundedCornerShape(12.dp),
      shadowElevation = 2.dp,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.MyLocation,
          contentDescription = "GPS Active",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = String.format(
            Locale.US,
            "%.4f, %.4f • %.2f mi",
            order.courierLat,
            order.courierLng,
            remainingMiles,
          ),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }

    // Bottom legend
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(10.dp),
    ) {
      Text(
        text = if (order.orderStatus == OrderStatus.DELIVERED) "Arrived" else "ETA ${order.etaMinutes} min",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      )
    }
  }
}

@Composable
fun CartSummaryBar(
  count: Int,
  subtotalCents: Long,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    shape = RoundedCornerShape(20.dp),
    shadowElevation = 6.dp,
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("cart_checkout_bar"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
          color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
          shape = CircleShape,
          modifier = Modifier.size(32.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = count.toString(),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }
        Column {
          Text("View Bag & Checkout", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Text("Tap to select address & payment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = formatCents(subtotalCents),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        Icon(Icons.Rounded.ShoppingBag, contentDescription = null, modifier = Modifier.size(20.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutModalSheet(
  cartItems: List<CartItem>,
  addresses: List<DeliveryAddress>,
  paymentMethods: List<PaymentMethod>,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onAddQuickAddressWithGps: () -> Unit,
  onOpenProfilePayments: () -> Unit,
  onConfirmOrder: (DeliveryAddress, PaymentMethod, Long) -> Unit,
) {
  val subtotal = cartItems.sumOf { it.menuItem.priceCents * it.quantity }
  val deliveryFee = 299L
  val tax = (subtotal * 0.0875).roundToLong()

  var tipPercent by remember { mutableIntStateOf(15) }
  val tipCents = (subtotal * (tipPercent / 100.0)).roundToLong()
  val total = subtotal + deliveryFee + tax + tipCents

  var selectedAddressId by remember(addresses) {
    mutableStateOf(addresses.firstOrNull { it.isDefault }?.id ?: addresses.firstOrNull()?.id)
  }
  var selectedPaymentId by remember(paymentMethods) {
    mutableStateOf(paymentMethods.firstOrNull { it.isDefault }?.id ?: paymentMethods.firstOrNull()?.id)
  }

  val fallbackCash = remember {
    PaymentMethod(id = "cash_fallback", type = "cash", brand = "Cash", last4 = "", cardholderName = "Customer", isDefault = false)
  }

  val activeAddress = addresses.firstOrNull { it.id == selectedAddressId }
  val activePayment = paymentMethods.firstOrNull { it.id == selectedPaymentId } ?: fallbackCash

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Secure Checkout", style = MaterialTheme.typography.headlineSmall)

      // Items summary
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          cartItems.forEach { cart ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(
                text = "${cart.quantity}x ${cart.menuItem.name}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = formatCents(cart.menuItem.priceCents * cart.quantity),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }

      // Delivery Address Selection
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Delivery Address", style = MaterialTheme.typography.titleMedium)
          TextButton(onClick = onAddQuickAddressWithGps) {
            Icon(Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Use GPS")
          }
        }
        if (addresses.isEmpty()) {
          OutlinedButton(
            onClick = onAddQuickAddressWithGps,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Rounded.LocationOn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Delivery Address via GPS")
          }
        } else {
          addresses.forEach { addr ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = if (addr.id == activeAddress?.id) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
              } else {
                MaterialTheme.colorScheme.surfaceContainerLow
              },
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedAddressId = addr.id },
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(12.dp),
              ) {
                RadioButton(selected = addr.id == activeAddress?.id, onClick = { selectedAddressId = addr.id })
                Column(modifier = Modifier.weight(1f)) {
                  Text(addr.label, style = MaterialTheme.typography.titleSmall)
                  Text("${addr.street}, ${addr.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      }

      // Payment Method Selection
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Payment Method", style = MaterialTheme.typography.titleMedium)
          TextButton(onClick = onOpenProfilePayments) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Manage")
          }
        }
        val choices = if (paymentMethods.isEmpty()) listOf(fallbackCash) else paymentMethods + fallbackCash
        choices.forEach { pm ->
          val label = when (pm.type) {
            "card" -> "${pm.brand} •••• ${pm.last4} (${pm.expiry ?: "Saved"})"
            "wallet" -> "${pm.brand} Digital Wallet"
            else -> "Cash on Delivery"
          }
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (pm.id == activePayment.id) {
              MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            } else {
              MaterialTheme.colorScheme.surfaceContainerLow
            },
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedPaymentId = pm.id },
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(12.dp),
            ) {
              RadioButton(selected = pm.id == activePayment.id, onClick = { selectedPaymentId = pm.id })
              Icon(Icons.Rounded.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
          }
        }
      }

      // Courier Tip Selector
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Courier Tip", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0, 10, 15, 20).forEach { pct ->
            FilterChip(
              selected = tipPercent == pct,
              onClick = { tipPercent = pct },
              label = { Text(if (pct == 0) "None" else "$pct%") },
            )
          }
        }
      }

      HorizontalDivider()

      // Price Breakdown
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PriceRow("Subtotal", formatCents(subtotal))
        PriceRow("Delivery Fee", formatCents(deliveryFee))
        PriceRow("Tax (8.75%)", formatCents(tax))
        PriceRow("Courier Tip ($tipPercent%)", formatCents(tipCents))
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Total", style = MaterialTheme.typography.titleLarge)
          Text(
            text = formatCents(total),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Button(
        onClick = {
          if (activeAddress != null) {
            onConfirmOrder(activeAddress, activePayment, tipCents)
          }
        },
        enabled = !isBusy && activeAddress != null,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("confirm_place_order_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(
            text = if (activeAddress == null) "Select a Delivery Address" else "Place Order • ${formatCents(total)}",
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun PriceRow(label: String, value: String) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
  }
}
