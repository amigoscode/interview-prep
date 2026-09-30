# Interview Tasks — String Drills

Sequential user stories, one at a time. Budget **30 minutes**.

---

## Task 1: Count the Words (8 min) (reported at Delivery Hero, 2021 and 2022)

**Story:** *`countWords(text)` returns how many words are in a string. Then write the test
cases yourself.*

**Acceptance Criteria:**
- `null`, empty and blank strings have 0 words
- Several spaces between words count once: `"a   b"` is 2
- Leading and trailing whitespace is ignored
- Tabs and newlines separate words just like spaces
- Punctuation does not make a word: `"Hello, world!"` is 2 and `"a - b"` is 2

**Hints:**
- Agree the definition first. Here a word is a run of non-whitespace characters that contains at
  least one letter or digit
- `text.trim().split("\\s+")` works but returns `[""]` for an empty string, so it counts 1.
  A single pass with a "was I inside a word?" flag has no such trap

---

## Task 2: Find and Replace Pattern (10 min) (reported at Delivery Hero, 2022)

**Story:** *Given a list of words and a pattern, return the words that match the pattern. A word
matches when there is a one-to-one mapping between the pattern's letters and the word's letters
(LeetCode 890).*

**Acceptance Criteria:**
- `words = [aabbcc, aakkff, akkffkk, tjjiijj, deftg]`, `pattern = "mnnssnn"` returns
  `[akkffkk, tjjiijj]`
- The result keeps the input order
- A word of a different length never matches
- Two pattern letters may not map to the same word letter: `"abb"` matches `"xyy"`, not `"xxx"`

**Hints:**
- One map is not enough: `"abc"` would match `"xxx"`. Check both directions, or number each
  string's letters in order of first appearance (`mnnssnn` becomes `0 1 1 2 2 1 1`) and compare

---

## Task 3: OCR-Damaged Compare (12 min) (reported at Delivery Hero, 2021)

**Story:** *An OCR scanner could not read some characters and wrote how many it skipped as a
number: `"A3BCD"` is `A`, then 3 unknown characters, then `BCD`. Given two such strings, decide
whether they could be the same original string.*

**Acceptance Criteria:**
- `A3BCD`, `A6` and `A3BC1` could all be equal to each other (every pair returns `true`)
- A count can have several digits: `"a12"` is `a` followed by 12 unknown characters
- An unknown character matches any letter; unknowns on both sides match each other
- Different total lengths are never equal: `"A2"` vs `"ABCD"` is `false`
- Known letters that differ at the same position are not equal: `"A1C"` vs `"ABD"` is `false`
- Do it with two pointers. Do not expand the strings

**Hints:**
- Keep a counter of pending unknowns for each side. When both are positive, cancel the smaller
  against the larger in one step (`x1000000` vs `x999999y` should not loop a million times)
- When only one side has pending unknowns, each one swallows one letter from the other side

---

## Questions to Ask Afterwards

- What is the Big-O of each task, in time and in extra space?
- How would you test Task 3 exhaustively? (Property-based: for small random inputs, expand both
  strings with a wildcard marker and compare position by position as an oracle)
- What changes for Unicode? (`char` is a UTF-16 unit, so emoji are two of them: iterate
  `codePoints()`, and use `Character.isLetterOrDigit(int)`)
- How would you count words in a 50 GB file? (Stream it with a `BufferedReader` and keep the
  "inside a word" flag across line or buffer boundaries; never load it whole)

## Tips for Interviewers

- In the 2022 report the candidate could not recall Java `String` methods without the IDE's
  autocomplete. Tell candidates to practise with suggestions off: `charAt`, `length`, `isBlank`,
  `Character.isWhitespace`, `Character.isDigit`, `Character.isLetterOrDigit`
- Task 1 is really a testing exercise. If the candidate stops at the happy path, ask "what input
  would break this?" and let them find the empty-string and tab cases themselves
- In Task 3, a candidate who expands the strings has a working answer; ask what happens with
  `a999999999` and steer them to two pointers
