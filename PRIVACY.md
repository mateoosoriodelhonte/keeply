# Your data stays yours

Receipts say where you live, what you buy, how you pay and when you are out of
the house. Keeply is built so none of that has to go anywhere.

## What Keeply does

- Stores your receipts as ordinary files in **one folder on your computer**.
- Reads receipts **on your machine**, using a text-recognition engine and
  language model shipped inside the application.
- Works **with no internet connection at all**.
- Lets you export everything, or back it up into one portable file, whenever you
  want.

## What Keeply does not do

- No account. No sign-in. No password.
- No cloud storage. No syncing.
- No analytics. No tracking. No crash reporting. No advertising.
- No sending your receipts anywhere to be read.
- No automatic uploads of any kind.
- No checking for updates, which would tell someone you are running it.

## This is enforced, not just promised

A privacy policy is a sentence anybody can write. Keeply's is a build step:

```bash
./gradlew privacyGuard
```

It fails the build if any file outside `:core:ai` imports an HTTP client, opens a
socket, or mentions an analytics SDK. It runs on every pull request.

That is why the optional local-AI integration is a separate module: it is the one
place a connection is permitted, and having it separate is what makes the rule
expressible for everywhere else.

## Where your files are

| platform | location |
| --- | --- |
| macOS | `~/Library/Application Support/Keeply` |
| Windows | `%LOCALAPPDATA%\Keeply` |
| Linux | `$XDG_DATA_HOME/keeply`, or `~/.local/share/keeply` |

```
Keeply/
  database/     keeply.db, your purchase details
  receipts/     your original files, never modified
  manuals/
  photos/
  thumbnails/   made from your files; safe to delete
  processed/    made from your files; safe to delete
  ocr/          the language model
```

Keeply never writes outside this folder. You can copy it, back it up with
whatever you already use, or open it in Finder. Deleting it deletes your library,
so take a backup first.

Files inside are named from generated identifiers rather than from what you
called them, so a receipt does not sit on disk under a name that says where you
shop and what you bought.

## The optional local model

If you choose to run [Ollama](https://ollama.com) on your own computer, Keeply
can ask it to tidy up an abbreviated product name, suggest a category, or
summarise warranty text you pasted in.

- It is **off unless you switch it on**.
- Keeply **only ever talks to your own machine**. The endpoint's host is parsed
  and compared exactly; an address that is not loopback is refused, including
  ones designed to look like it such as `http://127.0.0.1.example.invalid`.
- Keeply **never installs Ollama and never downloads a model**.
- It is **never on the correctness path**. Totals, dates and return windows come
  from the deterministic parser and nowhere else.
- Every answer is a **suggestion you accept or ignore**. Nothing it returns is
  written to a field on its own.

Keeply is a complete product with it switched off, and that is how it ships.

## Things Keeply deliberately does not store

- Card numbers, bank details or any payment credential. The payment field is a
  label you type, such as "Visa ending 1234", and nothing in the import pipeline
  ever writes to it.
- Your name, email address or anything identifying you. Keeply never asks.

## What leaves your machine, ever

Only what you export yourself: a backup file, a spreadsheet or a JSON file that
you chose to write, to a location you chose. Keeply puts them where you say and
does nothing else with them.

## Reporting a problem

If you find something that contradicts this page, that is a security issue and
not a documentation one. See [SECURITY.md](SECURITY.md).
