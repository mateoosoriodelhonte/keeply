# Contributing

Thanks for looking. Keeply is a small application with a narrow purpose, and the
most useful contributions are usually the ones that make it better at that rather
than broader.

## Getting set up

[docs/LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md). You need a JDK 21 or
newer and nothing else.

## Before opening a pull request

```bash
./gradlew spotlessApply
./gradlew spotlessCheck privacyGuard build -Pkeeply.strict=true
```

## What Keeply is trying to be

Some of this is unusual enough to be worth stating, because a change that
contradicts it will be turned down however good the code is.

**Nothing is invented.** A field Keeply could not read is left empty. A value
with no corroboration is marked uncertain and the person is asked. Keeply is
allowed to be unhelpful; it is not allowed to be confidently wrong.

**Nothing leaves the machine.** No network calls outside `:core:ai`, no
analytics, no crash reporting, no update checks. `./gradlew privacyGuard` will
tell you before CI does.

**Say where a value came from.** A deadline printed on a receipt and one from a
rule somebody saved are different claims. Keeply never presents its own guess as
the shop's policy.

**Measure before optimising, and believe the measurement.** The preprocessing
defaults are what they are because binarising the image measured *worse*, not
because a blog post said so. If you change a heuristic, change the measurement
with it.

**Write for a person.** Every string the application shows is read by somebody
who does not know what OCR is. "That PDF is password protected, so Keeply cannot
read it. Save an unprotected copy and import that instead" is the standard, not
"PDFBox: InvalidPasswordException".

## Tests

Keeply's tests are meant to be readable as a description of the behaviour, so
their names are sentences and they carry a comment when the reason is not
obvious from the assertion.

Worth testing carefully:

- anything that produces a deadline
- anything that reads an untrusted file
- anything that decides what a value's provenance is
- anything a person will read

Anything involving a date takes one rather than calling `now()`, so a test does
not pass on Tuesdays.

## Real receipts

Never commit one. Not yours, not a redacted one, not a "harmless" one. The
generated receipts in `:fixtures` cover more cases than a real receipt would, and
`./gradlew :fixtures:writeSampleReceipts` will make you as many as you want.

If you need a case the generator does not produce, add it to the generator. That
is more useful than the receipt would have been.

## Commits and pull requests

Say what changed and why. The why is the part nobody can recover later. If a
measurement or a bug prompted the change, put the number or the failure in the
message.

## Reporting problems

Bugs and ideas: [issues](https://github.com/mateoosoriodelhonte/keeply/issues).

Security, or anything contradicting [PRIVACY.md](PRIVACY.md):
[a security advisory](https://github.com/mateoosoriodelhonte/keeply/security/advisories/new)
rather than a public issue.
