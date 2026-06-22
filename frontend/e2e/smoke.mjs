import { Browser, Builder, By, until } from "selenium-webdriver";
import chrome from "selenium-webdriver/chrome.js";
import { createRequire } from "node:module";
import { existsSync } from "node:fs";

import { writeBrowserCoverage } from "./coverage-output.mjs";

const require = createRequire(import.meta.url);
const chromedriver = require("chromedriver");

export async function runSmoke({
  baseUrl = process.env.EBON_E2E_BASE_URL ?? "http://127.0.0.1:5173",
  coverageOutputFile
} = {}) {
  const driver = await createDriver();

  try {
    await driver.get(baseUrl);
    await waitForText(driver, "API-Token erforderlich");
    await waitForText(driver, "Dashboard");
    await waitForText(driver, "Einstellungen");

    const tokenInput = await driver.findElement(By.id("api-token"));
    await tokenInput.sendKeys("mock-token");
    await waitForText(driver, "Sync starten");
    await waitForText(driver, "Letzte Bons");

    await clickNav(driver, "#/settings");
    await waitForText(driver, "Allgemein");
    await waitForText(driver, "Software-Version");

    await clickButton(driver, "Backup");
    await waitForText(driver, "Backup herunterladen");
    await waitForText(driver, "Dry-Run prüfen");
    await waitForText(driver, "Restore-Bestätigung");

    await clickNav(driver, "#/search");
    await waitForText(driver, "Suchergebnisse");
    await waitForText(driver, "Bio Milch");

    await clickNav(driver, "#/receipts");
    await waitForText(driver, "Bons");
    await waitForText(driver, "REWE");

    // The mock receipt id is stable, so navigate directly to the detail route.
    // Clicking a table row is unreliable in headless Edge because the row itself
    // is not the interactive element that receives the application navigation.
    await driver.get(`${baseUrl}/#/receipts/1`);
    await waitForText(driver, "Erneut parsen");
    await clickButton(driver, "Erneut parsen");
    await waitForText(driver, "Paperless-Rohtext wurde seit dem Import geändert.");
    await clickButton(driver, "Gespeicherten Rohtext verwenden");
    await waitForText(driver, "Bon wurde erneut geparst.");

    if (coverageOutputFile) {
      await writeBrowserCoverage(driver, coverageOutputFile);
    }
  } finally {
    await driver.quit();
  }
}

async function clickNav(driver, href) {
  const element = await driver.wait(until.elementLocated(By.css(`a[href="${href}"]`)), 10_000);
  await element.click();
}

async function clickButton(driver, text) {
  const element = await driver.wait(until.elementLocated(By.xpath(`//button[normalize-space(.)='${text}']`)), 10_000);
  await element.click();
}

async function waitForText(driver, text) {
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
