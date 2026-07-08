import fs from "node:fs/promises";
import { normalizeLimits } from "./limits.mjs";

const file = process.argv[2];
if (!file) {
  console.error("Usage: node src/validate-limits.mjs <limits.json>");
  process.exit(2);
}

try {
  const json = JSON.parse(await fs.readFile(file, "utf8"));
  console.log(JSON.stringify(normalizeLimits(json), null, 2));
} catch (error) {
  console.error(error.message || String(error));
  process.exit(1);
}
