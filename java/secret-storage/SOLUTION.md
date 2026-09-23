# Reference Solution — Secret Storage

This is one good answer, not the only one. It is written the way the brief asks candidates to write
theirs: the problem, the fix, the reasoning, and the wider picture. The last section is a 15-minute
presentation built from it.

## 1. The Problem

`application.yml` held the admin password in plain text, under a comment promising to move it later.

That one line exposed the password to far more people than it looks:

| Where | Who can read it |
|-------|-----------------|
| The source tree | Everyone with repository access, including anyone given the code for an unrelated reason |
| **Git history** | Everyone who has ever cloned it, forever. Deleting the line does not remove it |
| **The jar** | Anyone with the build output: `src/main/resources` is packaged into `BOOT-INF/classes` |
| **The Docker image** | Anyone who can pull it. No source access needed: `docker cp` the jar out and unzip it |

So repository access was production access, and the password could not be changed without a commit,
a build and a deploy. In practice that means it never changes, which is what the `TODO` predicts.

**The old password is compromised.** Moving it is the code change; **rotating** it is the fix. If
this were a real service, the first action is to change the password where it is used, before anything
in this repository.

## 2. The Fix

Deliberately small. Every change answers one of the exposures above.

- **The secret left the repository.** `application.yml` now reads `${ADMIN_PASSWORD_HASH}` from the
  environment. Nothing secret is committed, packaged into the jar, or baked into the image.
- **A hash, not the password.** The environment holds a bcrypt hash. If the environment leaks, through
  `docker inspect`, `/proc/<pid>/environ`, a crash dump or an exposed Actuator, it leaks a slow, salted
  hash instead of a working credential. The variable is named `..._HASH` so nobody pastes a password
  into it. Bcrypt is hashing, not encryption: there is no key and no way back.
- **It fails fast, and says why.** `AdminProperties` is validated at startup. A missing value stops
  the app with `is not set: export ADMIN_PASSWORD_HASH`; a plaintext password in the variable stops it
  with `must be a bcrypt hash`. Nothing starts half-configured.
- **Local setup is documented.** A committed `.env.example`, `.env` added to the project's
  `.gitignore`, and README instructions that work when followed literally, including how the `$` signs in a
  hash behave under `--env-file`, docker compose and the shell.
- **Spring Security replaced the hand-rolled check.** This goes slightly beyond the brief, and it is
  the one addition worth defending, because it *removes* code rather than adding it. The controller
  parsed the header itself and got it wrong in four ways: malformed base64 gave a `500`, the charset
  was the platform default, the scheme was case-sensitive, and the comparison was not constant-time.
  It also protected exactly one method: `GET /anything` answered without asking for credentials.
  `httpBasic()` with `anyRequest().authenticated()` fixes all of it and makes every future endpoint
  private by default. For an unknown username Spring still runs a hash comparison, so response time
  does not reveal whether the username exists.
- **Tests prove it.** `AuthenticationTest` drives the real server over HTTP: no credentials, wrong
  password, wrong username, the hash used as a password, malformed base64, no colon, lowercase scheme,
  non-ASCII input, an unknown path, no session cookie. `StartupValidationTest` covers the missing and
  plaintext cases. The tests bring their own throwaway hash and need no `.env`.
- **The Dockerfile.** Builds from a Maven image instead of `apt-get install maven`, caches dependencies
  in their own layer, runs the tests instead of `-DskipTests`, runs as a non-root user, and a
  `.dockerignore` keeps `.env` and `.git` (which still contains the old password) out of the build
  context.

The username stayed in `application.yml`. It is configuration, not a secret, and making it a second
required variable adds friction without protecting anything.

## 3. What Was Deliberately Left Out

The brief says not to over-engineer, so these are described, not built:

- **Brute-force protection.** Worth having, but a correct lockout is harder than it looks (see Task 11
  in `INTERVIEW_TASKS.md`) and it is not a secret-handling problem. It belongs at the gateway or WAF,
  or in a proven library with shared state, as a separate change.
- **A secret manager integration.** The right production answer (below), but the service runs locally
  today and the environment variable is the seam it would plug into.
- **Rewriting git history.** It needs a force push, breaks every clone and open branch, and does
  nothing for copies already in forks, CI caches and old images. Rotation is what protects the
  service. A rewrite is optional cleanup afterwards, if the team agrees.

## 4. Local, CI/CD and Production

**Local development.** A gitignored `.env` from `.env.example`, holding a dev-only credential that is
worthless if it leaks. Developers never hold the production value.

**CI/CD.** The pipeline gets secrets from the CI provider's secret store, injected at run time and
masked in logs, never through `ENV` or `--build-arg`, which end up in the image's layer history. Two
guards so this cannot recur:

- **gitleaks** as a pre-commit hook and as a CI job that also scans history
  (`gitleaks detect --log-opts="--all"`), where the old password still is
- **GitHub secret scanning with push protection**, which blocks a push server-side even if the hook
  was skipped

A scanner is a backstop. A password like this one has no recognisable pattern and may need a custom
rule, so the real protection is that the code no longer has anywhere to put a secret.

**Production.** A secret manager: Vault, AWS Secrets Manager or SSM, or Kubernetes Secrets fed by
External Secrets. Spring can read it directly at startup with `spring.config.import`, or it can be
mounted as a file. The app authenticates to the secret manager with **workload identity**: an IAM
role, a Kubernetes service account, OIDC from CI. That avoids "secret zero", a credential needed to
fetch the credentials. The manager also provides what this repository never could: an audit trail
of who read the value, least-privilege access, and rotation.

**Rotation without downtime.** Accept the old and the new value for an overlap window, switch the
clients, then retire the old one. Once rotation is routine, a leak like this one is an operation,
not an incident.

**The longer term.** A single shared `admin` account gives no attribution and no way to remove one
person's access without disrupting everyone. The real fix is per-user identity through the company's
identity provider (OIDC / SSO), possibly an identity-aware proxy in front of the app. Basic auth also
sends the credential on every request, so it needs TLS whatever else changes.

## 5. Presenting It in 15 Minutes

| Minutes | Content |
|---------|---------|
| 0-2 | The problem as the table in section 1: *who* could read it. End on "it is already compromised: rotate first" |
| 2-6 | Live demo: start without `.env` (clear error), with a plaintext value (clear error), then correctly; `curl` 401 then 200; a malformed header returns 401 |
| 6-8 | The diff, walked briefly: config, `AdminProperties`, `SecurityConfig`, the tests |
| 8-12 | Local, CI/CD and production, including rotation and scanning |
| 12-14 | What was left out and why |
| 14-15 | What comes next: secret manager, SSO, brute-force protection at the edge |

Expect these questions, and have a one-breath answer ready for each:

- *Is the secret still in git history?* Yes. It is compromised and must be rotated; a history rewrite
  is optional cleanup.
- *Why bcrypt, if the environment is already private?* Environments leak in more ways than people
  expect. A hash turns a leak into a slow offline attack instead of instant access.
- *Why Spring Security, when the brief said keep it simple?* It deleted the parsing code and its bugs,
  and made every endpoint private by default. Less code, not more.
- *How does production get the value?* A secret manager, reached with workload identity.
- *What stops this happening again?* gitleaks in pre-commit and CI, push protection, and code that
  no longer has a place for a secret.
