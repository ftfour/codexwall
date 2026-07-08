import assert from "node:assert/strict";
import { normalizeCodexRateLimits } from "../src/codex-app-server-client.mjs";

const normalized = normalizeCodexRateLimits(
  {
    rateLimits: {
      rateLimitsByLimitId: {
        codex: {
          primary: {
            usedPercent: 37.2,
            windowDurationMins: 300,
            resetsAt: 1783416420
          },
          secondary: {
            usedPercent: 72,
            windowDurationMins: 10080,
            resetsAt: 1783750800
          }
        }
      }
    }
  },
  new Date("2026-07-07T00:00:00Z")
);

assert.equal(normalized.five_hour_percent_left, 63);
assert.equal(normalized.five_hour_resets_at, "2026-07-07T09:27:00.000Z");
assert.equal(normalized.weekly_percent_left, 28);
assert.equal(normalized.weekly_resets_at, "2026-07-11T06:20:00.000Z");
assert.equal(normalized.updated_at, "2026-07-07T00:00:00.000Z");

console.log(JSON.stringify(normalized));
