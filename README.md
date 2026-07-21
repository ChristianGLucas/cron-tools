# cron-tools

Composable **crontab-scheduling** nodes for the [Axiom](https://axiomide.com)
marketplace, published as `christiangeorgelucas/cron-tools`. Validate cron
expressions, compute next/previous run times, test whether a timestamp is due,
describe a cron in plain English, break it down field by field, and migrate a
cron between dialects — entirely offline and deterministically.

Written in **Java**, wrapping one battle-tested, permissively-licensed library:

| Concern | Library | License |
|---|---|---|
| Crontab parsing, validation, execution-time math, descriptions, dialect migration | [`cron-utils`](https://github.com/jmrozanec/cron-utils) (jmrozanec) | Apache-2.0 |

Every node is **stateless**, **offline** (no network, no API keys, no signup),
and **deterministic**. The reference instant for every time-relative node
(`NextRunTimes`, `PreviousRunTimes`, `IsDue`) is **always a caller-supplied RFC
3339 timestamp** — this package never reads the wall clock, so the same input
always produces the same output.

Distinct from `christiangeorgelucas/recurrence-tools`, which handles RFC 5545
(iCalendar) `RRULE` syntax — a different format and domain. This package speaks
**crontab syntax**: UNIX (5-field), Quartz (6-7 field with seconds and an
optional year), Spring Framework 5.3+ (6-field with seconds), and cron4j
(5-field with `|` alternation).

## Nodes

| Node | What it does |
|---|---|
| `ValidateCron` | Validate a cron expression against a dialect; returns the canonical re-print or a structured error. |
| `DescribeCron` | Plain-English description of a cron expression, in any of cron-utils' 17 bundled locales. |
| `FieldBreakdown` | Decompose a cron expression into its individual fields (name, raw sub-expression, classification). |
| `NextRunTimes` | The next N run times strictly after a caller-supplied reference timestamp. |
| `PreviousRunTimes` | The previous N run times strictly before a caller-supplied reference timestamp. |
| `IsDue` | Whether a caller-supplied timestamp exactly matches an occurrence of a cron expression. |
| `MigrateCron` | Translate a cron expression from one dialect's field layout into another's. |

## Dialects

| Dialect | Fields | Day-of-week numbering | Notes |
|---|---|---|---|
| `UNIX` | 5 (no seconds) | 0=Sunday..6=Saturday (7 also valid for Sunday) | Classic `/etc/crontab` syntax |
| `QUARTZ` | 6-7 (seconds + optional year) | 1=Sunday..7=Saturday | Quartz Job Scheduler; requires `?` in exactly one of day-of-month/day-of-week |
| `SPRING53` | 6 (seconds) | 0=Sunday..6=Saturday | Spring Framework 5.3+ `CronExpression` |
| `CRON4J` | 5 (no seconds) | 0=Sunday..7=Sunday | cron4j syntax; no `?`/`L`/`W`/`#` support |

## Bounds

`NextRunTimes`/`PreviousRunTimes`' `count` is capped at 500 (rejected with a
structured `INVALID_ARGUMENT` error above that, never silently truncated). A
cron whose fields can never simultaneously match (e.g. day-of-month 31 in
February) returns `truncated=true` with whatever run times were found before
cron-utils' own bounded search (100 years / 100,000 iterations) gave up,
rather than hanging.

## Error contract

Every node returns a structured `CronError { code, message }` on malformed
input instead of throwing — `INVALID_CRON`, `INVALID_TIMESTAMP`, or
`INVALID_ARGUMENT`. `error.code` is empty on success.

---

Built for the Axiom marketplace. MIT licensed.
