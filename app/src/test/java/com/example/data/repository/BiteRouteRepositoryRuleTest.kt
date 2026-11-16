package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.CartItem
import com.example.data.model.DeliveryAddress
import com.example.data.model.MenuItem
import com.example.data.model.PaymentMethod
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BiteRouteRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun userProfileAddressesAndPayments_ownerCrud_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = BiteRouteRepository(firestore)

    val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.ensureUserProfile("Alice", ALICE_EMAIL, null)
    }
    assertTrue("ensureUserProfile failed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)

    val addrRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.saveAddress(
        DeliveryAddress(
          label = "Home",
          street = "742 Evergreen Ave",
          city = "San Francisco",
          lat = 37.7812,
          lng = -122.4112,
          isDefault = true,
        ),
        emptyList(),
      )
    }
    assertTrue("saveAddress failed: ${addrRes.exceptionOrNull()}", addrRes.isSuccess)

    val pmRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.savePaymentMethod(
        PaymentMethod(
          type = "card",
          brand = "Visa",
          last4 = "4242",
          expiry = "08/28",
          cardholderName = "Alice",
          isDefault = true,
        ),
        emptyList(),
      )
    }
    assertTrue("savePaymentMethod failed: ${pmRes.exceptionOrNull()}", pmRes.isSuccess)

    val addresses = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeAddresses(uid).first { it.isNotEmpty() }
    }
    assertEquals("Home", addresses.first().label)
  }

  @Test
  fun placeOrderAndCrossUserIsolation_enforcesOwnership() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = BiteRouteRepository(firestore)

    val orderRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.placeOrder(
        customerName = "Alice",
        address = DeliveryAddress(
          label = "Work",
          street = "500 Howard St",
          city = "San Francisco",
          lat = 37.7885,
          lng = -122.3965,
          isDefault = true,
        ),
        paymentMethod = PaymentMethod(
          type = "card",
          brand = "Mastercard",
          last4 = "8888",
          cardholderName = "Alice",
          isDefault = true,
        ),
        cartItems = listOf(
          CartItem(
            menuItem = MenuItem(
              id = "",
              name = "Miso Salmon Bowl",
              priceCents = 1850L,
              prepMinutes = 14L,
              stockCount = 0L,
            ),
            quantity = 2,
          )
        ),
        tipCents = 400L,
      )
    }
    assertTrue("placeOrder failed: ${orderRes.exceptionOrNull()}", orderRes.isSuccess)
    val orderId = orderRes.getOrThrow()

    val aliceOrders = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeUserOrders(aliceUid).first { list -> list.any { it.id == orderId } }
    }
    assertEquals(1, aliceOrders.count { it.id == orderId })

    // Bob should not be able to read Alice's orders or cancel Alice's order
    signInTestUser(BOB_EMAIL)
    val bobRepo = BiteRouteRepository(firestore)
    val cancelRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.cancelOrder(orderId)
    }
    assertTrue(cancelRes.isFailure)
    val fsErr = cancelRes.exceptionOrNull()?.unwrapFirestoreException()
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
  }

  @Test
  fun observeUserOrders_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = BiteRouteRepository(firestore)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserOrders("alice_uid").first()
      }
      fail("Expected PERMISSION_DENIED for unauthenticated user")
    } catch (e: Exception) {
      val fsErr = checkNotNull(e.unwrapFirestoreException()) {
        "Expected nested FirebaseFirestoreException, got $e"
      }
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice_rules@test.com"
    const val BOB_EMAIL = "bob_rules@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
