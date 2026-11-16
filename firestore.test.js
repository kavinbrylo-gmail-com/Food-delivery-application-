const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "kavinbrylo@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date();
}

function validOrderPayload(uid, overrides = {}) {
  return {
    userId: uid,
    customerName: "Alice",
    restaurantName: "BiteRoute Kitchen",
    restaurantLat: 37.7749,
    restaurantLng: -122.4194,
    deliveryAddressLabel: "Home",
    deliveryAddressText: "742 Evergreen Ave, San Francisco",
    deliveryLat: 37.7812,
    deliveryLng: -122.4112,
    courierName: "Marco V.",
    courierLat: 37.7752,
    courierLng: -122.4190,
    etaMinutes: 24,
    status: "placed",
    paymentLabel: "Visa •••• 4242",
    itemsSummary: "1x Miso Salmon Bowl",
    items: [{ menuItemId: "item_1", name: "Miso Salmon Bowl", quantity: 1, priceCents: 1850 }],
    subtotalCents: 1850,
    deliveryFeeCents: 299,
    taxCents: 162,
    tipCents: 300,
    totalCents: 2611,
    hasFeedback: false,
    createdAt: nowTs(),
    updatedAt: nowTs(),
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("menu_items").get());
  await assertFails(unauthDb.collection("orders").where("userId", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("inventory").get());
});

test("User profile: owner can create and update, cross-user cannot read, shadow ghost field rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      orderStatusAlerts: true,
      courierArrivalAlerts: true,
      promoAlerts: false,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  // Cross-user read rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  // Non-admin list users rejected
  await assertFails(aliceDb.collection("users").get());
  // Shadow ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: nowTs(),
    })
  );
});

test("Addresses & payment methods: strict owner-only access", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("addresses").doc("addr_1").set({
      label: "Home",
      street: "123 Market St",
      city: "San Francisco",
      lat: 37.7749,
      lng: -122.4194,
      isDefault: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("payment_methods").doc("pm_1").set({
      type: "card",
      brand: "Visa",
      last4: "4242",
      expiry: "08/28",
      cardholderName: "Alice",
      isDefault: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("addresses").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("payment_methods").get());
});

test("Admin RBAC: email spoofing (email_verified=false) rejected; verified admin can manage menu & inventory", async () => {
  const spoofedDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const menuPayload = {
    name: "Miso Salmon Bowl",
    description: "Seared Atlantic salmon over forbidden rice.",
    category: "Bowls",
    priceCents: 1850,
    prepMinutes: 15,
    calories: 640,
    rating: 4.9,
    imageKey: "bowl",
    isAvailable: true,
    stockCount: 24,
    createdAt: nowTs(),
    updatedAt: nowTs(),
  };

  // Spoofed email with unverified flag rejected
  await assertFails(spoofedDb.collection("menu_items").doc("item_1").set(menuPayload));
  await assertFails(spoofedDb.collection("inventory").doc("sku_1").set({
    skuName: "Atlantic Salmon Fillet",
    category: "Seafood",
    quantityOnHand: 40,
    unit: "portions",
    reorderThreshold: 12,
    supplier: "Bay Fresh",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));

  // Verified admin succeeds
  await assertSucceeds(adminDb.collection("menu_items").doc("item_1").set(menuPayload));
  await assertSucceeds(adminDb.collection("inventory").doc("sku_1").set({
    skuName: "Atlantic Salmon Fillet",
    category: "Seafood",
    quantityOnHand: 40,
    unit: "portions",
    reorderThreshold: 12,
    supplier: "Bay Fresh",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));

  // Regular customer can read menu_items, cannot read inventory
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("menu_items").get());
  await assertFails(aliceDb.collection("inventory").get());
  // Regular customer cannot inflate stock or edit price
  await assertFails(aliceDb.collection("menu_items").doc("item_1").update({ priceCents: 100, updatedAt: nowTs() }));
  // Regular customer CAN decrement stock during checkout
  await assertSucceeds(aliceDb.collection("menu_items").doc("item_1").update({ stockCount: 23, updatedAt: nowTs() }));
});

test("Orders & Reviews: query isolation, admin fulfillment, customer cancel/review parent gate", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(aliceDb.collection("orders").doc("ord_1").set(validOrderPayload(ALICE_UID)));

  // Unfiltered list rejected for non-admin (rules are not filters)
  await assertFails(aliceDb.collection("orders").get());
  // Scoped list succeeds for Alice
  await assertSucceeds(aliceDb.collection("orders").where("userId", "==", ALICE_UID).get());
  // Bob cannot read Alice's order
  await assertFails(bobDb.collection("orders").doc("ord_1").get());
  // Alice cannot jump her own order straight to delivered
  await assertFails(aliceDb.collection("orders").doc("ord_1").update({ status: "delivered", updatedAt: nowTs() }));

  // Admin can query all orders and advance status + courier GPS
  await assertSucceeds(adminDb.collection("orders").get());
  await assertSucceeds(
    adminDb.collection("orders").doc("ord_1").update({
      status: "delivered",
      courierLat: 37.7812,
      courierLng: -122.4112,
      etaMinutes: 0,
      updatedAt: nowTs(),
    })
  );

  // Alice can submit feedback for her own existing order and mark hasFeedback = true
  await assertSucceeds(
    aliceDb.collection("reviews").doc("rev_1").set({
      orderId: "ord_1",
      userId: ALICE_UID,
      customerName: "Alice",
      rating: 5,
      tags: ["Fast Delivery", "Hot & Fresh"],
      comment: "Arrived right on time!",
      createdAt: nowTs(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("orders").doc("ord_1").update({
      hasFeedback: true,
      updatedAt: nowTs(),
    })
  );

  // Bob cannot submit review for Alice's order (Parent Gate!)
  await assertFails(
    bobDb.collection("reviews").doc("rev_2").set({
      orderId: "ord_1",
      userId: BOB_UID,
      customerName: "Bob",
      rating: 1,
      tags: [],
      comment: "Fake review",
      createdAt: nowTs(),
    })
  );
});
