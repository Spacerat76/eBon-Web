import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  applyProductDecisions,
  assertPersistentStateSafe,
  buildProductQueue,
  prepareProductBlock,
  recordOpenProductDecisions,
  validateProductDecision,
  writeProductStateAtomic,
} from "./product-audit.mjs";

function receiptState() {
  return {
    runId: "receipt-run",
    merchants: [{
      merchantKey: "rewe",
      merchantName: "REWE",
      branches: [{
        branchKey: "hauptstrasse",
        branchName: "Hauptstraße",
        documents: [
          { paperlessDocumentId: 10, receiptId: 100, status: "VERIFIED" },
          { paperlessDocumentId: 11, receiptId: 101, status: "NEEDS_USER" },
        ],
      }],
    }],
  };
}

function item(overrides = {}) {
  return {
    id: 1001,
    receiptId: 100,
    description: "PRIVATE MILCH 1 L",
    productFamilyId: null,
    productVariantId: null,
    productAssignmentSource: null,
    productAssignmentStatus: "NEEDS_REVIEW",
    ...overrides,
  };
}

function families() {
  return [
    { id: 1, name: "Milch", isActive: true },
    { id: 2, name: "Haferdrink", isActive: true },
  ];
}

function variants() {
  return [{ id: 11, productFamilyId: 1, name: "Milch 1 l", isActive: true }];
}

function baseDecision(overrides = {}) {
  return {
    receiptId: 100,
    receiptItemId: 1001,
    expected: { familyId: null, variantId: null, source: null, status: "NEEDS_REVIEW" },
    action: "ASSIGN_EXISTING",
    familyId: 1,
    variantId: 11,
    confidence: 0.99,
    userConfirmedManual: false,
    reasonCode: "UNIQUE_EXISTING_FAMILY",
    ...overrides,
  };
}

async function preparedState() {
  const stateDir = await mkdtemp(join(tmpdir(), "product-audit-"));
  const productState = buildProductQueue(receiptState());
  const client = {
    getReceipt: async () => ({ id: 100, items: [item()] }),
    listFamilies: async () => families(),
    listVariants: async () => variants(),
  };
  await prepareProductBlock({ stateDir, receiptState: receiptState(), productState, client });
  return { stateDir, productState };
}

test("queues only receipt-verified documents and preserves merchant order", () => {
  const state = buildProductQueue(receiptState());
  assert.equal(state.blocks.length, 1);
  assert.deepEqual(state.blocks[0].receipts, [{ paperlessDocumentId: 10, receiptId: 100 }]);
});

test("prepares a private block with current items and active product catalog", async () => {
  const { stateDir, productState } = await preparedState();
  const batch = JSON.parse(await readFile(productState.activeBatchPath, "utf8"));
  assert.equal(batch.receipts[0].items[0].description, "PRIVATE MILCH 1 L");
  assert.deepEqual(batch.families.map(entry => entry.id), [1, 2]);
  assert.deepEqual(batch.variants.map(entry => entry.id), [11]);

  const persisted = await readFile(join(stateDir, "product-progress.json"), "utf8");
  assert.doesNotMatch(persisted, /PRIVATE|description/i);
});

test("rejects stale decisions before any mutation", async () => {
  const { stateDir, productState } = await preparedState();
  let mutations = 0;
  const client = {
    getReceipt: async () => ({ id: 100, items: [item({ productFamilyId: 2 })] }),
    listFamilies: async () => families(),
    listVariants: async () => variants(),
    auditCorrect: async () => { mutations += 1; },
  };
  await assert.rejects(
    applyProductDecisions({ stateDir, productState, decisionDocument: decisionDocument(baseDecision()), client }),
    /STALE_PRODUCT_ASSIGNMENT/,
  );
  assert.equal(mutations, 0);
});

