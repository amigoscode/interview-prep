# Interview Tasks — String Drills

Sequential user stories, one at a time. Budget **45 minutes** for all five. Short on time? The
interviewer picks three, and Task 4 is a good substitute for Task 3.

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

## Task 4: Valid Word Abbreviation (8 min) (reported at Delivery Hero bar raiser, 2026)

**Story:** *`isValidAbbreviation(word, abbr)` returns whether `abbr` is a valid abbreviation of
`word`. A run of digits in `abbr` skips that many characters of `word`; every other character must
match `word` exactly (LeetCode 408).*

**Acceptance Criteria:**
- For `word = "substitution"`: `"s10n"`, `"su3i1u2on"` and `"12"` are valid
- Leading zeros are invalid: `"s010n"` is `false`
- A zero-length skip is invalid: `"s0ubstitution"` is `false`
- A count that runs past the end of the word is invalid: `"13"` and `"s11n"` are `false`
- The abbreviation must cover the whole word: `"s9n"` is `false`
- Do it with two pointers. Do not expand the abbreviation

**Hints:**
- This is Task 3 with only one damaged side: read a whole digit run as one number, then jump the
  word pointer forward by it
- A digit run that starts with `0` is invalid, which covers both the leading-zero and the
  zero-length rules in one check
- Stop as soon as the count exceeds what is left of the word, so `"99999999999"` cannot overflow

---

## Task 5: Palindrome, Then Modern Java (7 min) (reported at Delivery Hero, Blind post, date not given)

**Story:** *`isPalindrome(text)` returns whether `text` reads the same both ways, ignoring case and
anything that is not a letter or digit. Then rewrite it as `isPalindromeWithStreams(text)` using
streams and lambdas, and compare the two.*

**Acceptance Criteria:**
- `"A man, a plan, a canal: Panama"` is `true`; `"race a car"` is `false`
- Case is ignored: `"Aa"` is `true`
- Text with no letters or digits (`""`, `" "`, `".,!?"`) is `true`
- `isPalindrome` uses two pointers moving inwards and O(1) extra space
- `isPalindromeWithStreams` gives the same answer for every input, built from `String.chars()` /
  `IntStream` and lambdas or method references
- Say which version you would ship and why

**Hints:**
- In the two-pointer loop, skip a non-alphanumeric character on one side and `continue`; only
  compare when both sides point at a letter or digit
- `text.chars().filter(Character::isLetterOrDigit).map(Character::toLowerCase).toArray()`, then
  `IntStream.range(0, n / 2).allMatch(i -> c[i] == c[n - 1 - i])`
- The stream version reads like the definition, but it copies the cleaned characters into an
  array: O(n) extra space and a second pass. Two pointers need neither and can stop at the first
  mismatch before reading the whole string

---

## Questions to Ask Afterwards

- What is the Big-O of each task, in time and in extra space?
- How would you test Task 3 exhaustively? (Property-based: for small random inputs, expand both
  strings with a wildcard marker and compare position by position as an oracle)
- What changes for Unicode? (`char` is a UTF-16 unit, so emoji are two of them: iterate
  `codePoints()`, and use `Character.isLetterOrDigit(int)`)
- How would you test Task 4 exhaustively? (For a short word, generate every valid abbreviation by
  hiding or keeping each character, and check random candidates against that set)
- In Task 5, when is the stream version the right choice, and when is it not? (Readability and
  reviewability for normal input sizes; a hot path or a huge string favours two pointers. Would
  `parallel()` help? Not for this: the work per element is tiny)
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
- A candidate failed Task 4 in the second half of a 2026 Delivery Hero bar raiser and was
  rejected. A Delivery Hero bar raiser who replied to that report said they judge how you think,
  structure the problem and communicate trade-offs, not only whether you finish. Ask the candidate
  to talk through the leading-zero and overrun rules before coding
- In Task 5 the reported interviewer wanted to check the candidate is comfortable with lambdas and
  collections. If they only write the loop, ask for the stream version; if they only write the
  stream, ask what it costs in memory and push for two pointers
