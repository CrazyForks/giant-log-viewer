# Encoding-Aware Random Access Text

## Context

The viewer reads arbitrary-size text files by physical byte position. A file can be much larger than available memory, can grow while being viewed, and can contain lines that are either extremely short or extremely long. Code in this area must never preload the whole file and must not build a whole-file index.

The public navigation/search positions are physical byte offsets in the file, not character offsets. If a file starts with a BOM, the BOM bytes are still part of the physical file, but the first displayable text byte is after the BOM.

## Encoding Model

`GiantFileReader` resolves a `TextEncoding` once per reader. `Auto` reads only the first few bytes:

- UTF-8 BOM: `EF BB BF`
- UTF-16LE BOM: `FF FE`
- UTF-16BE BOM: `FE FF`
- No BOM: UTF-8 fallback

UTF-16 files without a BOM require explicit reader construction with `TextEncoding.Utf16LE` or `TextEncoding.Utf16BE`. In that mode, decoding starts at physical byte `0` unless a matching BOM is present, so all exposed byte offsets remain physical file offsets.

The reader delegates text decoding to `TextFileCodec`. UTF-8 and UTF-16 live in separate codec implementations so future encodings can be added without putting byte-boundary rules back into `GiantFileReader`.

UTF-8 reads expand to valid 1-4 byte character boundaries. This includes emoji and other non-BMP characters encoded as 4 bytes. UTF-16 reads align to 2-byte code units and expand around surrogate pairs so high/low surrogate pairs are not split.

Invalid byte sequences are currently left to the JVM charset decoder replacement behavior. Do not add silent whole-file validation in this path.

## Memory Model

Memory must be bounded by:

- the small block cache in `GiantFileReader`
- one or two decoded text windows
- the rows currently visible in the viewport
- small cursor state used for lazy byte-offset calculation

There is intentionally no global line index, no whole-file character index, and no eager per-character byte-position table. `DecodedTextWindow.bytePositionAtCharIndex()` resolves positions inside the current decoded window only. UTF-16 uses arithmetic. UTF-8 scans the current string with a tiny monotonic cursor cache.

Avoid `substring(...).toByteArray(...)` for byte-position math in pager code. That allocates and bakes in an encoding assumption. Use `DecodedTextWindow` or `GiantFileReader.encodedLength()`.

## Pager Constraints

The pager must handle both extremes:

- Files that are mostly line breaks.
- Files with very long lines and no line breaks.

Many line breaks should be processed incrementally from the current decoded window; avoid collecting every separator in a large file. Very long lines must not be fully loaded just to soft-wrap. Prefer bounded windows and, when necessary, extra local disk I/O.

Backward navigation is the hardest case because soft-wrap row starts depend on prior text in the same physical line. It is acceptable to reread nearby bytes when the user navigates back and forth. Exact previous-row reconstruction is capped to a fixed byte budget; beyond that, navigation must use bounded backward scanning rather than reading from the physical line start. It is not acceptable to retain an unbounded line in memory.

## Search Constraints

Regex search runs over bounded overlapping windows. Matches should be consumed as sequences where possible. Match byte ranges must be computed through the decoded window offset resolver so UTF-8 multibyte characters, UTF-16 code units, and surrogate pairs return correct physical byte ranges.

Regexes with a bounded maximum match length are searched exactly. Literal patterns, character classes, and bounded quantifiers such as `{1000}` or `{2,8}` are valid. Unbounded or complex patterns such as `.*`, `.+`, and `{10,}` are rejected by default because they cannot be searched exactly with bounded memory and finite overlap.

Callers may pass the search bypass flag after warning the user. Bypassed regex search still uses bounded memory and a capped search window of 4 MiB, so it can miss matches whose span exceeds that window. This is intended for UX flows where the user explicitly accepts approximate bounded search for complex regexes.

## Known Pain Points

- Backward soft-wrap reconstruction can require rereading earlier chunks of a long physical line.
- Previous-row positions in extremely long no-newline regions trade perfect reconstruction for bounded memory and bounded local I/O once the exact reconstruction cap is exceeded.
- Regex matches across window boundaries require overlap and can still be tricky for complex patterns. Bypass mode is bounded and approximate; keep the default rejection in place unless a true streaming regex engine is introduced.
- Variable-width text layout makes it harder to compute row starts without inspecting text.
- Invalid or mixed encodings are decoded with replacement characters; the viewer does not validate entire files.
- Tests often use physical byte offsets. For UTF-16 files, remember to include BOM bytes in expected positions.

## Adding Another Encoding

To add another encoding:

1. Add a `TextEncoding` and `TextEncodingKind` value.
2. Extend BOM or explicit encoding resolution in `GiantFileReader`.
3. Add a `TextFileCodec` implementation with boundary-safe `readText()` and `encodedLength()`.
4. Return a `DecodedTextWindow` whose `bytePositionAtCharIndex()` does not allocate a per-character table.
5. Add reader tests for start/end boundary alignment, block-boundary splits, and pager/search byte positions.

Keep the invariant that byte positions exposed outside the reader are physical file offsets.
