import { Browser, Builder, By, until } from "selenium-webdriver";
import chrome from "selenium-webdriver/chrome.js";
import { createRequire } from "node:module";
import { existsSync } from "node:fs";

const baseUrl = process.env.EBON_E2E_BASE_URL ?? "http://127.0.0.1:5173";
const require = createRequire(import.meta.url);
const chromedriver = require("chromedriver");

const driver = await createDriver();

try {
  await driver.get(baseUrl);
  await waitForText("API-Token erforderlich");
  await waitForText("Dashboard");
  await waitForText("Einstellungen");

  const tokenInput = await driver.findElement(By.id("api-token"));
  await tokenInput.sendKeys("mock-token");
  await waitForText("Sync starten");
  await waitForText("Letzte Bons");

  await clickNav("#/settings");
  await waitForText("Allgemein");
  await waitForText("Software-Version");

  await clickButton("Backup");
  await waitForText("Backup herunterladen");
  await waitForText("Dry-Run prüfen");
  await waitForText("Restore-Bestätigung");

  await clickNav("#/search");
  await waitForText("Suchergebnisse");
  await waitForText("Bio Milch");

  await clickNav("#/receipts");
  await waitForText("Bons");
  await waitForText("REWE");
} finally {
  await driver.quit();
}

async function clickNav(href) {
  const element = await driver.wait(until.elementLocated(By.css(`a[href="${href}"]`)), 10_000);
  await element.click();
}

async function clickButton(text) {
  const element = await driver.wait(until.elementLocated(By.xpath(`//button[normalize-space(.)='${text}']`)), 10_000);
  await element.click();
}

async function waitForText(text) {
  await driver.wait(
    until.elementLocated(By.xpath(`//*[contains(normalize-space(.), ${xpathLiteral(text)})]`)),
    10_000
  );
}

function xpathLiteral(value) {
  if (!value.includes("'")) {
    return `'${value}'`;
  }
  return `concat('${value.replaceAll("'", "',\"'\",'")}')`;
}

function locateBrowserBinary() {
  const configured = process.env.EBON_E2E_BROWSER_BINARY ?? process.env.CHROME_BIN;
  const chromeCandidates = process.platform === "win32"
    ? [
        configured,
        "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
        "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
      ]
    : [
        configured,
        process.env.GOOGLE_CHROME_BIN,
        "/usr/bin/google-chrome",
        "/usr/bin/google-chrome-stable",
        "/usr/bin/chromium",
        "/usr/bin/chromium-browser"
      ];
  const chromeBinary = chromeCandidates.filter(Boolean).find((candidate) => existsSync(candidate));
  if (chromeBinary) {
    return { type: "chrome", path: chromeBinary };
  }

  const edgeCandidates = process.platform === "win32"
    ? [
        process.env.EDGE_BINARY_PATH,
        "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
        "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
      ]
    : [
        process.env.EDGE_BINARY_PATH,
        "/usr/bin/microsoft-edge",
        "/usr/bin/microsoft-edge-stable"
      ];
  const edgeBinary = edgeCandidates.filter(Boolean).find((candidate) => existsSync(candidate));
  return edgeBinary ? { type: "edge", path: edgeBinary } : { type: "chrome", path: undefined };
}

async function createDriver() {
  const browserBinary = locateBrowserBinary();
  const args = [
    "--headless=new",
    "--no-sandbox",
    "--disable-dev-shm-usage",
    "--window-size=1440,1000"
  ];

  if (browserBinary.type === "edge") {
    const edge = await import("selenium-webdriver/edge.js");
    const edgedriver = await import("edgedriver");
    const edgeDriverPath = process.env.EDGEDRIVER_PATH ?? await edgedriver.download();
    const options = new edge.Options()
      .addArguments(...args)
      .setEdgeChromiumBinaryPath(browserBinary.path);
    return new Builder()
      .forBrowser(Browser.EDGE)
      .setEdgeOptions(options)
      .setEdgeService(new edge.ServiceBuilder(edgeDriverPath))
      .build();
  }

  const options = new chrome.Options().addArguments(...args);
  if (browserBinary.path) {
    options.setChromeBinaryPath(browserBinary.path);
  }
  return new Builder()
    .forBrowser(Browser.CHROME)
    .setChromeOptions(options)
    .setChromeService(new chrome.ServiceBuilder(chromedriver.path))
    .build();
}