test("manual and confirmed assignments require exact user confirmation", async () => {
  const current = item({
    productFamilyId: 2,
    productAssignmentSource: "MANUAL",
    productAssignmentStatus: "CONFIRMED",
  });
  const decision = baseDecision({
    expected: { familyId: 2, variantId: null, source: "MANUAL", status: "CONFIRMED" },
    familyId: 1,
  });
  assert.throws(() => validateProductDecision(decision, current, families(), variants()), /USER_CONFIRMATION_REQUIRED/);
  assert.doesNotThrow(() => validateProductDecision(
    { ...decision, userConfirmedManual: true }, current, families(), variants()));
});

test("NO_PRODUCT requires explicit user confirmation before using the manual endpoint", () => {
  const decision = baseDecision({
    action: "NO_PRODUCT",
    familyId: undefined,
    variantId: undefined,
    confidence: undefined,
    reasonCode: "SAFE_NON_PRODUCT_LINE",
  });

  assert.throws(
    () => validateProductDecision(decision, item(), families(), variants()),
    /USER_CONFIRMATION_REQUIRED/,
  );
  assert.doesNotThrow(() => validateProductDecision(
    { ...decision, userConfirmedManual: true }, item(), families(), variants()));
});

test("applies an obvious non-manual assignment through the audit endpoint", async () => {
  const { stateDir, productState } = await preparedState();
  const calls = [];
  const client = {
    getReceipt: async () => ({ id: 100, items: [item()] }),
    listFamilies: async () => families(),
    listVariants: async () => variants(),
    auditCorrect: async (id, payload) => calls.push({ id, payload }),
  };
  await applyProductDecisions({
    stateDir,
    productState,
    decisionDocument: decisionDocument(baseDecision()),
    client,
  });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].id, 1001);
  assert.deepEqual(calls[0].payload.expected, {
    productFamilyId: null,
    productVariantId: null,
    source: null,
    status: "NEEDS_REVIEW",
  });
  assert.equal(calls[0].payload.productFamilyId, 1);
  assert.equal(calls[0].payload.productVariantId, 11);
});

test("applies a confirmed manual correction only to the frozen item and target", async () => {
  const { stateDir, productState } = await preparedState();
  const calls = [];
  const current = item({
    productFamilyId: 2,
    productAssignmentSource: "MANUAL",
    productAssignmentStatus: "CONFIRMED",
  });
  const client = {
    getReceipt: async () => ({ id: 100, items: [current] }),
    listFamilies: async () => families(),
    listVariants: async () => variants(),
    manualCorrect: async (id, payload) => calls.push({ id, payload }),
  };
  const decision = baseDecision({
    expected: { familyId: 2, variantId: null, source: "MANUAL", status: "CONFIRMED" },
    userConfirmedManual: true,
  });

  await applyProductDecisions({
    stateDir,
    productState,
    decisionDocument: decisionDocument(decision),
    client,
  });

  assert.deepEqual(calls, [{
    id: 1001,
    payload: {
      productFamilyId: 1,
      newProductFamilyName: null,
      productVariantId: 11,
      applyToSameStoreDescription: false,
    },
  }]);
});

test("new families require high confidence, unique names, similarity review, and no size suffix", () => {
  const current = item();
  const create = baseDecision({
    action: "CREATE_FAMILY",
    familyId: undefined,
    variantId: undefined,
    newFamilyName: "Mandelmus",
    newVariant: { name: "Mandelmus 500 g", totalQuantity: 500, totalUnit: "g" },
    noSimilarFamilyExists: true,
  });
  assert.throws(() => validateProductDecision({ ...create, confidence: 0.979 }, current, families(), variants()), /CONFIDENCE_TOO_LOW/);
  assert.throws(() => validateProductDecision({ ...create, newFamilyName: "Milch" }, current, families(), variants()), /FAMILY_NAME_EXISTS/);
  assert.throws(() => validateProductDecision({ ...create, noSimilarFamilyExists: false }, current, families(), variants()), /SIMILARITY_REVIEW_REQUIRED/);
  assert.throws(() => validateProductDecision({ ...create, newFamilyName: "Mandelmus 500 g" }, current, families(), variants()), /SIZE_BELONGS_TO_VARIANT/);
  assert.doesNotThrow(() => validateProductDecision(create, current, families(), variants()));
});

