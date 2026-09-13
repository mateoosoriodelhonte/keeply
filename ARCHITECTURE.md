# Architecture

Keeply is a local-first desktop application. Everything it does happens on one
machine, and the architecture exists to keep that true while the product stays
pleasant to work on.

## The shape of it

```
                     a receipt, dropped on the window
                                  │
                                  ▼
     ┌────────────────────────────────────────────────────────┐
     │  :core:documents    safe import                        │
     │                     type from magic bytes, not the     │
     │                     extension; size and page limits    │
     │                     checked before anything decodes    │
     └────────────────────────────┬───────────────────────────┘
                                  │  original copied, never modified
                                  ▼
     ┌────────────────────────────────────────────────────────┐
     │  :core:imaging      find the page, flatten it          │
     │                     (skipped for a PDF with text)      │
     └────────────────────────────┬───────────────────────────┘
                                  ▼
     ┌────────────────────────────────────────────────────────┐
     │  :core:ocr          Tesseract, locally, bounded        │
     │                     (skipped for a PDF with text)      │
     └────────────────────────────┬───────────────────────────┘
                                  ▼
     ┌────────────────────────────────────────────────────────┐
     │  :core:extraction   merchant, date, total, tax, items  │
     │                     rules only, no model               │
     └────────────────────────────┬───────────────────────────┘
                                  ▼
                        a person checks it
                                  │
                                  ▼
     ┌────────────────────────────────────────────────────────┐
     │  :core:data         SQLite: purchases, merchants,      │
     │                     documents, derived text, FTS5      │
     └────────────────────────────┬───────────────────────────┘
                                  │
            ┌────────────┬────────┴────────┬──────────────┐
            ▼            ▼                 ▼              ▼
         search    return windows     warranties     reminders
                                                  (:core:reminders)

     :core:ai  ──  optional, off by default, outside all of this
```

## The modules, and why they are separate

| module | what it holds |
| --- | --- |
| `:core:domain` | The rules. Money, dates, return windows, warranties, duplicate detection, search parsing. Pure Kotlin: no I/O, no framework types, and no clock it cannot be handed. |
| `:core:data` | SQLite through SQLDelight. Typed queries, versioned migrations, FTS5. |
| `:core:documents` | Taking responsibility for an untrusted file, and reading PDFs. |
| `:core:imaging` | OpenCV. Finding a page in a photograph and flattening it. |
| `:core:ocr` | Tesseract, with bounded concurrency, progress and cancellation. |
| `:core:extraction` | Turning text into fields, with a rule and a line of evidence behind each one. |
| `:core:backup` | The archive format, and everything a restore refuses. CSV and JSON export. |
| `:core:reminders` | Local notifications. |
| `:core:ai` | The optional local model. The **only** module permitted to open a connection. |
| `:core:services` | The pieces wired together, and the composition root. |
| `:fixtures` | Generated receipts, used by the tests and by demo mode. |
| `:desktop` | The Compose Multiplatform interface. |

The split is not ceremony. Three properties depend on it:

**The domain is testable without anything else.** Deadline arithmetic, ambiguous
dates and rounding are pure functions taking a date, so their tests do not need a
database, an image, or the day the suite happens to run.

**The privacy promise is checkable.** `./gradlew privacyGuard` fails the build if
any module outside `:core:ai` imports an HTTP client, opens a socket or mentions
an analytics SDK. Having `:core:ai` be a separate module is what makes that rule
expressible.

**Optional things are provably optional.** Nothing on the correctness path
depends on `:core:ai`. Removing it entirely would break compilation of the
composition root and nothing else.

## Decisions worth explaining

### Nothing is invented

A field that could not be read is absent, not blank and not zero. A value with no
corroboration is marked uncertain so the person is asked. This runs from the
parser (`Field<T>` carries a confidence and a source) through the database (sum
types are stored as a discriminator plus payload columns, so "unknown" is a state
rather than a null that means several things) to the interface, which shows
where each value came from.

The cost is that Keeply asks more questions than an application that guesses.
The benefit is that it is never confidently wrong, which for a return deadline is
the difference between a refund and an argument.

### The original is never touched

An imported file is copied, never moved, and never rewritten. Everything derived
from it — the straightened image, the thumbnail, the OCR text — lives elsewhere
and can be deleted and rebuilt. The storage screen offers to delete only those.

### Preprocessing does less than you would expect

Received wisdom for receipt OCR is to binarise the image. Measuring it says
otherwise: on a photographed receipt, plain greyscale scored 0.96 and adaptive
thresholding scored 0.75. Tesseract binarises internally and does it better than
a fixed block size can, so Keeply does not.

Perspective correction does earn its place. It is the only heavy step on by
default. See [docs/OCR_PIPELINE.md](docs/OCR_PIPELINE.md).

### Search narrows in SQL and filters in Kotlin

FTS5 finds candidates, then the structured filters run over the result in Kotlin.
For a personal library of a few thousand purchases this is comfortably fast, it
keeps every SQL statement static and parameterised, and it puts the rules about
return and warranty status next to the types they are about instead of
duplicating them across generated queries.

If Keeply ever held a hundred thousand purchases this would be the first thing to
change.

### There is no server

Ktor is a dependency of exactly one module, and only as a client, talking to
`127.0.0.1` when somebody has chosen to run Ollama. Keeply has no local HTTP
boundary because it does not need one: a service boundary between an application
and its own database earns nothing and costs a serialisation format.

## Building on other platforms

The interface is Compose Multiplatform and the core is plain JVM, so Windows and
Linux are a build target rather than a rewrite. The parts that would need
attention are the notifier, which already has a system-tray path, and the data
directory, which already follows each platform's convention.

CI runs the full test suite on Linux as well as macOS, including the bundled
Tesseract and OpenCV binaries. But Keeply has not been tested as a packaged
application on Linux or Windows, so neither is claimed as supported.
