// Sizing evidence for T-2026-09-22-044 (citation into the request).
//
// Caliber: non-blank, non-comment *production* code lines. Both files this task edits are untracked
// (`git diff` is blind to them), so the lines this task added are listed here verbatim as blocks and
// each block must occur exactly once in the current file: a missing or duplicated block fails closed
// instead of silently under- or over-counting.
//
// Run: node artifacts/evidence/T-044-sizing.mjs

import fs from 'node:fs';

const REPO = new URL('../../', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');

const blocks = [
  {
    file: 'android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java',
    text: String.raw`
 * <p>The same envelope frames the sentence a learner cites. §6.2.19 lets a learner ask about one
 * sentence of an answer, so {@link #currentInput} wraps that sentence in {@code QUOTE_OPEN} /
 * {@code QUOTE_CLOSE} ahead of the question - one current question, refined by what it is about,
 * rather than a second turn.
 *
 * <p>Nothing here emits a system message and the plan it is given is encoded as it stands: no entry is
 * trimmed, reordered or dropped. Assembling the single system prompt with the current question
 * belongs to {@link DeepSeekRequestAssembler}; the one thing this class does for the current turn is
 * {@link #currentInput}, because the framing literals live here and nowhere else.`,
  },
  {
    file: 'android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java',
    text: String.raw`
    public static final String QUOTE_OPEN = "<treelearn_quote>";
    public static final String QUOTE_CLOSE = "</treelearn_quote>";`,
  },
  {
    file: 'android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java',
    text: String.raw`
    public static final String[] TAGS = {REASONING_OPEN, REASONING_CLOSE, ANSWER_OPEN, ANSWER_CLOSE,
            QUOTE_OPEN, QUOTE_CLOSE};`,
  },
  {
    file: 'android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java',
    text: String.raw`
    /**
     * The request's current input for one learner turn: the sentence the learner cited, when there is
     * one, escaping and framed, and then the question exactly as it was asked.
     *
     * <p>A citation is a refinement of the current question, not a turn of its own, so it travels
     * inside the current input. That keeps prompt design §5's invariant - exactly one current
     * question, and it is last - true, keeps {@code :core}'s {@code ContextPlan.Entry} ignorant of the
     * vendor protocol, and puts the citation inside the length the assembler already reserves for the
     * current input.
     *
     * <p><strong>This is the one place that decides whether a turn has a citation.</strong> Callers
     * forward whatever the stored row carries; {@code null} and blank are interpreted here, once, so
     * the send path and the retry/reset path cannot disagree about the same rows (one rule at two
     * entry points is a defect this repository has met more than once).
     *
     * <p><strong>Only the citation is escaped.</strong> It is application-written envelope content, so
     * escaping is what stops a stored sentence from closing the block early or opening a new one. The
     * question travels exactly as the learner typed it, because the same bytes are stored and drawn on
     * screen; escaping it here would make "what was sent" differ from "what was stored" while the
     * learner could simply rephrase. The residual risk is written down in prompt design §5: a question
     * that contains the envelope literal reaches the provider unescaped, which the learner can already
     * see on their own screen.
     *
     * @param question the learner's question, sent verbatim; must not be null
     * @param quote the stored citation, or {@code null}/blank for "this turn cites nothing"
     * @return the framed citation followed by the question, or the question alone when there is none
     * @throws IllegalArgumentException when {@code question} is null: a caller bug
     */
    public static String currentInput(String question, String quote) {
        String asked = Guard.text(question, "question");
        String cited = quote == null ? "" : quote.trim();
        if (cited.isEmpty()) {
            return asked;
        }
        return QUOTE_OPEN + "\n" + escape(cited) + "\n" + QUOTE_CLOSE + "\n" + asked;
    }`,
  },
  {
    file: 'android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java',
    text: String.raw`
 * <p><strong>Content can never forge a frame.</strong> Every string the plan carries, and every
 * citation {@link #currentInput} frames, is {@link #escape escaped}, so a stored answer containing
 * {@code </treelearn_answer>} cannot close the block early or open a new one: exactly one framing pair
 * survives any input. {@link #unescape} reverses it, so the escaping is a transformation rather than a
 * loss.`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
        // The citation is forwarded from the row that was just written, never judged here: whether a
        // turn has one is the adapter's decision (DeepSeekContextEncoder.currentInput), and it is
        // the same value the screen draws.
        return submit(command, questionOrdinal, userMessage.userContent, userMessage.quotedText());`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
        return submit(decided.value(), asked.ordinal, asked.text, asked.quote);`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
     * input and is never put in the history as well, which is what would otherwise send it twice. The
     * citation travels inside that same current input (§6.2.19): both routes forward the stored string
     * and the framing is decided once, in the adapter.`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
            final String input, final String quote) {`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
                DeepSeekContextEncoder.currentInput(input, quote), budget);`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
     * <p>The row also carries the sentence the learner cited (§6.2.19). It travels with the question,
     * so re-answering asks about the same sentence instead of quietly turning into a question about
     * nothing in particular.`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
        return question == null ? null
                : new Asked(question.message.userContent, question.message.ordinal,
                        question.message.quotedText());`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
    /** A question, the citation it asks about, and the ordinal its request's history stops before. */`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
        final String quote;`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
        Asked(String text, int ordinal, String quote) {`,
  },
  {
    file: 'android/app/src/main/java/com/treelearn/app/wiring/ChatWiring.java',
    text: String.raw`
            this.quote = quote;`,
  },
];

function isComment(line) {
  const trimmed = line.trim();
  return trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*');
}

const contents = new Map();
const perFile = new Map();
let problems = 0;

for (const block of blocks) {
  if (!contents.has(block.file)) {
    contents.set(block.file, fs.readFileSync(REPO + block.file, 'utf8'));
  }
  const content = contents.get(block.file);
  const occurrences = content.split(block.text).length - 1;
  const lines = block.text.split('\n').filter((line) => line.length > 0);
  if (occurrences !== 1) {
    console.log(`MISSING/DUP  ${block.file}: block occurs ${occurrences} time(s)`);
    console.log(block.text.slice(0, 120));
    problems++;
    continue;
  }
  const tally = perFile.get(block.file) || { code: 0, comment: 0, blank: 0 };
  for (const line of block.text.split('\n')) {
    if (line.trim().length === 0) {
      tally.blank++;
    } else if (isComment(line)) {
      tally.comment++;
    } else {
      tally.code++;
    }
  }
  perFile.set(block.file, tally);
}

let code = 0;
let comment = 0;
let blank = 0;
console.log('=== T-044 added lines, by file (non-blank non-comment = the judged figure) ===');
for (const [file, tally] of perFile) {
  console.log(`${file}`);
  console.log(`  code=${tally.code} comment=${tally.comment} blank=${tally.blank} physical=${tally.code + tally.comment + tally.blank}`);
  code += tally.code;
  comment += tally.comment;
  blank += tally.blank;
}
console.log(`TOTAL code=${code} comment=${comment} blank=${blank} physical=${code + comment + blank}`);
console.log(`blocks=${blocks.length} files=${perFile.size} problems=${problems}`);
console.log(`VERDICT: ${problems === 0 ? 'OK' : 'FAIL'} (code=${code}, soft limit 200, hard limit 500)`);
process.exit(problems === 0 ? 0 : 1);
