package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.InventoryItem
import com.example.data.model.MenuItem
import com.example.data.model.Order
import com.example.data.model.OrderReview
import com.example.data.model.OrderStatus
import com.example.ui.UiState
import com.example.ui.components.StatusPill
import com.example.util.formatCents
import com.example.util.formatTimestamp
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerDashboardScreen(
  ordersState: UiState<List<Order>>,
  menuState: UiState<List<MenuItem>>,
  inventoryState: UiState<List<InventoryItem>>,
  reviewsState: UiState<List<OrderReview>>,
  onAdvanceOrder: (Order) -> Unit,
  onUpdateCourierTelemetry: (Order, Double, Long) -> Unit,
  onSaveMenuItem: (MenuItem) -> Unit,
  onDeleteMenuItem: (String) -> Unit,
  onSaveInventoryItem: (InventoryItem) -> Unit,
  onAdjustInventoryStock: (String, Long) -> Unit,
  onSeedCatalog: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var editingMenuItem by remember { mutableStateOf<MenuItem?>(null) }
  var showMenuDialog by remember { mutableStateOf(false) }
  var showInventoryDialog by remember { mutableStateOf(false) }

  val orders = (ordersState as? UiState.Success)?.data ?: emptyList()
  val menuItems = (menuState as? UiState.Success)?.data ?: emptyList()
  val inventory = (inventoryState as? UiState.Success)?.data ?: emptyList()
  val reviews = (reviewsState as? UiState.Success)?.data ?: emptyList()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("Manager Console", style = MaterialTheme.typography.headlineMedium)
          Text(
            "Role-verified fulfillment, menu, stock & KPIs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (menuItems.isEmpty() || inventory.isEmpty()) {
          OutlinedButton(onClick = onSeedCatalog) {
            Text("Seed Data")
          }
        }
      }
    }

    item {
      SecondaryTabRow(selectedTabIndex = selectedTab) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Orders") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Menu") })
        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Stock") })
        Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Analytics") })
      }
    }

    when (selectedTab) {
      0 -> {
        if (orders.isEmpty()) {
          item {
            EmptyAdminCard("No orders in queue yet", "Incoming customer orders will stream here in real time.")
          }
        } else {
          items(orders, key = { it.id }) { order ->
            AdminOrderCard(
              order = order,
              onAdvance = { onAdvanceOrder(order) },
              onUpdateGps = { prog, eta -> onUpdateCourierTelemetry(order, prog, eta) },
            )
          }
        }
      }

      1 -> {
        item {
          Button(
            onClick = {
              editingMenuItem = null
              showMenuDialog = true
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_add_menu_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add Menu Dish")
          }
        }
        items(menuItems, key = { it.id }) { item ->
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
              Icon(Icons.Rounded.RestaurantMenu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text(
                  "${item.category} • ${formatCents(item.priceCents)} • Stock ${item.stockCount}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(
                checked = item.isAvailable,
                onCheckedChange = { avail -> onSaveMenuItem(item.copy(isAvailable = avail)) },
              )
              IconButton(
                onClick = {
                  editingMenuItem = item
                  showMenuDialog = true
                },
              ) {
                Icon(Icons.Rounded.ArrowForward, contentDescription = "Edit dish")
              }
              IconButton(onClick = { onDeleteMenuItem(item.id) }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete dish")
              }
            }
          }
        }
      }

      2 -> {
        val lowStockCount = inventory.count { it.quantityOnHand <= it.reorderThreshold }
        if (lowStockCount > 0) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
              shape = RoundedCornerShape(16.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(14.dp),
              ) {
                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                  "$lowStockCount kitchen SKU(s) at or below reorder threshold.",
                  style = MaterialTheme.typography.titleSmall,
                )
              }
            }
          }
        }
        item {
          Button(
            onClick = { showInventoryDialog = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("admin_add_inventory_button"),
          ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add Inventory SKU")
          }
        }
        items(inventory, key = { it.id }) { sku ->
          val isLow = sku.quantityOnHand <= sku.reorderThreshold
          val progress = (sku.quantityOnHand.toFloat() / (sku.reorderThreshold * 3f).coerceAtLeast(1f)).coerceIn(0f, 1f)
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Column {
                    Text(sku.skuName, style = MaterialTheme.typography.titleSmall)
                    Text("${sku.category} • ${sku.supplier}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
                Text(
                  text = "${sku.quantityOnHand} ${sku.unit}",
                  style = MaterialTheme.typography.titleSmall,
                  color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                  fontWeight = FontWeight.Bold,
                )
              }
              LinearProgressIndicator(
                progress = { progress },
                color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp),
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Reorder @ ${sku.reorderThreshold} ${sku.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  OutlinedButton(onClick = { onAdjustInventoryStock(sku.id, (sku.quantityOnHand - 1).coerceAtLeast(0)) }) {
                    Text("-1")
                  }
                  Button(onClick = { onAdjustInventoryStock(sku.id, sku.quantityOnHand + 10) }) {
                    Text("+10 Restock")
                  }
                }
              }
            }
          }
        }
      }

      3 -> {
        item {
          AnalyticsSection(orders = orders, reviews = reviews, menuItems = menuItems)
        }
      }
    }
  }

  if (showMenuDialog) {
    MenuItemEditorDialog(
      initial = editingMenuItem,
      onDismiss = { showMenuDialog = false },
      onSave = { item ->
        onSaveMenuItem(item)
        showMenuDialog = false
      },
    )
  }

  if (showInventoryDialog) {
    InventorySkuDialog(
      onDismiss = { showInventoryDialog = false },
      onSave = { sku ->
        onSaveInventoryItem(sku)
        showInventoryDialog = false
      },
    )
  }
}

