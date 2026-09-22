"""Does the quoted sentence reach the DeepSeek request? (read-only)

The verdict is COMPUTED from the code, never asserted. An earlier revision of this file printed a
hardcoded "NO" beside a hand-written reason; once T-044 changed the chain it kept reporting the old
state with the same confident wording, which is what a non-derived snapshot always does.

Three links must hold, each printed with file:line so the reader can check it:
  1. the sender hands the stored quote to the encoder's currentInput, not only the question text;
  2. the encoder's currentInput frames a non-blank quote in the envelope it declares;
  3. that envelope literal is defined in exactly one file across the android main sources.

Exit codes: 0 = the quote reaches the request, 1 = it does not (the reason names the failed link),
3 = inconclusive (a file this check needs is missing).
"""
import os
import sys

WIRING = "android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java"
ENCODER = "android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java"
CALL = "currentInput("


def read(root, rel):
    path = os.path.join(root, rel)
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return handle.read().splitlines()


def show(lines, rel, first, last, why):
    first = max(1, first)
    last = min(last, len(lines))
    print("--- %s:%d-%d  (%s)" % (rel, first, last, why))
    for number in range(first, last + 1):
        print("%d: %s" % (number, lines[number - 1]))
    print("")


def code_lines(lines):
    """Line numbers that are real code: a javadoc or block-comment line is not a call site."""
    out = []
    for number, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith(("*", "/*", "//")):
            continue
        out.append(number)
    return out


def link_quote_forwarded(wiring):
    """1. the current input a send builds is currentInput(<question>, <quote>)."""
    for number in code_lines(wiring):
        if CALL not in wiring[number - 1]:
            continue
        window = " ".join(wiring[number - 1:number + 2])
        if "quote" in window:
            return number
    return None


def link_encoder_frames(encoder):
    """2. the encoder's own currentInput wraps a non-blank quote in QUOTE_OPEN / QUOTE_CLOSE."""
    for number in code_lines(encoder):
        line = encoder[number - 1]
        if CALL not in line or "static" not in line:
            continue
        body = " ".join(encoder[number - 1:number + 12])
        if "QUOTE_OPEN" in body and "QUOTE_CLOSE" in body and "isEmpty" in body:
            return number
    return None


def literal(lines, name):
    """The string literal a constant is declared with, read from the source, not assumed here."""
    for line in lines:
        if name in line and "=" in line and '"' in line:
            parts = line.split('"')
            if len(parts) >= 3:
                return parts[1]
    return None


def main(root):
    wiring = read(root, WIRING)
    encoder = read(root, ENCODER)
    if wiring is None or encoder is None:
        print("missing: %s" % (WIRING if wiring is None else ENCODER))
        print("QUOTE-REACHES-PROMPT: INCONCLUSIVE")
        return 3

    forwarded = link_quote_forwarded(wiring)
    if forwarded:
        show(wiring, WIRING, forwarded - 2, forwarded + 2,
             "submit(): what becomes the request's current input")
    framed = link_encoder_frames(encoder)
    if framed:
        show(encoder, ENCODER, framed, framed + 7, "currentInput(): where a quote is framed")

    envelope = literal(encoder, "QUOTE_OPEN")
    defining = []
    if envelope:
        for module in sorted(os.listdir(os.path.join(root, "android"))):
            base = os.path.join(root, "android", module, "src", "main")
            if not os.path.isdir(base):
                continue
            for walk_base, _dirs, names in os.walk(base):
                for name in names:
                    if not name.endswith(".java"):
                        continue
                    path = os.path.join(walk_base, name)
                    with open(path, encoding="utf-8") as handle:
                        if envelope in handle.read():
                            defining.append(os.path.relpath(path, root))
    print("envelope literal: %s" % envelope)
    print("files defining the envelope across android/*/src/main: %d" % len(defining))
    for path in defining:
        print("  " + path)
    print("")

    failures = []
    if not forwarded:
        failures.append("ChatWiring never passes a quote to " + CALL + " - only the question text is sent")
    if not framed:
        failures.append("the encoder's " + CALL + " does not frame a quote in QUOTE_OPEN/QUOTE_CLOSE")
    if not envelope:
        failures.append("the encoder declares no QUOTE_OPEN string literal")
    elif len(defining) != 1:
        failures.append("the envelope literal is defined in %d files, not 1" % len(defining))

    if failures:
        print("QUOTE-REACHES-PROMPT: NO")
        for why in failures:
            print("  reason: " + why)
        return 1
    print("QUOTE-REACHES-PROMPT: YES")
    print("  reason: the sender hands the stored quote to " + CALL + " at %s:%d, and the encoder" % (WIRING, forwarded))
    print("  frames a non-blank quote as %s...%s ahead of the question, which is sent verbatim." % (envelope, literal(encoder, "QUOTE_CLOSE")))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))