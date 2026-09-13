# Backups

A local-only application has to make backups good. If a laptop is stolen and the
backup does not restore, Keeply has lost somebody's receipts, and no amount of
privacy makes up for that.

## What is in one

A `.keeplybackup` file is a ZIP:

```
manifest.json          what the archive says about itself
database/keeply.db     purchase details
files/receipts/...     the original receipts
files/manuals/...
files/photos/...
```

The manifest carries the format version, the schema version, the application
version, when it was made, how many purchases and documents it holds, and a
SHA-256 digest of every file. That digest is what lets a restore tell whether
what it read is what was written.

Thumbnails and processed images are deliberately not included. They are derived,
Keeply rebuilds them, and including them would roughly double the size of a
backup for nothing.

## Making one

Settings, then **Back up Keeply**. It is written to a temporary name and moved
into place, so an interrupted backup never leaves a file that looks complete.

## Restoring

Settings, then **Restore a backup**. Keeply describes the archive first, with
how many purchases it holds and what restoring it would replace, and asks. It is
never the immediate result of choosing a file.

Then:

1. Everything in the archive is validated. Nothing is written yet.
2. Files are extracted to a staging folder and each digest is checked.
3. The database is closed.
4. The existing library is moved aside, not deleted.
5. The restored library is moved into place.
6. Only then is the old one removed, and the application reopens.

A restore either happens completely or not at all. One that fails half way leaves
you exactly as you were.

A restore into a folder that already holds a library is refused unless you
explicitly chose to replace it.

## What a restore refuses

A backup file can come from anywhere: a download, a shared drive, someone else's
machine. Keeply treats it as hostile.

| | why |
| --- | --- |
| `../` in a path | writes outside the folder you chose |
| absolute paths, drive letters | same |
| backslash paths | some readers normalise these into traversal |
| null bytes, padded segments | disguise one path as another |
| the same file twice | the second overwrites the first after the first was checked |
| paths Keeply would not write | an archive with a `launchd` plist in it is not a backup |
| extreme expansion ratios | a zip bomb fills the disk |
| an entry larger than it claims | a ZIP header can lie, so the bytes are counted too |
| a digest that does not match | the archive is damaged or was edited |
| a manifest that does not parse | not a Keeply backup |
| a newer format or schema version | this build does not understand it |

Every check runs **before** anything is written. A reader that stops at the first
bad entry has already written the good ones.

Twelve tests build the archive an attacker would send and assert Keeply refuses
it and writes nothing. One of them is written byte by byte, because Java's own
ZIP writer refuses to produce a duplicate entry name, which is precisely why an
attacker would not use it either.

## Exports, which are a different thing

**Export as a spreadsheet** and **Export as JSON** write the purchase details
only, not the receipt files. They exist so "your data is yours" is something you
can act on rather than something you read.

CSV cells are disarmed: a spreadsheet executes a cell beginning with `=`, `+`,
`-` or `@`, so a leading apostrophe is added. It is invisible once the file is
open.

## Backing up yourself

Keeply's folder is ordinary files. Copy it, or point Time Machine or whatever you
already use at it. Close Keeply first so the database is not mid-write.
[PRIVACY.md](../PRIVACY.md) says where the folder is.