@Composable
private fun AdminOrderCard(
  order: Order,
  onAdvance: () -> Unit,
  onUpdateGps: (Double, Long) -> Unit,
) {
  var sliderPos by remember(order.id, order.courierLat) {
    val dLat = order.deliveryLat - order.restaurantLat
    val frac = if (kotlin.math.abs(dLat) > 1e-6) {
      ((order.courierLat - order.restaurantLat) / dLat).toFloat().coerceIn(0f, 1f)
    } else 0.5f
    mutableFloatStateOf(frac)
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(18.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text("#${order.id.takeLast(5).uppercase()} • ${order.customerName}", style = MaterialTheme.typography.titleSmall)
          Text(formatTimestamp(order.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusPill(order.orderStatus)
      }

      Text(order.itemsSummary, style = MaterialTheme.typography.bodyMedium)
      Text(
        "${order.deliveryAddressText} • ${formatCents(order.totalCents)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (order.orderStatus == OrderStatus.OUT_FOR_DELIVERY) {
        Column {
          Text(
            text = "Courier GPS Route Progress: ${(sliderPos * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          Slider(
            value = sliderPos,
            onValueChange = { sliderPos = it },
            onValueChangeFinished = {
              val eta = ((1f - sliderPos) * 18f).roundToLong().coerceAtLeast(1L)
              onUpdateGps(sliderPos.toDouble(), eta)
            },
          )
        }
      }

      val nextActionLabel = when (order.orderStatus) {
        OrderStatus.PLACED -> "Start Preparing"
        OrderStatus.PREPARING -> "Mark Ready for Pickup"
        OrderStatus.READY_FOR_PICKUP -> "Dispatch Courier"
        OrderStatus.OUT_FOR_DELIVERY -> "Mark Delivered"
        else -> null
      }
      if (nextActionLabel != null) {
        Button(
          onClick = onAdvance,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_advance_order_${order.id}"),
        ) {
          Icon(Icons.Rounded.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text(nextActionLabel)
        }
      }
    }
  }
}

@Composable
private fun AnalyticsSection(
  orders: List<Order>,
  reviews: List<OrderReview>,
  menuItems: List<MenuItem>,
) {
  val nonCancelled = orders.filter { it.orderStatus != OrderStatus.CANCELLED }
  val totalRevenue = nonCancelled.sumOf { it.totalCents }
  val avgOrderValue = if (nonCancelled.isNotEmpty()) totalRevenue / nonCancelled.size else 0L
  val avgRating = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else 5.0

  val statusCounts = listOf(
    "Placed" to orders.count { it.orderStatus == OrderStatus.PLACED },
    "Prep" to orders.count { it.orderStatus == OrderStatus.PREPARING || it.orderStatus == OrderStatus.READY_FOR_PICKUP },
    "En Route" to orders.count { it.orderStatus == OrderStatus.OUT_FOR_DELIVERY },
    "Done" to orders.count { it.orderStatus == OrderStatus.DELIVERED },
  )

  Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      KpiCard("Gross Revenue", formatCents(totalRevenue), Modifier.weight(1f))
      KpiCard("Total Orders", nonCancelled.size.toString(), Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      KpiCard("Avg Order Value", formatCents(avgOrderValue), Modifier.weight(1f))
      KpiCard(
        "Avg Rating",
        String.format(Locale.US, "%.1f ★ (%d)", avgRating, reviews.size),
        Modifier.weight(1f),
      )
    }

    // Custom Canvas Pipeline Bar Chart
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      shape = RoundedCornerShape(18.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Fulfillment Pipeline Breakdown", style = MaterialTheme.typography.titleMedium)
        val barColor = MaterialTheme.colorScheme.primary
        val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
        val maxCount = statusCounts.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        ) {
          val barWidth = size.width / (statusCounts.size * 2f)
          statusCounts.forEachIndexed { idx, (_, count) ->
            val x = barWidth * (idx * 2f + 0.5f)
            drawRoundRect(
              color = trackColor,
              topLeft = Offset(x, 0f),
              size = Size(barWidth, size.height),
              cornerRadius = CornerRadius(14f, 14f),
            )
            val fillH = (count / maxCount.toFloat()) * size.height
            if (fillH > 0f) {
              drawRoundRect(
                color = barColor,
                topLeft = Offset(x, size.height - fillH),
                size = Size(barWidth, fillH),
                cornerRadius = CornerRadius(14f, 14f),
              )
            }
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
          statusCounts.forEach { (label, count) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(count.toString(), style = MaterialTheme.typography.titleSmall)
              Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }

    // Top Dishes
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      shape = RoundedCornerShape(18.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Catalog Overview (${menuItems.size} dishes)", style = MaterialTheme.typography.titleMedium)
        menuItems.take(4).forEach { dish ->
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(dish.name, style = MaterialTheme.typography.bodyMedium)
            Text("${formatCents(dish.priceCents)} • ${dish.stockCount} left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }

    // Recent Customer Reviews
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
      shape = RoundedCornerShape(18.dp),
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Customer Feedback Stream", style = MaterialTheme.typography.titleMedium)
        if (reviews.isEmpty()) {
          Text("No customer reviews submitted yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          reviews.take(5).forEach { rev ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(rev.customerName, style = MaterialTheme.typography.titleSmall)
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Text(" ${rev.rating}/5", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                  }
                }
                if (rev.tags.isNotEmpty()) {
                  Text(rev.tags.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (rev.comment.isNotBlank()) {
                  Text(rev.comment, style = MaterialTheme.typography.bodySmall)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun KpiCard(title: String, value: String, modifier: Modifier = Modifier) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(16.dp),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    }
  }
}

@Composable
private fun EmptyAdminCard(title: String, subtitle: String) {
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
private fun MenuItemEditorDialog(
  initial: MenuItem?,
  onDismiss: () -> Unit,
  onSave: (MenuItem) -> Unit,
) {
  var name by remember { mutableStateOf(initial?.name ?: "") }
  var description by remember { mutableStateOf(initial?.description ?: "") }
  var category by remember { mutableStateOf(initial?.category ?: "Bowls") }
  var priceDollars by remember { mutableStateOf(if (initial != null) String.format(Locale.US, "%.2f", initial.priceCents / 100.0) else "16.50") }
  var prepMinutes by remember { mutableStateOf((initial?.prepMinutes ?: 15L).toString()) }
  var stockCount by remember { mutableStateOf((initial?.stockCount ?: 25L).toString()) }
  var imageKey by remember { mutableStateOf(initial?.imageKey ?: "bowl") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (initial == null) "New Menu Dish" else "Edit Dish") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Dish Name") }, singleLine = true)
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, maxLines = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("Bowls" to "bowl", "Burgers" to "burger", "Pizza" to "pizza").forEach { (cat, key) ->
            FilterChip(
              selected = category == cat,
              onClick = {
                category = cat
                imageKey = key
              },
              label = { Text(cat) },
            )
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = priceDollars,
            onValueChange = { priceDollars = it },
            label = { Text("Price ($)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = stockCount,
            onValueChange = { stockCount = it.filter(Char::isDigit) },
            label = { Text("Stock") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedTextField(
          value = prepMinutes,
          onValueChange = { prepMinutes = it.filter(Char::isDigit) },
          label = { Text("Prep Time (min)") },
          singleLine = true,
        )
      }
    },
    confirmButton = {
      val priceCents = ((priceDollars.toDoubleOrNull() ?: 0.0) * 100).roundToLong()
      Button(
        onClick = {
          onSave(
            (initial ?: MenuItem()).copy(
              name = name.trim(),
              description = description.trim().ifBlank { "Chef prepared fresh to order." },
              category = category,
              priceCents = priceCents.coerceAtLeast(100L),
              prepMinutes = (prepMinutes.toLongOrNull() ?: 15L).coerceIn(1L, 180L),
              stockCount = (stockCount.toLongOrNull() ?: 20L).coerceIn(0L, 10000L),
              imageKey = imageKey,
              isAvailable = initial?.isAvailable ?: true,
            )
          )
        },
        enabled = name.trim().length >= 2 && priceCents >= 100L,
      ) {
        Text("Save Dish")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}

@Composable
private fun InventorySkuDialog(
  onDismiss: () -> Unit,
  onSave: (InventoryItem) -> Unit,
) {
  var skuName by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Produce") }
  var quantity by remember { mutableStateOf("30") }
  var unit by remember { mutableStateOf("units") }
  var threshold by remember { mutableStateOf("10") }
  var supplier by remember { mutableStateOf("Local Harvest Co.") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("New Inventory SKU") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = skuName, onValueChange = { skuName = it }, label = { Text("SKU Name") }, singleLine = true)
        OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it.filter(Char::isDigit) },
            label = { Text("Qty") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = unit,
            onValueChange = { unit = it },
            label = { Text("Unit") },
            singleLine = true,
            modifier = Modifier.weight(1f),
          )
        }
        OutlinedTextField(
          value = threshold,
          onValueChange = { threshold = it.filter(Char::isDigit) },
          label = { Text("Reorder Threshold") },
          singleLine = true,
        )
        OutlinedTextField(value = supplier, onValueChange = { supplier = it }, label = { Text("Supplier") }, singleLine = true)
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            InventoryItem(
              skuName = skuName.trim(),
              category = category.trim().ifBlank { "Kitchen" },
              quantityOnHand = (quantity.toLongOrNull() ?: 0L).coerceIn(0L, 100000L),
              unit = unit.trim().ifBlank { "units" },
              reorderThreshold = (threshold.toLongOrNull() ?: 10L).coerceIn(0L, 10000L),
              supplier = supplier.trim().ifBlank { "Primary Supplier" },
            )
          )
        },
        enabled = skuName.trim().length >= 2,
      ) {
        Text("Save SKU")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