test("a failed mutation is persisted without private data and stops later calls", async () => {
  const { stateDir, productState } = await preparedState();
  productState.blocks[0].itemIds.push({ receiptId: 100, receiptItemId: 1002 });
  await writeProductStateAtomic(stateDir, productState);
  let calls = 0;
  const client = {
    getReceipt: async () => ({ id: 100, items: [item(), item({ id: 1002 })] }),
    listFamilies: async () => families(),
    listVariants: async () => variants(),
    auditCorrect: async () => {
      calls += 1;
      if (calls === 1) throw new Error("PRIVATE upstream body");
    },
  };
  await assert.rejects(applyProductDecisions({
    stateDir,
    productState,
    decisionDocument: decisionDocument([baseDecision(), baseDecision({ receiptItemId: 1002 })]),
    client,
  }), /PRODUCT_APPLY_FAILED/);
  assert.equal(calls, 1);
  const persisted = await readFile(join(stateDir, "product-progress.json"), "utf8");
  assert.match(persisted, /APPLY_FAILED/);
  assert.doesNotMatch(persisted, /PRIVATE upstream/);
});

test("a failed second step of a confirmed manual family and variant creation is recorded", async () => {
  const { stateDir, productState } = await preparedState();
  let manualCalls = 0;
  let variantCalls = 0;
  const current = item({
    productFamilyId: 2,
    productAssignmentSource: "MANUAL",
    productAssignmentStatus: "CONFIRMED",
  });
  const client = {
    getReceipt: async () => ({ id: 100, items: [current] }),
    listFamilies: async () => families(),
    listVariants: async () => variants(),
    manualCorrect: async () => {
      manualCalls += 1;
      return { currentProductFamilyId: 77 };
    },
    createVariant: async () => {
      variantCalls += 1;
      throw new Error("PRIVATE variant response");
    },
  };
  const decision = baseDecision({
    expected: { familyId: 2, variantId: null, source: "MANUAL", status: "CONFIRMED" },
    action: "CREATE_FAMILY",
    familyId: undefined,
    variantId: undefined,
    newFamilyName: "Mandelmus",
    newVariant: { name: "Mandelmus 500 g", totalQuantity: 500, totalUnit: "g" },
    noSimilarFamilyExists: true,
    userConfirmedManual: true,
  });

  await assert.rejects(applyProductDecisions({
    stateDir,
    productState,
    decisionDocument: decisionDocument(decision),
    client,
  }), /PRODUCT_APPLY_FAILED/);

  assert.equal(manualCalls, 1);
  assert.equal(variantCalls, 1);
  const persisted = await readFile(join(stateDir, "product-progress.json"), "utf8");
  assert.match(persisted, /APPLY_FAILED/);
  assert.doesNotMatch(persisted, /PRIVATE variant/);
});

test("records open proposals by IDs only and completes the covered block", async () => {
  const { stateDir, productState } = await preparedState();
  await recordOpenProductDecisions({
    stateDir,
    productState,
    decisionDocument: {
      runId: productState.runId,
      merchantKey: "rewe",
      branchKey: "hauptstrasse",
      decisions: [{
        receiptId: 100,
        receiptItemId: 1001,
        status: "USER_CONFIRMATION_REQUIRED",
        confidence: 0.9,
        reasonCode: "MANUAL_ASSIGNMENT_CONFLICT",
        proposedFamilyId: 1,
        proposedVariantId: 11,
      }],
    },
  });
  const persisted = JSON.parse(await readFile(join(stateDir, "product-progress.json"), "utf8"));
  assert.equal(persisted.blocks[0].status, "COMPLETED");
  assert.equal(persisted.blocks[0].results[0].proposedFamilyId, 1);
  assert.doesNotThrow(() => assertPersistentStateSafe(persisted));
});

function decisionDocument(decisions) {
  const list = Array.isArray(decisions) ? decisions : [decisions];
  return {
    runId: "product-receipt-run",
    merchantKey: "rewe",
    branchKey: "hauptstrasse",
    decisions: list,
  };
}
