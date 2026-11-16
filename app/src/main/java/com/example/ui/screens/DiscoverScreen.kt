package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.DeliveryAddress
import com.example.data.model.MenuItem
import com.example.ui.UiState
import com.example.util.GpsFix
import com.example.util.dishDrawableForKey
import com.example.util.formatCents
import java.util.Locale

@Composable
fun DiscoverScreen(
  menuState: UiState<List<MenuItem>>,
  filteredItems: List<MenuItem>,
  cart: Map<String, CartItem>,
  searchQuery: String,
  selectedCategory: String,
  addresses: List<DeliveryAddress>,
  liveGpsFix: GpsFix?,
  onSearchChange: (String) -> Unit,
  onSelectCategory: (String) -> Unit,
  onAddToCart: (MenuItem) -> Unit,
  onDecrementCart: (String) -> Unit,
  onRequestGpsLocation: () -> Unit,
  onOpenAddresses: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val defaultAddr = addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull()
  val categories = listOf("All", "Bowls", "Burgers", "Pizza")

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Location Header Pill
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .weight(1f)
            .clickable(onClick = onOpenAddresses)
            .testTag("header_address_pill"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.LocationOn,
              contentDescription = "Delivery Address",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
              Text(
                text = defaultAddr?.label ?: "Deliver to",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = when {
                  defaultAddr != null -> "${defaultAddr.street}, ${defaultAddr.city}"
                  liveGpsFix?.streetHint != null -> liveGpsFix.streetHint
                  liveGpsFix != null -> String.format(Locale.US, "GPS %.4f, %.4f", liveGpsFix.lat, liveGpsFix.lng)
                  else -> "Tap to set address or detect GPS"
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        Spacer(Modifier.width(10.dp))
        FilledIconButton(
          onClick = onRequestGpsLocation,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.primary,
          ),
          modifier = Modifier
            .size(48.dp)
            .testTag("detect_gps_button"),
        ) {
          Icon(Icons.Rounded.MyLocation, contentDescription = "Detect current GPS")
        }
      }
    }

    // Editorial Hero Banner
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(176.dp)
          .clip(RoundedCornerShape(22.dp)),
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_hero_banner),
          contentDescription = "BiteRoute Kitchen Showcase",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.horizontalGradient(
                colors = listOf(Color(0xD91C1917), Color(0x331C1917)),
              )
            ),
        )
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(18.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.secondary,
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = "KITCHEN OPEN • LIVE TRACKING",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = "BiteRoute Kitchen",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
          )
          Text(
            text = "14–19 min prep • Precision courier routing",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.88f),
          )
        }
      }
    }

    // Search + Categories
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchChange,
          placeholder = { Text("Search bowls, smash burgers, sourdough pizza…") },
          leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotBlank()) {
              IconButton(onClick = { onSearchChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("menu_search_input"),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(categories) { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { onSelectCategory(cat) },
              label = { Text(cat) },
              modifier = Modifier.testTag("category_chip_${cat.lowercase()}"),
            )
          }
        }
      }
    }

    when (menuState) {
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
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          ) {
            Text(
              text = menuState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      }
      is UiState.Success -> {
        if (filteredItems.isEmpty()) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text("No dishes match your filter", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = if (menuState.data.isEmpty()) {
                    "Kitchen catalog is awaiting publication from a restaurant manager."
                  } else {
                    "Try clearing your search or choosing another category."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(filteredItems, key = { it.id }) { item ->
            val qtyInCart = cart[item.id]?.quantity ?: 0
            MenuDishCard(
              item = item,
              qtyInCart = qtyInCart,
              onAdd = { onAddToCart(item) },
              onDecrement = { onDecrementCart(item.id) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MenuDishCard(
  item: MenuItem,
  qtyInCart: Int,
  onAdd: () -> Unit,
  onDecrement: () -> Unit,
) {
  val soldOut = !item.isAvailable || item.stockCount <= 0L
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(20.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("menu_card_${item.id}"),
  ) {
    Column {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(165.dp),
      ) {
        Image(
          painter = painterResource(id = dishDrawableForKey(item.imageKey)),
          contentDescription = item.name,
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
          shape = RoundedCornerShape(50),
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          ) {
            Icon(
              Icons.Rounded.Star,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.tertiary,
              modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text = String.format(Locale.US, "%.1f", item.rating),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
              Icons.Rounded.Schedule,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
              text = "${item.prepMinutes}m",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (soldOut) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color(0x991C1917)),
            contentAlignment = Alignment.Center,
          ) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(50)) {
              Text(
                text = "SOLD OUT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
              )
            }
          }
        }
      }

      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.Top,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "${item.category} • ${item.calories} kcal • ${item.stockCount} left",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.width(12.dp))
          Text(
            text = formatCents(item.priceCents),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
        }

        Text(
          text = item.description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (qtyInCart == 0) {
            Surface(
              color = if (soldOut) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primary,
              contentColor = if (soldOut) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .clickable(enabled = !soldOut, onClick = onAdd)
                .testTag("add_item_${item.id}"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
              ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add to Bag", style = MaterialTheme.typography.labelLarge)
              }
            }
          } else {
            Surface(
              color = MaterialTheme.colorScheme.primaryContainer,
              shape = RoundedCornerShape(14.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
              ) {
                IconButton(
                  onClick = onDecrement,
                  modifier = Modifier.size(40.dp),
                ) {
                  Icon(Icons.Rounded.Remove, contentDescription = "Decrease quantity", tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                  text = qtyInCart.toString(),
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                IconButton(
                  onClick = onAdd,
                  modifier = Modifier.size(40.dp),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = "Increase quantity", tint = MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
        }
      }
    }
  }
}
