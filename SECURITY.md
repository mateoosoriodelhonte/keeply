# Security

Keeply takes files from strangers. A receipt arrives by email, a backup comes off
a shared drive, a PDF is downloaded from a shop's website. All of it is untrusted
input, and Keeply is built on that assumption.

## Reporting a vulnerability

Please open a [security advisory](https://github.com/mateoosoriodelhonte/keeply/security/advisories/new)
rather than a public issue. Include what you did, what happened, and what you
expected. There is no bounty; there is gratitude and credit if you want it.

Anything that contradicts [PRIVACY.md](PRIVACY.md) is a security issue.

## How imported files are handled

**The type comes from the bytes, not the name.** The extension is a claim made by
whoever produced the file. Keeply reads the magic bytes and accepts only JPEG,
PNG and PDF. A genuine mismatch, such as a JPEG saved as `.png`, is recorded
rather than refused; it is a human mistake, not an attack.

**Limits are checked before anything decodes.** Image dimensions are read from
the header without allocating a pixel buffer, so a 200-byte PNG declaring
60,000 × 60,000 pixels is refused rather than processed. PDF page counts and
encryption are checked before text extraction. The copy itself is bounded,
because a file being written while it is imported can grow past a limit its
metadata satisfied.

**Nothing inside a document is executed or fetched.** No JavaScript, no embedded
content, no remote resources.

**Originals are never modified.** Files are copied, written to a temporary name
and moved into place, so an interrupted import leaves nothing behind.

**Paths are generated, never taken from input.** A stored path can only be built
from an identifier Keeply generated, and resolving one that escapes the data
directory throws. That second check matters because stored paths come back from
the database, which a restored backup can influence.

## How backups are handled

A backup archive is the most dangerous thing Keeply opens, because the attacks
against archive readers are cheap and well known. Every one of these is refused,
with nothing written:

| | |
| --- | --- |
| `../` in a path | refused |
| absolute paths, and Windows drive letters | refused |
| backslash paths some readers normalise | refused |
| null bytes and padded path segments | refused |
| the same file listed twice | refused |
| any path Keeply would not itself write | refused |
| an archive that expands far more than it compresses | refused |
| an entry larger than it claims, checked while reading | refused |
| a file whose digest does not match the manifest | refused |
| a manifest that does not parse, or claims a future version | refused |

Everything is validated **before** anything is written, because a reader that
stops at the first bad entry has already written the good ones. Extraction goes
to a staging folder; the existing library is moved aside rather than deleted and
removed only once the new one is in place. A restore either happens completely or
not at all.

A restore into a folder that already holds a library is refused unless the person
explicitly chose to replace it.

## Other measures

**No SQL is built from strings.** Every statement is generated and parameterised
by SQLDelight. Search input is rebuilt from the words a person typed rather than
passed through, because FTS5 has its own expression syntax and a stray quote
would otherwise either error or mean something unintended.

**CSV cells are disarmed.** A spreadsheet executes a cell beginning with `=`,
`+`, `-` or `@`, so a product name of `=cmd|'/c calc'!A1` would run when the
exported file is opened. A leading apostrophe makes the cell text.

**Notification text cannot escape its string literal.** The text comes off a
receipt. Arguments go straight to the interpreter rather than through a shell,
quotes and backslashes are escaped, and control characters are stripped.

**The local-AI endpoint is parsed, not prefix-matched.** An early version compared
prefixes and accepted `http://127.0.0.1.example.invalid`, a hostname an attacker
controls that begins with a loopback address. The host is now compared exactly.

**Dependencies are pinned and checked.** The Gradle wrapper is pinned by SHA-256.
The Tesseract language model is fetched from a pinned commit and refused unless
it matches a pinned digest, so a changed upstream cannot alter what ships inside
the application. Dependabot watches the rest.

## What is not covered

Keeply does not encrypt your data at rest. Your receipts are ordinary files in a
folder, readable by anything running as you. If you need them encrypted, use
FileVault or the equivalent on your platform. Saying otherwise would imply a
protection Keeply does not provide.

Keeply is not signed or notarised, because this project has no Apple Developer
certificate. See
[docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md#macos-and-unsigned-applications)
for what that means and how to check what you downloaded.
