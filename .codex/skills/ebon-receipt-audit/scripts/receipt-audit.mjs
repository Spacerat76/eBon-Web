import { mkdir, readFile, readdir, realpath, rename, rm, writeFile } from "node:fs/promises";
import { dirname, join, resolve, sep } from "node:path";
import { pathToFileURL } from "node:url";

const STATE_FILE = "progress.json";
const PRIVATE_KEYS = /^(rawText|content|description|ocrText|prompt|response|token|authorization|bytes|original)$/i;
const RECEIPT_STATUSES = new Set(["VERIFIED", "NEEDS_USER", "NO_SENSIBLE_PROPOSAL"]);

export function assertPaperlessMethod(method) {
  if (String(method).toUpperCase() !== "GET") throw new Error("PAPERLESS_GET_ONLY");
}

function assertOrigin(url, allowedOrigin, code = "CROSS_ORIGIN_NEXT") {
  if (new URL(url).origin !== new URL(allowedOrigin).origin) throw new Error(code);
}

async function responseJson(response, code) {
  if (!response.ok) throw new Error(`${code}_${response.status}`);
  return response.json();
}

export async function fetchAllPages({ firstUrl, fetchImpl, headers = {}, allowedOrigin, maxPages = 1000 }) {
  const results = [];
  let next = new URL(firstUrl).href;
  for (let pageNumber = 0; next; pageNumber += 1) {
    if (pageNumber >= maxPages) throw new Error("PAPERLESS_PAGE_LIMIT");
    assertOrigin(next, allowedOrigin);
    assertPaperlessMethod("GET");
    const page = await responseJson(await fetchImpl(next, { method: "GET", headers }), "PAPERLESS_HTTP");
    if (!Array.isArray(page.results)) throw new Error("PAPERLESS_INVALID_PAGE");
    results.push(...page.results);
    if (results.length > 100_000) throw new Error("PAPERLESS_DOCUMENT_LIMIT");
    next = page.next ? new URL(page.next, next).href : null;
  }
  return results;
}

function normalizeKey(value, fallback) {
  const key = String(value ?? "")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
  return key || fallback;
}

function stableCountSort(first, second) {
  return second.receiptCount - first.receiptCount || first.merchantKey?.localeCompare(second.merchantKey)
    || first.branchKey?.localeCompare(second.branchKey);
}

export function buildReceiptQueue(paperlessDocuments, ebonReceipts) {
  const receiptByDocument = new Map(ebonReceipts
    .filter(receipt => Number.isInteger(receipt.paperlessDocumentId))
    .map(receipt => [receipt.paperlessDocumentId, receipt]));
  const merchantMap = new Map();
  const unmatchedPaperlessDocumentIds = [];

  for (const document of paperlessDocuments) {
    const receipt = receiptByDocument.get(document.id);
    const merchantKey = receipt ? normalizeKey(receipt.storeName, "unknown-merchant") : "unmatched-paperless";
    const branchKey = receipt ? normalizeKey(receipt.storeBranch, "unknown-branch") : "unmatched";
    if (!receipt) unmatchedPaperlessDocumentIds.push(document.id);
    if (!merchantMap.has(merchantKey)) {
      merchantMap.set(merchantKey, {
        merchantKey,
        merchantName: receipt?.storeName ?? "UNMATCHED_PAPERLESS",
        receiptCount: 0,
        branches: new Map(),
      });
    }
    const merchant = merchantMap.get(merchantKey);
    if (!merchant.branches.has(branchKey)) {
      merchant.branches.set(branchKey, {
        branchKey,
        branchName: receipt?.storeBranch ?? null,
        receiptCount: 0,
        status: "PENDING",
        documents: [],
      });
    }
    const branch = merchant.branches.get(branchKey);
    branch.documents.push({
      paperlessDocumentId: document.id,
      receiptId: receipt?.id ?? null,
      status: "PENDING",
      reasonCodes: [],
    });
    branch.receiptCount += 1;
    merchant.receiptCount += 1;
  }

  const merchants = [...merchantMap.values()].map(merchant => ({
    ...merchant,
    branches: [...merchant.branches.values()]
      .map(branch => ({ ...branch, documents: branch.documents.sort((a, b) => a.paperlessDocumentId - b.paperlessDocumentId) }))
      .sort((a, b) => b.receiptCount - a.receiptCount || a.branchKey.localeCompare(b.branchKey)),
  })).sort(stableCountSort);

  return { merchants, unmatchedPaperlessDocumentIds: unmatchedPaperlessDocumentIds.sort((a, b) => a - b) };
}

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

