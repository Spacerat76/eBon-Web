import { mkdir, readFile, readdir, rename, rm, writeFile } from "node:fs/promises";
import { basename, join, resolve, sep } from "node:path";
import { pathToFileURL } from "node:url";

const PRODUCT_STATE_FILE = "product-progress.json";
const OPEN_STATUSES = new Set(["PROPOSED", "NO_SENSIBLE_PROPOSAL", "USER_CONFIRMATION_REQUIRED"]);
const CLOSED_ACTIONS = new Set(["ASSIGN_EXISTING", "CREATE_FAMILY", "CREATE_VARIANT", "NO_PRODUCT"]);
const PRIVATE_KEYS = /^(rawText|content|description|ocrText|prompt|response|token|authorization|bytes|original|packageDescription)$/i;
const REASON_CODE = /^[A-Z0-9_]{1,64}$/;

export function assertPersistentStateSafe(value, path = "$") {
  if (Array.isArray(value)) {
    value.forEach((entry, index) => assertPersistentStateSafe(entry, `${path}[${index}]`));
    return;
  }
  if (value && typeof value === "object") {
    for (const [key, entry] of Object.entries(value)) {
      if (PRIVATE_KEYS.test(key)) throw new Error(`PRIVATE_FIELD_FORBIDDEN:${path}.${key}`);
      assertPersistentStateSafe(entry, `${path}.${key}`);
    }
  }
}

