import fs from "node:fs/promises";
import path from "node:path";

export const fallbackLimits = Object.freeze({
  five_hour_percent_left: 63,
  five_hour_resets_at: "2026-07-06T18:27:00+03:00",
  weekly_percent_left: 28,
  weekly_resets_at: "2026-07-10T14:20:00+03:00",
  updated_at: "2026-07-06T16:09:00+03:00"
});

export function dataDir() {
  return process.env.DATA_DIR || path.resolve("data");
}

export function limitsPath() {
  return path.join(dataDir(), "limits.json");
}

export function clampPercent(value) {
  const number = Number.parseInt(String(value), 10);
  if (Number.isNaN(number)) throw new Error(`Invalid percent: ${value}`);
  return Math.max(0, Math.min(100, number));
}

export function normalizeLimits(input) {
  const limits = {
    five_hour_percent_left: clampPercent(input.five_hour_percent_left),
    five_hour_resets_at: requireIsoInstant(input.five_hour_resets_at, "five_hour_resets_at"),
    weekly_percent_left: clampPercent(input.weekly_percent_left),
    weekly_resets_at: requireIsoInstant(input.weekly_resets_at, "weekly_resets_at"),
    updated_at: input.updated_at
      ? requireIsoInstant(input.updated_at, "updated_at")
      : new Date().toISOString()
  };
  return limits;
}

export async function readLimits() {
  try {
    const raw = await fs.readFile(limitsPath(), "utf8");
    return normalizeLimits(JSON.parse(raw));
  } catch {
    return fallbackLimits;
  }
}

export async function writeLimits(input) {
  const normalized = normalizeLimits(input);
  await fs.mkdir(dataDir(), { recursive: true });
  const target = limitsPath();
  const tmp = `${target}.tmp`;
  await fs.writeFile(tmp, `${JSON.stringify(normalized, null, 2)}\n`, "utf8");
  await fs.rename(tmp, target);
  return normalized;
}

export function requireIsoInstant(value, field) {
  if (typeof value !== "string" || value.trim() === "") {
    throw new Error(`${field} must be an ISO-8601 string`);
  }
  const timestamp = Date.parse(value);
  if (Number.isNaN(timestamp)) {
    throw new Error(`${field} is not a valid date`);
  }
  return value;
}