export async function writeStateAtomic(stateDir, state) {
  assertPersistentStateSafe(state);
  await mkdir(stateDir, { recursive: true });
  const target = join(stateDir, STATE_FILE);
  const temporary = `${target}.tmp-${process.pid}`;
  await writeFile(temporary, `${JSON.stringify(state, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  await rename(temporary, target);
}

async function readState(stateDir) {
  return JSON.parse(await readFile(join(stateDir, STATE_FILE), "utf8"));
}

function nextBranch(state) {
  for (const merchant of state.merchants ?? []) {
    const branch = merchant.branches.find(candidate => candidate.status !== "COMPLETED");
    if (branch) return { merchant, branch };
  }
  return null;
}

function isExplicitlyReparseProtected(receipt) {
  return receipt?.parseStatus === "MANUALLY_EDITED"
    || receipt?.items?.some(item => item.isManuallyEdited
      || item.productAssignmentStatus === "REJECTED"
      || item.productAssignmentStatus === "NO_PRODUCT");
}

async function reparseForAudit(ebon, receiptId, options) {
  try {
    return await ebon.reparseReceipt(receiptId, options);
  } catch (error) {
    if (error instanceof Error && error.message === "EBON_HTTP_409") {
      const current = await ebon.getReceipt(receiptId);
      if (isExplicitlyReparseProtected(current)) return current;
    }
    throw error;
  }
}

export async function prepareReceiptBlock({ stateDir, state, clients }) {
  const selection = nextBranch(state);
  if (!selection) return { batchPath: null, completed: true };
  const { merchant, branch } = selection;
  const receipts = [];
  for (const documentState of branch.documents) {
    const paperless = await clients.paperless.getDocument(documentState.paperlessDocumentId);
    if (documentState.receiptId == null) {
      receipts.push({ paperless, receipt: null, parseTrace: [], reasonCode: "UNMATCHED_PAPERLESS" });
      continue;
    }
    const receipt = await reparseForAudit(clients.ebon, documentState.receiptId, {
      overwriteManualEdits: false,
      useAiFallback: false,
      rawTextSource: "PAPERLESS",
    });
    const parseTrace = await clients.ebon.getParseTrace(documentState.receiptId);
    receipts.push({ paperless, receipt, parseTrace });
  }

  const temporaryDirectory = join(stateDir, "tmp");
  await mkdir(temporaryDirectory, { recursive: true });
  const batchPath = join(temporaryDirectory, `receipt-${merchant.merchantKey}-${branch.branchKey}.json`);
  await writeFile(batchPath, `${JSON.stringify({
    runId: state.runId,
    merchantKey: merchant.merchantKey,
    branchKey: branch.branchKey,
    receipts,
  }, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
  branch.status = "IN_PROGRESS";
  state.activeBatchPath = batchPath;
  await writeStateAtomic(stateDir, state);
  return { batchPath, completed: false, receiptCount: receipts.length };
}

function sameDecision(existing, decision) {
  return existing.status === decision.status
    && JSON.stringify(existing.reasonCodes ?? []) === JSON.stringify(decision.reasonCodes ?? []);
}

export async function recordReceiptDecisions({ stateDir, decisions }) {
  const state = await readState(stateDir);
  if (state.runId !== decisions.runId) throw new Error("RUN_ID_MISMATCH");
  const merchant = state.merchants.find(entry => entry.merchantKey === decisions.merchantKey);
  const branch = merchant?.branches.find(entry => entry.branchKey === decisions.branchKey);
  if (!branch) throw new Error("BLOCK_NOT_FOUND");
  if (!Array.isArray(decisions.receipts) || decisions.receipts.length !== branch.documents.length) {
    throw new Error("INCOMPLETE_DECISIONS");
  }
  const decisionKeys = new Set(decisions.receipts.map(entry => `${entry.paperlessDocumentId}:${entry.receiptId ?? "null"}`));
  const documentKeys = new Set(branch.documents.map(entry => `${entry.paperlessDocumentId}:${entry.receiptId ?? "null"}`));
  if (decisionKeys.size !== documentKeys.size || [...documentKeys].some(key => !decisionKeys.has(key))) {
    throw new Error("INCOMPLETE_DECISIONS");
  }
  const activeBatchPath = state.activeBatchPath;
  if (activeBatchPath) {
    const temporaryRoot = resolve(stateDir, "tmp");
    const resolvedBatch = resolve(activeBatchPath);
    if (!resolvedBatch.startsWith(`${temporaryRoot}${sep}`)) throw new Error("UNSAFE_BATCH_PATH");
  }
  for (const decision of decisions.receipts) {
    if (!RECEIPT_STATUSES.has(decision.status)) throw new Error("INVALID_RECEIPT_STATUS");
    const documentState = branch.documents.find(entry => entry.paperlessDocumentId === decision.paperlessDocumentId
      && entry.receiptId === decision.receiptId);
    if (!documentState) throw new Error("RECEIPT_NOT_IN_BLOCK");
    if (documentState.status !== "PENDING" && !sameDecision(documentState, decision)) {
      throw new Error("DECISION_CONFLICT");
    }
    documentState.status = decision.status;
    documentState.reasonCodes = [...new Set(decision.reasonCodes ?? [])].sort();
  }
  branch.status = "COMPLETED";
  state.activeBatchPath = null;
  state.updatedAt = new Date().toISOString();
  await writeStateAtomic(stateDir, state);
  if (activeBatchPath) await rm(activeBatchPath, { force: true });
  return state;
}

export async function cleanupTemporaryFiles(stateDir) {
  const stateRoot = resolve(stateDir);
  const temporaryRoot = resolve(stateRoot, "tmp");
  if (temporaryRoot !== join(stateRoot, "tmp") || !temporaryRoot.startsWith(`${stateRoot}${sep}`)) {
    throw new Error("UNSAFE_TEMP_PATH");
  }
  await mkdir(temporaryRoot, { recursive: true });
  const resolvedRoot = await realpath(temporaryRoot);
  let removed = 0;
  for (const entry of await readdir(resolvedRoot, { withFileTypes: true })) {
    const candidate = resolve(resolvedRoot, entry.name);
    if (!candidate.startsWith(`${resolvedRoot}${sep}`)) throw new Error("UNSAFE_TEMP_ENTRY");
    await rm(candidate, { recursive: true, force: true });
    removed += 1;
  }
  return removed;
}

function requiredEnv(env, key) {
  const value = env[key];
  if (!value) throw new Error(`MISSING_${key}`);
  return value;
}

function createPaperlessClient(env, fetchImpl) {
  const baseUrl = requiredEnv(env, "PAPERLESS_BASE_URL");
  const token = requiredEnv(env, "PAPERLESS_API_TOKEN");
  const tag = requiredEnv(env, "PAPERLESS_EBON_TAG");
  const origin = new URL(baseUrl).origin;
  const headers = { Authorization: `Token ${token}` };
  return {
    listDocuments: () => fetchAllPages({
      firstUrl: new URL(`/api/documents/?tags__name__iexact=${encodeURIComponent(tag)}&page_size=100`, baseUrl).href,
      fetchImpl,
      headers,
      allowedOrigin: origin,
    }),
    async getDocument(id) {
      const url = new URL(`/api/documents/${id}/`, baseUrl).href;
      assertOrigin(url, origin, "PAPERLESS_ORIGIN_MISMATCH");
      return responseJson(await fetchImpl(url, { method: "GET", headers }), "PAPERLESS_HTTP");
    },
  };
}

function createEbonClient(env, fetchImpl) {
  const baseUrl = requiredEnv(env, "EBON_BASE_URL");
  const token = requiredEnv(env, "APP_API_TOKEN");
  const origin = new URL(baseUrl).origin;
  const headers = { Authorization: `Bearer ${token}`, "content-type": "application/json" };
  const request = async (path, options = {}) => {
    const url = new URL(path, baseUrl).href;
    assertOrigin(url, origin, "EBON_ORIGIN_MISMATCH");
    return responseJson(await fetchImpl(url, { ...options, method: options.method ?? "GET", headers }), "EBON_HTTP");
  };
  return {
    async listReceipts() {
      const receipts = [];
      for (let page = 0; page < 1000; page += 1) {
        const response = await request(`/api/receipts?page=${page}&size=100&sortBy=id&sortDir=asc`);
        if (!Array.isArray(response.content)) throw new Error("EBON_INVALID_PAGE");
        receipts.push(...response.content);
        if (page + 1 >= response.totalPages) return receipts;
      }
      throw new Error("EBON_PAGE_LIMIT");
    },
    getReceipt: id => request(`/api/receipts/${id}`),
    reparseReceipt(id, options) {
      const query = new URLSearchParams({
        overwriteManualEdits: String(options.overwriteManualEdits),
        useAiFallback: String(options.useAiFallback),
        confirmFullText: "false",
        rawTextSource: options.rawTextSource,
      });
      return request(`/api/receipts/${id}/reparse?${query}`, { method: "POST" });
    },
    getParseTrace: id => request(`/api/receipts/${id}/parse-trace`),
  };
}

export function parseArgs(argv) {
  const args = {};
  for (let index = 0; index < argv.length; index += 2) {
    const key = argv[index];
    if (!key?.startsWith("--") || argv[index + 1] == null) throw new Error("INVALID_ARGUMENTS");
    args[key.slice(2)] = argv[index + 1];
  }
  return args;
}

export async function main(argv, env, fetchImpl = fetch) {
  const [command, ...rest] = argv;
  if (!new Set(["inventory", "next", "record", "cleanup"]).has(command)) throw new Error("UNKNOWN_COMMAND");
  const args = parseArgs(rest);
  const stateDir = resolve(args.state ?? "var/ebon-codex-audit");
  if (command === "cleanup") return { removed: await cleanupTemporaryFiles(stateDir) };
  if (command === "record") {
    const decisions = JSON.parse(await readFile(requiredArg(args, "decisions"), "utf8"));
    await recordReceiptDecisions({ stateDir, decisions });
    return { recorded: decisions.receipts.length };
  }
  const clients = { paperless: createPaperlessClient(env, fetchImpl), ebon: createEbonClient(env, fetchImpl) };
  if (command === "inventory") {
    const documents = await clients.paperless.listDocuments();
    const receipts = await clients.ebon.listReceipts();
    const queue = buildReceiptQueue(documents, receipts);
    const state = {
      version: 1,
      runId: `receipt-${new Date().toISOString().replace(/[-:.]/g, "")}`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      activeBatchPath: null,
      ...queue,
    };
    await writeStateAtomic(stateDir, state);
    return { merchantCount: state.merchants.length, documentCount: documents.length, unmatchedCount: queue.unmatchedPaperlessDocumentIds.length };
  }
  return prepareReceiptBlock({ stateDir, state: await readState(stateDir), clients });
}

function requiredArg(args, key) {
  if (!args[key]) throw new Error(`MISSING_ARGUMENT_${key.toUpperCase()}`);
  return args[key];
}

export function failWithoutPrivateData(error) {
  process.stderr.write(`${error instanceof Error ? error.message : "AUDIT_FAILED"}\n`);
  process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2), process.env)
    .then(result => process.stdout.write(`${JSON.stringify(result)}\n`))
    .catch(failWithoutPrivateData);
}
