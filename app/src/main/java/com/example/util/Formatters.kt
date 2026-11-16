package com.example.util

import com.example.R
import com.google.firebase.Timestamp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun formatCents(cents: Long): String {
  val formatter = NumberFormat.getCurrencyInstance(Locale.US)
  return formatter.format(cents / 100.0)
}

fun formatTimestamp(ts: Timestamp?): String {
  val date = ts?.toDate() ?: Date()
  val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
  return fmt.format(date)
}

fun haversineMiles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
  val rMiles = 3958.8
  val dLat = Math.toRadians(lat2 - lat1)
  val dLng = Math.toRadians(lng2 - lng1)
  val a =
    sin(dLat / 2) * sin(dLat / 2) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
  val c = 2 * atan2(sqrt(a), sqrt(1 - a))
  return rMiles * c
}

fun dishDrawableForKey(imageKey: String): Int =
  when (imageKey.lowercase(Locale.ROOT)) {
    "burger" -> R.drawable.img_dish_burger
    "pizza" -> R.drawable.img_dish_pizza
    "bowl" -> R.drawable.img_dish_bowl
    else -> R.drawable.img_hero_banner
  }
