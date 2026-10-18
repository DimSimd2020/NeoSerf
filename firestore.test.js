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

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read bookmarks, passwords, history, or speed_dial", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("bookmarks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("passwords").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("history").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("speed_dial").get());
});

test("Authenticated user: can create and query own bookmark with matching userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  const bookmarkRef = aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("bm_1");

  await assertSucceeds(
    bookmarkRef.set({
      userId: ALICE_UID,
      title: "Kotlin Docs",
      url: "https://kotlinlang.org",
      folder: "Разработка",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user: cannot read or write another user's bookmarks or passwords", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("passwords").doc("pw_1").set({
      userId: BOB_UID,
      siteDomain: "github.com",
      siteTitle: "GitHub",
      username: "bob",
      encryptedPassword: "ENC_VALUE_123",
      notes: "Work account",
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("passwords").doc("pw_1").get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("passwords").where("userId", "==", BOB_UID).get()
  );
});

test("Shadow update with ghost field is rejected", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("bm_2");

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      title: "NeoSerf Home",
      url: "https://neoserf.app",
      folder: "Избранное",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    docRef.update({
      title: "Hacked",
      isVerified: true,
      updatedAt: new Date(),
    })
  );
});
