# Reading a receipt

How Keeply gets from a file somebody dropped on the window to fields they can
check, and what measuring it actually showed.

## The path

```
file
 │
 ├─ PDF with a text layer ──────────────► use it, skip everything below
 │
 ├─ PDF without one ────────► render each page at 300 dpi ──┐
 │                                                          │
 └─ image ─────────────────────────────────────────────────┤
                                                            ▼
                                          find the page in the photograph
                                                            │
                                                            ▼
                                                   flatten it
                                                            │
                                                            ▼
                                                    greyscale
                                                            │
                                                 enlarge if small
                                                            ▼
                                                    Tesseract
                                                            │
                                                            ▼
                                              text + per-word confidence
                                                            │
                                                            ▼
                                              deterministic extraction
```

A PDF that carries its own text never goes through OCR. It would be slower and
give a worse answer, and the distinction matters often: emailed order
confirmations are the most common kind of receipt somebody already has as a file.

The threshold is deliberate rather than "any text at all". Scanners embed a
handful of stray characters, and parsing six characters of noise is worse than
running OCR.

## What preprocessing actually does

Received wisdom for receipt OCR is to binarise the image before handing it over.
Keeply measured it against generated receipts whose exact contents are known.

Character similarity, photographed receipt, mean over four layouts:

| what was done | similarity |
| --- | --- |
| greyscale only | **0.96** |
| plus adaptive thresholding | 0.76 |
| everything on | 0.66 |

Thresholding is not neutral, it is actively harmful. Tesseract binarises
internally and does it better than a fixed block size can, so Keeply does not.

Perspective correction does earn its place:

| | similarity |
| --- | --- |
| without flattening the page | 0.953 |
| with | 0.963 |

So the default pipeline is: find the page, flatten it, convert to greyscale,
enlarge if small. Contrast equalisation and denoising measured neutral and are
off. The full chain remains available for somebody retrying a bad photo, labelled
as what it is, and is never applied automatically.

[`PreprocessingAblationTest`](../core/ocr/src/test/kotlin/app/keeply/ocr/PreprocessingAblationTest.kt)
asserts both conclusions with a live comparison rather than a remembered number,
so they cannot quietly rot.

## Page segmentation

Chosen by measuring all four modes rather than by copying advice. Automatic mode
lost the total on two of four layouts; single-block found it on all of them and
was faster. Keeply uses single block, and keeps `preserve_interword_spaces` on
because the column alignment of a till receipt is most of what makes a total
findable next to its label.

## Measured accuracy

From [`OcrAccuracyTest`](../core/ocr/src/test/kotlin/app/keeply/ocr/OcrAccuracyTest.kt).
Character similarity against the known text, with rule lines excluded.

| layout | similarity |
| --- | --- |
| till roll | 0.996 |
| aligned columns | 1.000 |
| compact slip | 0.986 |
| printed invoice | 0.996 |

Between 60 and 130 milliseconds per receipt on an Apple silicon machine.
Timings vary between runs; the similarities do not.

| capture | similarity |
| --- | --- |
| clean | 0.988 |
| faded thermal paper | 0.992 |
| photographed at an angle | 0.996 |
| out of focus | 0.954 |

A note on method: rule lines are excluded from the comparison. Tesseract
correctly does not report a row of dashes as text, and counting those omissions
as errors made a perfectly-read receipt score 0.55. That would have hidden real
regressions in noise.

## Extraction

No model. Everything Keeply reports can be traced to a rule and a line of text,
which is what makes the review screen worth looking at.

**Money.** The hard part is not finding numbers, it is not finding the wrong
ones. A till receipt is full of digits that are not money: dates, times, phone
numbers, quantities, card suffixes, postal codes. An amount needs a currency
marker or exactly two decimal places, and never sits inside a date.

**Totals.** `SUBTOTAL` contains `TOTAL`, so subtotals are matched first; an
extractor that checks for `TOTAL` first reports the subtotal confidently and
nobody notices. Cash tendered is routinely larger than the total, so taking the
largest number is wrong often enough to matter. Labelled totals win, and among
them the one that subtotal plus tax agrees with is the only reading marked
confident.

**Dates.** Receipts write them every way there is, and OCR turns zeroes into
`@`. A date written `08.10.26` cannot be resolved from the text alone. Keeply
uses the separator as a hint, because a till writing dots is almost always
writing day first, and marks the field uncertain either way. A test exists whose
whole job is that Keeply is never *confidently* wrong about a date: a deadline
computed from the wrong month costs somebody a refund.

**OCR repair.** Tesseract confuses a small fixed set of glyphs. Repair is applied
only where digits already dominate a token, so `$l2.5O` becomes `$12.50` and a
shop called SOHO is never rewritten to 5OHO.

**Return windows.** Only attributed to a shop when the shop printed one. "See our
returns policy online" yields nothing, which is correct.

### Measured extraction accuracy

From [`ExtractionAccuracyTest`](../core/extraction/src/test/kotlin/app/keeply/extraction/ExtractionAccuracyTest.kt),
on 40 receipts across four layouts, both tax arrangements, grouped and ungrouped
thousands, four date formats and two currencies. Perfect text, so this measures
parsing rather than recognition.

| field | correct |
| --- | --- |
| total | 40/40 |
| shop | 40/40 |
| purchase date | 40/40 |
| receipt number | 40/40 |
| tax, where printed | 38/38 |
| item count, exactly | 40/40 |

Two receipts print no tax line, and Keeply reports no tax for them. A test
asserts that rather than treating it as a miss.

## The honest caveat

These are Keeply's own generated receipts: clean, English, consistently laid out,
and rendered rather than photographed in a kitchen. A crumpled thermal receipt
under a warm bulb will do worse, and a receipt in a language the shipped model
does not cover will not be read at all.

That is exactly why nothing is saved without somebody looking at it, why every
field carries a confidence, and why the review screen shows the text a value was
read from.

## Seeing it for yourself

Every import keeps its reading. Open a purchase, or choose **See what Keeply
read** during review, and you get the raw text, the confidence, the preprocessing
steps that were applied, the words Tesseract was unsure of, and which rule
produced each field.

To generate receipts to try it on:

```bash
./gradlew :fixtures:writeSampleReceipts
```
