# Encoding-Aware Random Access Text

## Context

The viewer reads arbitrary-size text files by physical byte position. A file can be much larger than available memory, can grow while being viewed, and can contain lines that are either extremely short or extremely long. Code in this area must never preload the whole file and must not build a whole-file index.

The public navigation/search positions are physical byte offsets in the file, not character offsets. If a file starts with a BOM, the BOM bytes are still part of the physical file, but the first displayable text byte is after the BOM.

## Encoding Model

`GiantFileReader` resolves a `TextEncoding` once per reader. `Auto` probes `TEXT_ENCODING_PROBE_BYTES` bytes from the physical start of the file:

- UTF-8 BOM: `EF BB BF`
- UTF-16LE BOM: `FF FE`
- UTF-16BE BOM: `FE FF`
- No BOM: UTF-8 fallback

UTF-16 files without a BOM require explicit reader construction with `TextEncoding.Utf16LE` or `TextEncoding.Utf16BE`. In that mode, decoding starts at physical byte `0` unless a matching BOM is present, so all exposed byte offsets remain physical file offsets.

The reader delegates text decoding to `TextFileCodec`. UTF-8 and UTF-16 live in separate codec implementations so future encodings can be added without putting byte-boundary rules back into `GiantFileReader`.

UTF-8 reads expand to valid 1-4 byte character boundaries. This includes emoji and other non-BMP characters encoded as 4 bytes. The UTF-8 codec may still advance by `KOTLIN_CHARS_PER_SURROGATE_PAIR` while walking a Kotlin `String`, because JVM/Kotlin strings are indexed by UTF-16 code units even when the file encoding is UTF-8.

UTF-16 reads align to `UTF16_CODE_UNIT_BYTES` boundaries and expand around surrogate pairs so high/low surrogate pairs are not split. `encodedLength()` for UTF-16 is therefore `text.length * UTF16_CODE_UNIT_BYTES`.

Encoding constants are named in `TextEncodingConstants.kt`. Keep byte-size constants (`UTF16_CODE_UNIT_BYTES`, `UTF8_MAX_BYTES_PER_CODE_POINT`) separate from in-memory string-index constants (`KOTLIN_CHARS_PER_SURROGATE_PAIR`).

`GiantFileReader` requires `blockSize >= MIN_BLOCK_SIZE`, currently the same as the BOM probe size. This keeps block/cache assumptions out of the "tiny block" edge case while preserving bounded random access for realistic block sizes.

Invalid byte sequences are currently left to the JVM charset decoder replacement behavior. Do not add silent whole-file validation in this path.

## Memory Model

Memory must be bounded by:

- the small block cache in `GiantFileReader`
- one or two decoded text windows
- the rows currently visible in the viewport
- small cursor state used for lazy byte-offset calculation

There is intentionally no global line index, no whole-file character index, and no eager per-character byte-position table. `DecodedTextWindow.bytePositionAtCharIndex()` resolves positions inside the current decoded window only. UTF-16 uses arithmetic. UTF-8 scans the current string with a tiny monotonic cursor cache.

Avoid `substring(...).toByteArray(...)` for byte-position math in pager code. That allocates and bakes in an encoding assumption. Use `DecodedTextWindow` or `GiantFileReader.encodedLength()`.

Row counts and row-movement counts are `Long`. Do not convert logical row counts to `Int` just because a large file can have more rows than fit in `Int`. Convert to `Int` only at unavoidable Kotlin/JVM collection API boundaries such as `take(n)` or `ArrayList(capacity)`, and only after bounding the local decoded window.

## Pager Constraints

The pager must handle both extremes:

- Files that are mostly line breaks.
- Files with very long lines and no line breaks.

Many line breaks should be processed incrementally from the current decoded window; avoid collecting every separator in a large file. Very long lines must not be fully loaded just to soft-wrap. Prefer bounded windows and, when necessary, extra local disk I/O.

Backward navigation is the hardest case because soft-wrap row starts depend on prior text in the same physical line. It is acceptable to reread nearby bytes when the user navigates back and forth. Exact previous-row reconstruction is capped to a fixed byte budget; beyond that, navigation must use bounded backward scanning rather than reading from the physical line start. It is not acceptable to retain an unbounded line in memory.

## Search Constraints

Regex search runs over bounded overlapping decoded windows. Matches should be consumed as sequences where possible. Match byte ranges must be computed through the decoded window offset resolver so UTF-8 multibyte characters, UTF-16 code units, and surrogate pairs return correct physical byte ranges.

Regex search starting positions are not limited to the initial local window; forward and backward search continue window by window until a match is found or EOF/BOF is reached. The size of each search window is bounded: roughly `SEARCH_WINDOW_PAGE_COUNT` pages from the current viewport-derived byte window, capped by `MAX_REGEX_SEARCH_WINDOW_BYTES` (currently 4 MiB).

Unbounded and very large bounded regexes are allowed, but they only see the current local search window. For example, if a page is about 10 KiB, `.*` can match only within roughly four pages, not across the whole file. This restores the pre-branch behavior: the starting point can move arbitrarily through the file, while an individual regex match cannot grow without bound.

The overlap between search windows is a heuristic based on `encodedLength(searchPredicate.pattern)`, with a minimum of one encoded character. This is enough for ordinary literal-ish patterns and keeps memory bounded, but it is not a streaming regex engine. A match whose required span exceeds the local window can be missed.

The `isBypass` parameter remains on the search APIs for compatibility, but current search behavior does not branch on it. Do not reintroduce parser-based rejection of complex regexes unless the UX/API contract changes again.

## Known Pain Points

- Backward soft-wrap reconstruction can require rereading earlier chunks of a long physical line.
- Previous-row positions in extremely long no-newline regions trade perfect reconstruction for bounded memory and bounded local I/O once the exact reconstruction cap is exceeded.
- Regex matches across window boundaries require overlap and can still be tricky for complex patterns. Current search is bounded and approximate for matches larger than the local search window; a true streaming regex engine would be needed for exact unbounded regex matching.
- Variable-width text layout makes it harder to compute row starts without inspecting text.
- Invalid or mixed encodings are decoded with replacement characters; the viewer does not validate entire files.
- Tests often use physical byte offsets. For UTF-16 files, remember to include BOM bytes in expected positions.

## Adding Another Encoding

To add another encoding:

1. Add a `TextEncoding` and `TextEncodingKind` value.
2. Extend BOM or explicit encoding resolution in `GiantFileReader`.
3. Add a `TextFileCodec` implementation with boundary-safe `readText()` and `encodedLength()`.
4. Return a `DecodedTextWindow` whose `bytePositionAtCharIndex()` does not allocate a per-character table.
5. Add reader tests for start/end boundary alignment, block-boundary splits, BOM/no-BOM behavior, and pager/search byte positions.

Keep the invariant that byte positions exposed outside the reader are physical file offsets.
