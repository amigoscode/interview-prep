# Interview Tasks — URL Shortener

Sequential user stories, one at a time. Budget **30 minutes**.

---

## Task 1: Shorten and Expand (8 min)

**Story:** *`shorten(longUrl)` returns a short code. `expand(code)` returns the original URL, or
empty when the code is unknown.*

**Acceptance Criteria:**
- Null or blank input is rejected
- Invalid URLs are rejected; "valid" is agreed with the interviewer and written down (the
  solution uses: absolute `http` or `https` with a host)
- Two different URLs never share a code
- `expand` of an unknown code is `Optional.empty()`

---

## Task 2: Idempotent (4 min)

**Story:** *Shortening the same URL twice returns the same code.*

**Acceptance Criteria:**
- Same input, same code
- Surrounding whitespace does not create a second code

---

## Task 3: Pluggable Code Generation (10 min)

**Story:** *Support a counter-based base62 strategy and a random 8-character strategy. Codes must
stay unique either way.*

**Acceptance Criteria:**
- `CodeGenerator` interface, two implementations
- Base62 encodes like an odometer: `0`, ..., `Z`, `10`; `62^7 - 1` is `ZZZZZZZ`
- A random collision is retried, never overwrites an existing mapping

**Hints:**
- Ask: "Do codes need to be unguessable?" That decides counter vs random

---

## Task 4: Many Threads (8 min)

**Story:** *20 threads shorten the same URL at the same time. Exactly one code is created.*

**Acceptance Criteria:**
- A test proves exactly one generator call and one distinct code across 20 threads
- Many threads shortening many URLs stay consistent (`expand(shorten(u)) == u`)

**Hints:**
- `ConcurrentHashMap.computeIfAbsent` is the whole answer. Being able to say why (`get` then `put`
  is two operations with a gap; `computeIfAbsent` is one atomic one) is the point
- Claim the code in the reverse map inside the same function so it can never lag

---

## Questions to Ask Afterwards

- Why base62? How many URLs fit in 7 characters?
- What happens to the counter strategy on two servers? (Id ranges, or a database sequence)
- Where does this live in production and what is the cache?

## Tips for Interviewers

- Watch for `HashMap` in task 4. If it survives, ask what happens under two threads.
