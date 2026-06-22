import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

export async function writeBrowserCoverage(driver, outputFile) {
  const coverage = await driver.executeScript("return window.__coverage__ || null;");

  if (!coverage || typeof coverage !== "object" || Object.keys(coverage).length === 0) {
    throw new Error("Istanbul browser coverage was not produced. Ensure the E2E coverage Vite flag is enabled.");
  }

  await mkdir(dirname(outputFile), { recursive: true });
  await writeFile(outputFile, JSON.stringify(coverage), "utf8");
}