export async function writeProductStateAtomic(stateDir, state) {
  assertPersistentStateSafe(state);
  await mkdir(stateDir, { recursive: true });
  const target = join(stateDir, PRODUCT_STATE_FILE);
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(state, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  await rename(temporary, target);
}

export function buildProductQueue(receiptState) {
  if (!receiptState?.runId) throw new Error("RECEIPT_STATE_INVALID");
  const blocks = [];
  for (const merchant of receiptState.merchants ?? []) {
    for (const branch of merchant.branches ?? []) {
      const receipts = (branch.documents ?? [])
        .filter(document => document.status === "VERIFIED" && Number.isInteger(document.receiptId))
        .map(document => ({
          paperlessDocumentId: document.paperlessDocumentId,
          receiptId: document.receiptId,
        }));
      if (receipts.length > 0) {
        blocks.push({
          merchantKey: merchant.merchantKey,
          merchantName: merchant.merchantName,
          branchKey: branch.branchKey,
          branchName: branch.branchName,
          receiptCount: receipts.length,
          status: "PENDING",
          receipts,
          itemIds: [],
          results: [],
        });
      }
    }
  }
  const now = new Date().toISOString();
  return {
    version: 1,
    runId: `product-${receiptState.runId}`,
    sourceReceiptRunId: receiptState.runId,
    createdAt: now,
    updatedAt: now,
    activeBatchPath: null,
    blocks,
  };
}

function nextBlock(state) {
  return state.blocks.find(block => block.status !== "COMPLETED") ?? null;
}

export async function prepareProductBlock({ stateDir, receiptState, productState, client }) {
  const state = productState ?? buildProductQueue(receiptState);
  if (state.sourceReceiptRunId !== receiptState.runId) throw new Error("RECEIPT_RUN_MISMATCH");
  const block = nextBlock(state);
  if (!block) return { batchPath: null, completed: true };

  const receipts = [];
  const itemIds = [];
  for (const receiptRef of block.receipts) {
    const receipt = await client.getReceipt(receiptRef.receiptId);
    receipts.push(receipt);
    for (const receiptItem of receipt.items ?? []) {
      itemIds.push({ receiptId: receipt.id, receiptItemId: receiptItem.id });
    }
  }
  const families = (await client.listFamilies()).filter(family => family.isActive !== false);
  const variants = (await client.listVariants()).filter(variant => variant.isActive !== false);
  block.itemIds = itemIds;
  block.status = "IN_PROGRESS";

  const temporaryDirectory = join(stateDir, "tmp");
  await mkdir(temporaryDirectory, { recursive: true });
  const batchPath = join(temporaryDirectory, `product-${block.merchantKey}-${block.branchKey}.json`);
  await writeFile(batchPath, `${JSON.stringify({
    runId: state.runId,
    merchantKey: block.merchantKey,
    branchKey: block.branchKey,
    receipts,
    families,
    variants,
  }, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  state.activeBatchPath = batchPath;
  state.updatedAt = new Date().toISOString();
  await writeProductStateAtomic(stateDir, state);
  return { batchPath, completed: false, receiptCount: receipts.length, itemCount: itemIds.length };
}

function normalizeFamilyName(value) {
  return String(value ?? "")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function containsSizeOrPackage(value) {
  const normalized = String(value ?? "").toLowerCase();
  return /\b\d+(?:[.,]\d+)?\s*(?:mg|g|kg|ml|cl|l|stk|stueck|stück)\b/u.test(normalized)
    || /\b\d+\s*x(?:\s*\d+)?\b/u.test(normalized);
}

function activeFamily(families, id) {
  return families.find(family => family.id === id && family.isActive !== false);
}

function activeVariant(variants, id) {
  return variants.find(variant => variant.id === id && variant.isActive !== false);
}

function isProtected(item) {
  return item.productAssignmentSource === "MANUAL"
    || ["CONFIRMED", "NO_PRODUCT", "REJECTED"].includes(item.productAssignmentStatus);
}

function assertReasonCode(reasonCode) {
  if (!REASON_CODE.test(String(reasonCode ?? ""))) throw new Error("INVALID_REASON_CODE");
}

function assertExpectedAssignment(item, expected) {
  if (!expected
    || (item.productFamilyId ?? null) !== (expected.familyId ?? null)
    || (item.productVariantId ?? null) !== (expected.variantId ?? null)
    || (item.productAssignmentSource ?? null) !== (expected.source ?? null)
    || (item.productAssignmentStatus ?? null) !== (expected.status ?? null)) {
    throw new Error("STALE_PRODUCT_ASSIGNMENT");
  }
}

export function validateProductDecision(decision, currentItem, families, variants) {
  if (!CLOSED_ACTIONS.has(decision.action)) throw new Error("INVALID_PRODUCT_ACTION");
  assertReasonCode(decision.reasonCode);
  assertExpectedAssignment(currentItem, decision.expected);
  if (isProtected(currentItem) && decision.userConfirmedManual !== true) {
    throw new Error("USER_CONFIRMATION_REQUIRED");
  }
  if (decision.action === "NO_PRODUCT") {
    if (decision.userConfirmedManual !== true) throw new Error("USER_CONFIRMATION_REQUIRED");
    return;
  }
  if (!Number.isFinite(decision.confidence) || decision.confidence < 0.98 || decision.confidence > 1) {
    throw new Error("CONFIDENCE_TOO_LOW");
  }

  if (decision.action === "ASSIGN_EXISTING") {
    const family = activeFamily(families, decision.familyId);
    if (!family) throw new Error("ACTIVE_FAMILY_NOT_FOUND");
    if (decision.variantId != null) {
      const variant = activeVariant(variants, decision.variantId);
      if (!variant) throw new Error("ACTIVE_VARIANT_NOT_FOUND");
      if (variant.productFamilyId !== family.id) throw new Error("VARIANT_FAMILY_MISMATCH");
    }
    return;
  }

  if (decision.action === "CREATE_VARIANT") {
    if (!activeFamily(families, decision.familyId)) throw new Error("ACTIVE_FAMILY_NOT_FOUND");
    if (!decision.newVariant?.name) throw new Error("NEW_VARIANT_REQUIRED");
    return;
  }

  const normalizedName = normalizeFamilyName(decision.newFamilyName);
  if (!normalizedName) throw new Error("NEW_FAMILY_NAME_REQUIRED");
  if (families.some(family => normalizeFamilyName(family.name) === normalizedName)) {
    throw new Error("FAMILY_NAME_EXISTS");
  }
  if (decision.noSimilarFamilyExists !== true) throw new Error("SIMILARITY_REVIEW_REQUIRED");
  if (containsSizeOrPackage(decision.newFamilyName)) throw new Error("SIZE_BELONGS_TO_VARIANT");
  if (decision.newVariant != null && !decision.newVariant.name) throw new Error("INVALID_NEW_VARIANT");
}

function findBlock(state, document) {
  if (state.runId !== document.runId) throw new Error("RUN_ID_MISMATCH");
  const block = state.blocks.find(candidate => candidate.merchantKey === document.merchantKey
    && candidate.branchKey === document.branchKey);
  if (!block) throw new Error("PRODUCT_BLOCK_NOT_FOUND");
  return block;
}

function assertDecisionTargetsBlock(block, decisions) {
  if (!Array.isArray(decisions) || decisions.length === 0) throw new Error("PRODUCT_DECISIONS_REQUIRED");
  const allowed = new Set(block.itemIds.map(entry => `${entry.receiptId}:${entry.receiptItemId}`));
  const seen = new Set();
  for (const decision of decisions) {
    const key = `${decision.receiptId}:${decision.receiptItemId}`;
    if (!allowed.has(key)) throw new Error("PRODUCT_ITEM_NOT_IN_BLOCK");
    if (seen.has(key)) throw new Error("DUPLICATE_PRODUCT_DECISION");
    seen.add(key);
  }
}

function resultFor(block, decision) {
  return block.results.find(result => result.receiptId === decision.receiptId
    && result.receiptItemId === decision.receiptItemId);
}

function upsertResult(block, result) {
  const index = block.results.findIndex(existing => existing.receiptId === result.receiptId
    && existing.receiptItemId === result.receiptItemId);
  if (index >= 0) block.results[index] = result;
  else block.results.push(result);
}

async function applyNonManual(client, decision) {
  if (decision.action === "NO_PRODUCT") {
    return client.markNoProduct(decision.receiptItemId);
  }
  const payload = {
    expected: {
      productFamilyId: decision.expected.familyId ?? null,
      productVariantId: decision.expected.variantId ?? null,
      source: decision.expected.source ?? null,
      status: decision.expected.status ?? null,
    },
    productFamilyId: decision.action === "CREATE_FAMILY" ? null : decision.familyId,
    newProductFamilyName: decision.action === "CREATE_FAMILY" ? decision.newFamilyName : null,
    productVariantId: decision.action === "ASSIGN_EXISTING" ? decision.variantId ?? null : null,
    newProductVariant: ["CREATE_FAMILY", "CREATE_VARIANT"].includes(decision.action)
      ? decision.newVariant ?? null
      : null,
    confidence: decision.confidence,
    reasonCode: decision.reasonCode,
  };
  return client.auditCorrect(decision.receiptItemId, payload);
}

async function applyConfirmedManual(client, decision) {
  if (decision.action === "NO_PRODUCT") return client.markNoProduct(decision.receiptItemId);
  if (decision.action === "ASSIGN_EXISTING") {
    return client.manualCorrect(decision.receiptItemId, {
      productFamilyId: decision.familyId,
      newProductFamilyName: null,
      productVariantId: decision.variantId ?? null,
      applyToSameStoreDescription: false,
    });
  }
  if (decision.action === "CREATE_VARIANT") {
    const variant = await client.createVariant({
      productFamilyId: decision.familyId,
      ...decision.newVariant,
      isActive: true,
    });
    return client.manualCorrect(decision.receiptItemId, {
      productFamilyId: decision.familyId,
      newProductFamilyName: null,
      productVariantId: variant.id,
      applyToSameStoreDescription: false,
    });
  }
  const assigned = await client.manualCorrect(decision.receiptItemId, {
    productFamilyId: null,
    newProductFamilyName: decision.newFamilyName,
    productVariantId: null,
    applyToSameStoreDescription: false,
  });
  if (!decision.newVariant) return assigned;
  const familyId = assigned.currentProductFamilyId;
  if (!Number.isInteger(familyId)) throw new Error("MANUAL_FAMILY_ID_MISSING");
  const variant = await client.createVariant({ productFamilyId: familyId, ...decision.newVariant, isActive: true });
  return client.manualCorrect(decision.receiptItemId, {
    productFamilyId: familyId,
    newProductFamilyName: null,
    productVariantId: variant.id,
    applyToSameStoreDescription: false,
  });
}

async function finishBlockIfCovered(stateDir, state, block) {
  const terminal = new Set(["APPLIED", ...OPEN_STATUSES]);
  const covered = new Set(block.results.filter(result => terminal.has(result.status))
    .map(result => `${result.receiptId}:${result.receiptItemId}`));
  const complete = block.itemIds.every(entry => covered.has(`${entry.receiptId}:${entry.receiptItemId}`));
  if (!complete) return false;
  block.status = "COMPLETED";
  const activeBatchPath = state.activeBatchPath;
  state.activeBatchPath = null;
  await writeProductStateAtomic(stateDir, state);
  if (activeBatchPath) await removeProductBatch(stateDir, activeBatchPath);
  return true;
}

export async function applyProductDecisions({ stateDir, productState, decisionDocument, client }) {
  const state = productState ?? await readProductState(stateDir);
  const block = findBlock(state, decisionDocument);
  assertDecisionTargetsBlock(block, decisionDocument.decisions);
  const families = (await client.listFamilies()).filter(family => family.isActive !== false);
  const variants = (await client.listVariants()).filter(variant => variant.isActive !== false);

  for (const decision of decisionDocument.decisions) {
    if (resultFor(block, decision)?.status === "APPLIED") continue;
    const receipt = await client.getReceipt(decision.receiptId);
    const currentItem = (receipt.items ?? []).find(entry => entry.id === decision.receiptItemId);
    if (!currentItem) throw new Error("PRODUCT_ITEM_NOT_FOUND");
    validateProductDecision(decision, currentItem, families, variants);
    try {
      if (isProtected(currentItem)) await applyConfirmedManual(client, decision);
      else await applyNonManual(client, decision);
    } catch {
      upsertResult(block, {
        receiptId: decision.receiptId,
        receiptItemId: decision.receiptItemId,
        status: "APPLY_FAILED",
        reasonCode: decision.reasonCode,
      });
      state.updatedAt = new Date().toISOString();
      await writeProductStateAtomic(stateDir, state);
      throw new Error("PRODUCT_APPLY_FAILED");
    }
    upsertResult(block, {
      receiptId: decision.receiptId,
      receiptItemId: decision.receiptItemId,
      status: "APPLIED",
      reasonCode: decision.reasonCode,
    });
    state.updatedAt = new Date().toISOString();
    await writeProductStateAtomic(stateDir, state);
  }
  await finishBlockIfCovered(stateDir, state, block);
  return state;
}

export async function recordOpenProductDecisions({ stateDir, productState, decisionDocument }) {
  const state = productState ?? await readProductState(stateDir);
  const block = findBlock(state, decisionDocument);
  assertDecisionTargetsBlock(block, decisionDocument.decisions);
  for (const decision of decisionDocument.decisions) {
    if (!OPEN_STATUSES.has(decision.status)) throw new Error("INVALID_OPEN_PRODUCT_STATUS");
    assertReasonCode(decision.reasonCode);
    upsertResult(block, {
      receiptId: decision.receiptId,
      receiptItemId: decision.receiptItemId,
      status: decision.status,
      confidence: decision.confidence ?? null,
      reasonCode: decision.reasonCode,
      proposedFamilyId: decision.proposedFamilyId ?? null,
      proposedVariantId: decision.proposedVariantId ?? null,
    });
  }
  state.updatedAt = new Date().toISOString();
  await writeProductStateAtomic(stateDir, state);
  await finishBlockIfCovered(stateDir, state, block);
  return state;
}

async function removeProductBatch(stateDir, batchPath) {
  const temporaryRoot = resolve(stateDir, "tmp");
  const resolvedBatch = resolve(batchPath);
  if (!resolvedBatch.startsWith(`${temporaryRoot}${sep}`) || !basename(resolvedBatch).startsWith("product-")) {
    throw new Error("UNSAFE_PRODUCT_BATCH_PATH");
  }
  await rm(resolvedBatch, { force: true });
}

export async function cleanupProductTemporaryFiles(stateDir) {
  const temporaryRoot = resolve(stateDir, "tmp");
  await mkdir(temporaryRoot, { recursive: true });
  let removed = 0;
  for (const entry of await readdir(temporaryRoot, { withFileTypes: true })) {
    if (!entry.name.startsWith("product-")) continue;
    const candidate = resolve(temporaryRoot, entry.name);
    if (!candidate.startsWith(`${temporaryRoot}${sep}`)) throw new Error("UNSAFE_PRODUCT_TEMP_ENTRY");
    await rm(candidate, { recursive: true, force: true });
    removed += 1;
  }
  return removed;
}

async function readProductState(stateDir) {
  return JSON.parse(await readFile(join(stateDir, PRODUCT_STATE_FILE), "utf8"));
}

async function readProductStateOrCreate(stateDir, receiptState) {
  try {
    const state = await readProductState(stateDir);
    if (state.sourceReceiptRunId !== receiptState.runId) throw new Error("RECEIPT_RUN_MISMATCH");
    return state;
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
    return buildProductQueue(receiptState);
  }
}

function requiredEnv(env, key) {
  if (!env[key]) throw new Error(`MISSING_${key}`);
  return env[key];
}

function createEbonClient(env, fetchImpl) {
  const baseUrl = requiredEnv(env, "EBON_BASE_URL");
  const token = requiredEnv(env, "APP_API_TOKEN");
  const origin = new URL(baseUrl).origin;
  const request = async (path, options = {}) => {
    const url = new URL(path, baseUrl).href;
    if (new URL(url).origin !== origin) throw new Error("EBON_ORIGIN_MISMATCH");
    const response = await fetchImpl(url, {
      ...options,
      method: options.method ?? "GET",
      headers: { Authorization: `Bearer ${token}`, "content-type": "application/json" },
    });
    if (!response.ok) throw new Error(`EBON_HTTP_${response.status}`);
    if (response.status === 204) return null;
    return response.json();
  };
  const post = (path, body = {}) => request(path, { method: "POST", body: JSON.stringify(body) });
  return {
    getReceipt: id => request(`/api/receipts/${id}`),
    listFamilies: () => request("/api/products/families"),
    listVariants: () => request("/api/products/variants"),
    auditCorrect: (id, body) => post(`/api/products/review/${id}/audit-correct`, body),
    manualCorrect: (id, body) => post(`/api/products/review/${id}/correct`, body),
    markNoProduct: id => post(`/api/products/review/${id}/no-product`),
    createVariant: body => post("/api/products/variants", body),
  };
}

function parseArgs(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 2) {
    if (!argv[index]?.startsWith("--") || argv[index + 1] == null) throw new Error("INVALID_ARGUMENTS");
    args[argv[index].slice(2)] = argv[index + 1];
  }
  return args;
}

function requiredArg(args, key) {
  if (!args[key]) throw new Error(`MISSING_ARGUMENT_${key.toUpperCase()}`);
  return args[key];
}

export async function main(argv, env, fetchImpl = fetch) {
  const [command, ...rest] = argv;
  if (!new Set(["next", "apply", "record-open", "cleanup"]).has(command)) throw new Error("UNKNOWN_COMMAND");
  const args = parseArgs(rest);
  const stateDir = resolve(args.state ?? "var/ebon-codex-audit");
  if (command === "cleanup") return { removed: await cleanupProductTemporaryFiles(stateDir) };
  const client = createEbonClient(env, fetchImpl);
  if (command === "next") {
    const receiptState = JSON.parse(await readFile(join(stateDir, "progress.json"), "utf8"));
    const productState = await readProductStateOrCreate(stateDir, receiptState);
    return prepareProductBlock({ stateDir, receiptState, productState, client });
  }
  const decisionDocument = JSON.parse(await readFile(requiredArg(args, "decisions"), "utf8"));
  if (command === "apply") {
    const state = await applyProductDecisions({ stateDir, decisionDocument, client });
    return { applied: decisionDocument.decisions.length, blockCompleted: findBlock(state, decisionDocument).status === "COMPLETED" };
  }
  const state = await recordOpenProductDecisions({ stateDir, decisionDocument });
  return { recorded: decisionDocument.decisions.length, blockCompleted: findBlock(state, decisionDocument).status === "COMPLETED" };
}

export function failWithoutPrivateData(error) {
  process.stderr.write(`${error instanceof Error ? error.message : "PRODUCT_AUDIT_FAILED"}\n`);
  process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2), process.env)
    .then(result => process.stdout.write(`${JSON.stringify(result)}\n`))
    .catch(failWithoutPrivateData);
}
