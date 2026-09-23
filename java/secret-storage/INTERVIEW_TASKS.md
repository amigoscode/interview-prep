# Interview Tasks — Secret Storage (Take-Home + Presentation)

This case is usually run as a **take-home followed by a presentation**: the candidate fixes the
repository in their own time, then presents for up to 15 minutes and takes questions. It also works
live. Pick **1-2 tasks** matching the candidate's level, or use the
[presentation question bank](#presentation-and-qa-question-bank) at the bottom to run the Q&A part.

If you are practising as the candidate, attempt the brief in [`README.md`](README.md) first. This file
discusses the answers.

**Sequencing note:** Task 2 is the fix the brief asks for, and every later task assumes it. Tasks 1
and 2 together are a complete junior session. For a mid or senior candidate, hand them Task 2 as
pre-work and start at Task 5 or later.

**The brief rewards restraint.** "Don't over-engineer it" is part of the assessment. A candidate who
adds a feature the brief did not ask for should be able to defend every line of it — Task 11 is
built around exactly that.

---

## Junior Level (15-20 min each)

### Task 1: Find the Problem and Say Who It Exposes

**Context:** `application.yml` holds the admin password in plain text, above a `TODO` promising to
move it later. Spotting it takes seconds. Explaining what it means is the task.

**What to do:**
1. Identify the problem
2. List every place that password can be read from today, not just the source file
3. Prove at least one of them from the command line

**Acceptance Criteria:**
- Names the committed plaintext credential as the problem
- Covers the source tree, **git history**, the **built jar** and the **Docker image**
- Demonstrates one, for example reading the password out of the jar:
  `unzip -p target/*.jar BOOT-INF/classes/application.yml`
- States that removing the line does not make the value safe again: it must be **rotated**

**Hints:**
- Ask: "Could someone who has never seen the source code find this password?" Anyone who can pull the
  image can, because `src/main/resources` is packaged into the jar. The runtime image has no `unzip`,
  so: `docker cp $(docker create secret-storage):/app/app.jar .` and read it locally
- Ask what "repo access equals production access" means for onboarding a contractor
- Ask why the `TODO` comment is itself worth mentioning: temporary secrets are the ones that stay

---

### Task 2: Move the Secret Out of the Repository

**Context:** The password has to come from the environment the app runs in, not from anything that
is committed or built into the image.

**What to do:**
1. Remove the value from `application.yml` and read it from an environment variable
2. Make the app refuse to start, with a clear message, when the variable is missing
3. Give a new developer a way to run it locally without committing anything
4. Update the README so the documented `docker run` command still works

**Acceptance Criteria:**
- No credential anywhere in the working tree
- A missing variable stops startup with a message naming it, rather than starting with an empty
  password or failing on the first request
- A committed `.env.example` with a placeholder value, and `.env` listed in the project's
  `.gitignore`
- The README's run instructions work when followed literally, e.g.
  `docker run --env-file .env -p 8080:8080 secret-storage`

**Hints:**
- `app.admin-password: ${ADMIN_PASSWORD}` already fails fast, but with a placeholder stack trace. Ask
  whether they can make the failure read better (`@ConfigurationProperties` + `@Validated`)
- **Clone their result and follow their README exactly.** The most common failure in real
  submissions is a fix that works on the candidate's machine and a README that still says
  `docker run -p 8080:8080 secret-storage`, which now crashes on startup
- This repository's root `.gitignore` already ignores `.env`, so check the **project's** own
  `.gitignore`: the case is meant to stand alone
- Ask why `ENV ADMIN_PASSWORD=...` in the Dockerfile, or `docker build --build-arg`, would undo the
  fix. Both end up in the image's layer history (`docker history --no-trunc`)

---

### Task 3: Fix the Header Parsing Bugs

**Context:** `HelloController` decodes the `Authorization` header by hand, and gets several details
wrong. None of them is the planted problem, but all of them are found by an interviewer with `curl`.

**What to do:**
1. Send each of these and note the status code:
   - `Authorization: Basic %%%%`
   - `Authorization: basic <valid credentials>` (lowercase scheme)
   - `Authorization: Basic YWRtaW4=` (base64 of `admin`, no colon)
2. Fix every response that is not a `401` or a `200`
3. Make the decoding independent of the platform's default charset

**Acceptance Criteria:**
- A malformed header returns `401`, never `500`
- The scheme is matched case-insensitively (RFC 7235)
- Decoding uses UTF-8 explicitly (RFC 7617)
- The length of the split result is checked **before** any element is read

**Hints:**
- `Base64.getDecoder().decode()` throws `IllegalArgumentException` on bad input. Today nothing
  catches it
- `new String(bytes)` with no charset works on a laptop and breaks in a container with a different
  locale, for any non-ASCII password
- A real submission moved the length check into the condition but read `parts[1]` on the line above
  it. Base64 of `admin` then produced an `ArrayIndexOutOfBoundsException` and a `500`. If the
  candidate has written their own parser, try this header on it
- Ask: which of these would Spring Security have handled for free? All of them

---

### Task 4: Prove It With Tests

**Context:** There are no tests beyond `contextLoads()`, and the Dockerfile builds with
`-DskipTests`. Nothing shows the fix works, or keeps working.

**What to do:**
1. Write tests for the authentication behaviour against the running application
2. Cover the malformed inputs from Task 3
3. Make the test build independent of any real secret

**Acceptance Criteria:**
- No header → `401` with a `WWW-Authenticate` header
- Wrong password → `401`; wrong username → `401`
- Correct credentials → `200`
- Malformed header → `401`
- The tests supply their own test credential; they pass on a machine with no `.env`

**Hints:**
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` plus `java.net.http.HttpClient` exercises the real
  filter chain exactly as `curl` does, with no extra test dependencies
- Supply the test credential with `@SpringBootTest(properties = "...")`, not with a file that could be
  mistaken for real configuration
- Ask which test would have caught the `parts[1]` bug from Task 3

---

## Mid-Level (20-25 min each)

### Task 5: Replace the Hand-Rolled Check With Spring Security

**Context:** Authentication lives inside one controller method. Add a second endpoint tomorrow and it
is public by default: today `GET /anything` answers `404` without ever asking for credentials.

**What to do:**
1. Add `spring-boot-starter-security`
2. Configure HTTP Basic with a `SecurityFilterChain` that denies by default
3. Serve the admin user from configuration through a `UserDetailsService`
4. Delete the parsing code from the controller

**Acceptance Criteria:**
- Every path requires authentication unless explicitly permitted
- `GET /` behaves as before for a client: `401` without credentials, `200` with them
- The controller contains no authentication logic
- No session cookie is issued for Basic auth requests

**Hints:**
- `http.authorizeHttpRequests(a -> a.anyRequest().authenticated()).httpBasic(withDefaults())`
- `sessionManagement(s -> s.sessionCreationPolicy(STATELESS))` for a Basic-only service
- Ask what they would add to allow an unauthenticated `/actuator/health`, and why that line belongs in
  the security configuration rather than in the endpoint
- Ask what Spring Security's `DaoAuthenticationProvider` does for an **unknown** username. It still
  runs a password hash comparison, so a wrong username and a wrong password take the same time

---

### Task 6: Store a Hash, Not the Password

**Context:** Even outside the repository, the environment variable is the password itself. It shows
up in `docker inspect`, in `/proc/<pid>/environ`, in crash dumps, and in Actuator's `/env` if that is
ever exposed.

**What to do:**
1. Store a bcrypt hash in the environment instead of the password
2. Document how a developer generates one
3. Name the variable so the next engineer knows what it holds

**Acceptance Criteria:**
- The app compares against a hash; the plaintext password is not configured anywhere
- The README shows a working command to create a hash
- The variable name says it is a hash, e.g. `ADMIN_PASSWORD_HASH`
- Works when run through `docker run --env-file` **and** documented for `docker compose`

**Hints:**
- `htpasswd -bnBC 12 "" 'your-password' | tr -d ':\n'` produces a `$2y$` hash, which Spring's
  `BCryptPasswordEncoder` accepts
- A bcrypt hash is full of `$`. `docker run --env-file` takes values literally (and keeps any quotes
  as part of the value), while `docker compose` interpolates `$` and needs `$$`. This breaks demos in
  real submissions
- Listen for the words. Bcrypt is **hashing**, not encryption: there is no key and no way back. A
  real submission's PR description said "bcrypt implementation for encrypting"
- Ask the honest question: for a single shared admin password, how much does this buy? (Some: a
  leaked environment exposes a slow, salted hash instead of a working credential. It does not replace
  a secret manager)

---

### Task 7: Fix the Dockerfile

**Context:** The image builds, but it installs an unpinned Maven with `apt-get` on every build, skips
the tests, has no dependency cache layer, and runs the application as root.

**What to do:**
1. Build with a Maven base image or the committed wrapper instead of `apt-get install maven`
2. Cache dependencies in their own layer
3. Run the tests during the build
4. Run the application as a non-root user
5. Add a `.dockerignore`

**Acceptance Criteria:**
- Changing a source file does not re-download dependencies
- A failing test fails the image build
- `docker run --rm --entrypoint id <image>` does not print `uid=0`
- `.env`, `.git` and `target/` are excluded from the build context
- No secret appears in `docker history --no-trunc`

**Hints:**
- `COPY pom.xml .` then `RUN mvn dependency:go-offline` before `COPY src` gives the cache layer
- Ask why `.git` in the build context matters for *this* repository: it holds the old password
- Ask what pinning the base image by digest buys, and what it costs

---

### Task 8: Stop It Happening Again

**Context:** The fix removes one secret. Nothing prevents the next one being committed next month.

**What to do:**
1. Add secret scanning that runs before a commit and in CI
2. Show it catching the original `application.yml`
3. Explain what else in the platform helps

**Acceptance Criteria:**
- A pre-commit hook or config (e.g. gitleaks) that blocks a commit containing the old value
- A CI job that fails on a detected secret, including in history
- Mentions server-side protection: GitHub secret scanning and push protection
- Explains why a scanner is a backstop, not the fix

**Hints:**
- `gitleaks detect --source . --log-opts="--all"` scans history, which is exactly where the old
  password still is
- Ask what a scanner cannot catch: a password with no recognisable pattern, like this one, may need
  a custom rule or entropy settings
- Ask who gets paged when the CI job fails, and what they do next (Task 9)

---

## Senior Level (25-30 min each)

### Task 9: The Secret Is Already Out — Incident Response

**Context:** The repository has been shared with the whole team for months. Assume the password is
known to people who should not know it.

**What to do:**
1. Put the response steps in order
2. Decide whether to rewrite git history, and defend the decision
3. Say how you would know whether the credential was used

**Acceptance Criteria:**
- **Rotation first**: the leaked value must stop working before anything cosmetic happens
- Distinguishes remediation (rotate, revoke) from cleanup (history rewrite)
- Knows history rewriting (`git filter-repo`, BFG) needs a force push, breaks every clone and open
  branch, and does nothing about copies that already exist in forks, CI caches and old images
- Talks about detection: access logs for the endpoint, who pulled the image, and how long the value
  was exposed

**Hints:**
- The trap is starting with `git filter-repo`. It feels decisive and protects nothing
- Ask what they would tell the team, and in what order
- Ask what changes so that next time rotation is a routine operation rather than an incident (Task 10)

---

### Task 10: Design It for Local, CI and Production

**Context:** "Don't commit it, use an environment variable" is the local answer. The brief asks how
the candidate thinks about CI/CD and production too. This task is a design discussion with a
whiteboard, not code.

**What to do:**
1. Describe how the credential reaches the app on a laptop, in CI, and in production
2. Solve "secret zero": how does the app authenticate to wherever the secret lives?
3. Explain how the password is rotated without downtime

**Acceptance Criteria:**
- Local: gitignored `.env` or compose `env_file`, with a dev-only value that is worthless if leaked
- CI: the provider's secret store, injected at run time, masked in logs, never baked into an image
- Production: a secret manager (Vault, AWS Secrets Manager or SSM, Kubernetes Secrets through
  External Secrets), read at startup or mounted as a file
- Secret zero solved with **workload identity** (IAM role, Kubernetes service account, OIDC from CI)
  rather than another credential in configuration
- A zero-downtime rotation scheme, e.g. accept both old and new for an overlap window, then retire the
  old one

**Hints:**
- `spring.config.import=vault://...` or `aws-secretsmanager:...` lets Spring read the secret directly
  at startup
- Ask for the trade-off between environment variables and mounted files (process listings,
  `docker inspect`, child processes, and whether a file can be re-read on rotation)
- Ask what the audit trail should show, and who is allowed to read the production value at all

---

### Task 11: Review a Brute-Force Lockout

**Context:** Candidates often add login protection that the brief did not ask for. This is a
condensed version of one that shipped in a real submission. Hand it over as a code review.

```java
@Service
public class RateLimiter {
    private final Map<String, FailedAttempts> lockouts = new ConcurrentHashMap<>();

    private static class FailedAttempts {
        int count = 0;
        Instant lockedUntil;
    }

    boolean isLockedOut(String ip) {
        FailedAttempts a = lockouts.get(ip);
        return a != null && a.lockedUntil != null && Instant.now().isBefore(a.lockedUntil);
    }

    void recordFailure(String ip) {
        FailedAttempts a = lockouts.computeIfAbsent(ip, k -> new FailedAttempts());
        a.count++;
        if (a.count >= 5) {
            a.lockedUntil = Instant.now().plus(Duration.ofMinutes(15));
        }
    }

    void recordSuccess(String ip) {
        lockouts.remove(ip);
    }
}
```

It is called from the authentication filter with `request.getRemoteAddr()` as the key: check
`isLockedOut`, verify the password, then `recordFailure` or `recordSuccess`.

**What to do:**
1. Find as many problems as you can
2. Rank them by what an attacker, or an unlucky user, would actually hit
3. Say what you would ship instead

**Acceptance Criteria:** finds at least four of these:
- `count++` is a non-atomic read-modify-write; `ConcurrentHashMap` protects the map, not the object
  inside it
- Check-then-act: many parallel guesses pass `isLockedOut` before any failure is recorded, and bcrypt
  takes around 100 ms, so far more than five guesses get through
- `count` is never reset when the lock expires, so after 15 minutes a **single** mistake locks the
  user out again
- Entries are removed only on success, so the map grows without bound; rotating IPs or using IPv6
  exhausts memory
- Behind a load balancer `getRemoteAddr()` is the balancer, so one attacker locks out **everyone**;
  behind office NAT, colleagues lock each other out
- Trusting `X-Forwarded-For` blindly lets an attacker choose their own key
- State is per instance: three replicas allow three times the attempts
- It is a lockout, not a rate limiter — the name is wrong

**Hints:**
- Ask what they would use instead: a bounded cache with expiry (Caffeine), a proven library
  (Bucket4j, Resilience4j), shared state (Redis), or rate limiting at the gateway or WAF
- The strongest answer questions whether it belongs in this change at all, given the brief

---

### Task 12: Retire the Shared Admin Account

**Context:** Even with a perfectly stored secret, there is one account called `admin` that everyone
shares. Nobody can tell who did what, and removing one person's access means rotating it for everyone.

**What to do:**
1. Explain what a shared credential costs, independently of where it is stored
2. Propose a replacement
3. Describe the migration

**Acceptance Criteria:**
- Names the costs: no attribution, no individual revocation, rotation disrupts everyone
- Proposes per-user identity through the company identity provider: OIDC / SSO, e.g. Spring
  Security's `oauth2Login` or a resource server validating tokens from an internal gateway
- Mentions that Basic auth over plain HTTP sends the credential on every request and needs TLS
  regardless
- A migration path that does not lock anyone out mid-way

**Hints:**
- For an internal tool, an identity-aware proxy in front of the app can remove authentication from
  the app entirely. Ask what the app then has to trust, and how
- Ask when a shared service credential *is* the right answer: machine-to-machine, with one owner

---

## Presentation and Q&A Question Bank

For the take-home format: the candidate presents for up to 15 minutes without interruption, then
two interviewers ask questions for about 30 minutes. Note what the presentation **skipped**. The brief
asks for four things: the problem, the fix, the reasoning, and local / CI / production. Start with
whatever was missing.

**Before the call:** clone the candidate's fork, follow their README exactly, and send the malformed
headers from Task 3 at their version. Open with what you found.

| # | Question | Strong answer | Red flag |
|---|----------|---------------|----------|
| 1 | Who could read that password before your change? | Repo, every clone, git history, the jar, the image | "It was hardcoded" and nothing more |
| 2 | You removed it. Is it safe now? | No: still in history and copies; rotate it | "Yes, it's gone" |
| 3 | We followed your README and it didn't start. Why? | Missing variables; README and `.env.example` should have been updated | Blames the interviewer's setup |
| 4 | We sent `Basic YWRtaW4=` and got a 500. Why? | Finds the unchecked array access in their own code, live | Cannot find it |
| 5 | Why bcrypt? What is in that variable now? | A salted, slow hash; the name should say so; `$` escaping | Calls it encryption |
| 6 | Why your own filter rather than Spring Security? | A reasoned trade-off either way | Never considered it |
| 7 | With a wrong username, does the password check still run? | Spots the short-circuit and the timing difference | — |
| 8 | Where does this value come from in production? | Secret manager plus workload identity | "An environment variable" and nothing more |
| 9 | Why not `--build-arg` or `ENV` in the Dockerfile? | Visible in image layers and history | — |
| 10 | How would you rotate it without downtime? | Overlap window accepting old and new | — |
| 11 | How do we stop the next secret being committed? | Pre-commit and CI scanning, push protection | — |
| 12 | The brief said don't over-engineer. What would you remove? | Honest scope judgement | Defends everything |
| 13 | How did you verify it works? | Tests, or honestly says they only used `curl` | Claims tests that do not exist |
| 14 | Anything else in the repo you would change? | Root user in the Dockerfile, skipped tests, HTTP without TLS, shared account | — |

"I don't know, but here is how I would find out" is a good answer. Bluffing is the red flag.

## Common Mistakes in Real Submissions

- The README's run command was left unchanged, so the app **fails to start** for the reviewer
- `.env` used locally but never added to `.gitignore`
- Header parsing rewritten, with the array read on the line **before** the length check
- The old password never described as compromised; no mention of rotation
- A hash stored in a variable still called `ADMIN_PASSWORD`, and described as "encrypted"
- A lockout or rate limiter added outside the brief, with the concurrency and memory bugs in Task 11
- The username moved to an environment variable as though it were a secret, adding a second required
  variable for no security gain
- No tests, and the Dockerfile's `-DskipTests` left in place
- Commit messages and PR descriptions that list what changed but never say why
