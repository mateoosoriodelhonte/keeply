# The data model

Everything Keeply knows lives in one SQLite file and one folder of documents.
This describes both, and the rules encoded in them.

## Tables

```
merchant ────┐
             │      ┌──── purchase_tag
category ────┤      ├──── purchase_document ──── document
             ├─► purchase ──┤
document ────┘      └──── line_item
                            
document ──── document_text          purchase_fts (FTS5)
meta
```

| table | holds |
| --- | --- |
| `purchase` | What was bought: product, shop, date, price, tax, return window, warranty, notes, serial number, archived flag |
| `merchant` | Shops, and the return rule a person saved for each |
| `category` | The starter set plus anything added |
| `document` | Every file Keeply took responsibility for |
| `document_text` | Text derived from a document. Separate on purpose |
| `purchase_tag`, `purchase_document`, `line_item` | Things belonging to one purchase |
| `purchase_fts` | The search index |
| `meta` | Keeply's notes about the store itself, written into backups |

## Rules the schema enforces

**Deleting a scan never deletes a purchase.** `receipt_document_id`,
`product_photo_id`, `category_id` and `merchant_id` are all `ON DELETE SET NULL`.
Somebody clearing out a file loses the scan, not the record of what they bought.

**Deleting a purchase takes its own rows with it.** Tags, line items, attachment
links and derived text are `ON DELETE CASCADE`. They are meaningless without
their parent.

These rules are load-bearing and invisible: changing one `SET NULL` to `CASCADE`
would turn "I deleted an old scan" into "I deleted the record of what I bought",
and nothing else in the test suite would notice. `SchemaTest` asserts each one
directly.

**Foreign keys are actually on.** SQLite treats `foreign_keys` as per-connection
state that defaults to off, and the driver opens connections as it needs them, so
a single `PRAGMA` after opening protects only the first. It is a connection
property. This was a real bug, and its failure mode was silent.

**A shop's name is kept twice.** `merchant_id` points at the record;
`merchant_name` is stored on the purchase as well, so a purchase still reads
correctly if the merchant record is ever deleted.

## Unknown is a state, not a null

Sum types are stored as a discriminator plus its payload columns rather than as a
serialised blob:

```sql
return_policy_kind   TEXT NOT NULL DEFAULT 'UNKNOWN',  -- DAYS | UNTIL | UNKNOWN
return_policy_days   INTEGER,
return_policy_until  TEXT,
return_source        TEXT NOT NULL DEFAULT 'NONE',
return_deadline      TEXT
```

So the database can tell "30 days from purchase" from "until 12 October" from "we
do not know", and can say where each came from. A single nullable `deadline`
column could not, and the difference is the whole point: a deadline from a rule
somebody saved and one printed on a receipt are different claims.

It also keeps the database readable. Open it in any SQLite tool and you can see
exactly what Keeply recorded about you.

## Money

Minor units and an ISO 4217 code, never a floating-point number:

```sql
price_minor    INTEGER,
price_currency TEXT
```

Receipt totals are added, compared and stored, and binary floating point gets
those wrong in ways people notice. `Money` detects overflow rather than wrapping,
and refuses to combine currencies.

## Provenance

Every field Keeply reads carries a confidence and a source through the whole
pipeline:

```kotlin
Field(value, confidence, source, evidence)
```

- **confident** — read cleanly and consistent with the rest of the receipt
- **uncertain** — read, but something did not add up
- **confirmed** — a person looked at it

A field that could not be read is **absent**, not blank and not zero. That is the
rule the design rests on: Keeply is never confidently wrong, because it never
claims more than it has.

## Migrations

The current schema is defined by the `.sq` files; a snapshot is committed at
`core/data/src/main/sqldelight/databases/`, and the build verifies that migrating
an older database produces exactly the current schema.

Opening is conservative. A store written by a newer Keeply is refused with an
explanation rather than migrated downward, and a file that is not a Keeply
database is refused rather than written to. Receipts are irreplaceable; an error
message is always cheaper than a damaged store.

## Where files live

```
Keeply/
  database/keeply.db
  receipts/ab/abcdefghijklmnopqrstuvwxyz.png
  manuals/
  photos/
  thumbnails/    derived, safe to delete
  processed/     derived, safe to delete
  ocr/           the language model
```

Names come from generated identifiers, never from what a person called the file,
so a receipt does not sit on disk under a name that says where they shop. Files
are spread over sub-folders by their first two characters, which keeps directory
listings usable after a few thousand receipts.

Anything under `thumbnails/` or `processed/` can be deleted and rebuilt. Nothing
else can, and nothing else is ever offered for deletion by the storage screen.
