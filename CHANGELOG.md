# Changelog

All notable changes to Keeply are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
Keeply uses [semantic versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0]

First release.

### Keeping receipts

- Import a photo, a scan or a PDF by dropping it on the window or choosing a
  file. The original is copied and never modified.
- A PDF that carries its own text is read directly and never sent through text
  recognition.
- Photographed receipts are found in the frame and flattened before reading.
- Text recognition runs on the machine, with the engine and English language
  model shipped inside the application.
- Fields are extracted by rule: shop, purchase date, total, tax, receipt number
  and line items. Each carries a confidence and the text it was read from.
- Every import is reviewed before anything is saved. Fields Keeply was unsure of
  say so; fields it could not read stay empty.
- Likely duplicates are flagged and never removed automatically.

### Returns and warranties

- Return deadlines from a number of days, a date printed on the receipt, a rule
  saved for a shop, or a correction, with the source shown beside the deadline.
- Warranties as a duration, an end date, lifetime or unknown, distinguishing
  what a document said from what a person entered from what Keeply suggested.
- Local reminders before a return window closes or a warranty ends.

### Finding things

- Full-text search across product names, shops, notes, tags, serial numbers and
  the text read off receipts.
- A small search vocabulary: `returnable`, `warranty`, a year, `under 100`,
  `this month`.
- Filters for return status, warranty status and archived purchases.

### Your data

- Backup and restore of everything, including the receipt files.
- Export as CSV or JSON.
- A storage screen that offers to delete only what Keeply can rebuild.
- A privacy page describing exactly what the application does, enforced by a
  build step that fails if any module outside the optional local-AI one gains
  network access.

### Optional

- Local assistance through Ollama, off by default, never on the correctness
  path, and refused unless the endpoint is on the same machine.
- Demo data, clearly marked and removable in one action.

### Known limitations

- English only. The shipped language model covers English; a receipt in another
  language will not be read.
- Accuracy is measured against generated receipts, which are cleaner and more
  consistent than a crumpled thermal receipt photographed in a dim kitchen.
- macOS is the tested platform. The test suite runs on Linux in CI, but Keeply
  has not been tested as a packaged application on Linux or Windows.
- The application is not signed or notarised, so macOS refuses to open it on a
  plain double-click. See the README.
- Receipts are not encrypted at rest. Use FileVault or the equivalent if you need
  that.

[1.0.0]: https://github.com/mateoosoriodelhonte/keeply/releases/tag/v1.0.0
