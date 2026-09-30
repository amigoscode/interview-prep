# Delivery Hero — Technical Round Practice Map

Every question from our Delivery Hero technical-round research (September 2026), mapped to the exercise in
this repo that practises it. Use it to plan a mock interview or a week of prep.

**The round:** 1 hour, live on CoderPad, any language, framed as pair programming. Typically about 40 minutes of
talk (your projects, Kafka, SQL, microservices) and one easy/medium problem in the last 15 to 20 minutes.
System design is a separate 1-hour round for senior roles. Every later round (hiring manager, bar raiser) also
includes some coding. Source: Delivery Hero's own post,
[Preparing for your Technical Interview](https://deliveryhero.jobs/blog/preparing-for-your-technical-interview-delivery-hero-2/).

**Labels**
- **Reported**: a real candidate said they got this at Delivery Hero.
- **Sister brand**: reported at Glovo, talabat or foodpanda, which run a similar process.
- **Predicted**: our inference from Delivery Hero's engineering blog, job ads and domain. Not confirmed.

Practise without IDE autocomplete. One reported candidate was down-levelled for not recalling basic `String`
methods without it.

---

## Coding questions

| Question | Label | Exercise |
|---|---|---|
| Count the words in a string, then write the tests | Reported (2021, 2022) | [string-drills](string-drills/) Task 1 |
| Find and Replace Pattern (LeetCode 890): `mnnssnn` matches `akkffkk`, `tjjiijj` | Reported (2022) | [string-drills](string-drills/) Task 2 |
| OCR string compare: `A3BCD == A6 == A3BC1`, two pointers | Reported (2021) | [string-drills](string-drills/) Task 3 |
| Largest, second largest, then kth largest | Reported (2022) | [array-drills](array-drills/) Task 1 |
| Maximum Subarray in O(1) space | Reported (2021) | [array-drills](array-drills/) Task 2 |
| All index pairs summing to K, with duplicates | Reported (2021) | [array-drills](array-drills/) Task 3 |
| A binary-search-based problem | Reported (2026) | [array-drills](array-drills/) Task 4 |
| Simple stream-processing problem, data continuously coming in | Reported (2026) | [order-tracking](order-tracking/) Task 3 |
| Implement 2 REST APIs live | Reported (2023) | [order-api](order-api/) Task 1 |
| Implement a CRUD API | Reported (2023) | [order-api](order-api/) Task 2 |
| "A customer sends 500 requests to the same API. What do you do?" | Reported (2023) | [order-api](order-api/) Task 5, deep version in [rate-limiter](rate-limiter/) |
| Fix a small service with 4 failing tests, then make it production-ready | Sister brand (Glovo, 2025) | [fix-the-service](fix-the-service/) Tasks 1 and 2 |
| Convert all keys of a nested map from snake_case to camelCase | Sister brand (Glovo, 2025) | [fix-the-service](fix-the-service/) Task 3 |
| Dinner Plate Stacks (LeetCode 1172) | Sister brand (Glovo, 2025) | [data-structure-drills](data-structure-drills/) Task 4 |
| LRU Cache (LeetCode 146) | Sister brand (Glovo, 2025) | [data-structure-drills](data-structure-drills/) Task 3 |
| Multiply X and Y without `*`, TDD | Sister brand (talabat, 2023) | [data-structure-drills](data-structure-drills/) Task 1 |
| Ordered Set: push, pop, remove, intersect, values, orderedValues | Sister brand (foodpanda, 2022) | [data-structure-drills](data-structure-drills/) Task 2 |
| Rate limiter: `allowRequest(customerId)` | Predicted | [rate-limiter](rate-limiter/) |
| Top-K restaurants by order count from a stream | Predicted | [order-tracking](order-tracking/) Task 4 |
| Merge overlapping intervals (rider shifts) | Predicted | [rider-dispatch](rider-dispatch/) Task 2 |
| Order state machine, reject illegal and out-of-order events | Predicted | [order-tracking](order-tracking/) Tasks 1 and 2 |
| Nearest K riders to a restaurant (heap) | Predicted | [rider-dispatch](rider-dispatch/) Task 1 |
| Moving average / sliding window over a stream | Predicted | [order-tracking](order-tracking/) Task 4 |

---

## Backend and Java concept questions

These are asked out loud. Each is covered in the "Questions to Ask Afterwards" of the exercise listed.

| Question | Label | Where to practise |
|---|---|---|
| Kafka: why use it, architecture, partitioning strategy, key design, consumer groups | Reported (2021, 2023, 2026) | [order-tracking](order-tracking/) |
| SQL/Postgres: `HAVING` vs `WHERE`, range queries, locks; Postgres vs MySQL; transactions | Reported (2023, 2026) | [sql-transfer](sql-transfer/) |
| Java: functional interfaces, abstract class vs interface, OOP, SOLID | Reported (2026) | [fix-the-service](fix-the-service/) |
| Microservices: disadvantages, distributed transactions, caching | Reported (2021, 2023) | [order-api](order-api/), [order-tracking](order-tracking/) |
| MS1 updates DB1, calls MS2 and it's down: how do you restore consistency? | Reported (2021) | [order-api](order-api/) (saga, outbox) |
| Memory leaks and threads; CI/CD; taking a project from design to production | Reported (2021, 2023) | [rider-dispatch](rider-dispatch/), [fix-the-service](fix-the-service/) |
| What is CORS? How do you secure and authenticate a REST API? JWT | Reported (2021), Glovo (2025) | [order-api](order-api/) |
| Kubernetes basics | Reported (2026) | Discussion only |
| Anything on your CV, in depth | Reported (2022) | Discussion only |
| Postgres isolation levels, indexing, finding a slow query | Predicted (job ads) | [sql-transfer](sql-transfer/) |
| Idempotency keys: avoiding a double charge on retry | Predicted | [order-api](order-api/) Task 4 |
| Outbox pattern: publishing an event exactly when the DB commits | Predicted | [order-tracking](order-tracking/) |
| At-least-once delivery, duplicate and out-of-order messages, dead-letter queues | Predicted | [order-tracking](order-tracking/) Task 2 |
| Timeouts, retries with backoff and jitter, circuit breakers, fallbacks | Predicted | [order-api](order-api/) |
| Spring `@Transactional` (proxies, self-invocation, propagation), JPA N+1 | Predicted | [order-api](order-api/) |
| Java concurrency: thread pools, `CompletableFuture`, virtual threads, races | Predicted | [rider-dispatch](rider-dispatch/) Task 4, [order-tracking](order-tracking/) Task 5 |
| Caching with Redis: invalidation, TTLs, stampede | Predicted | [data-structure-drills](data-structure-drills/) (LRU follow-ups) |
| Observability: what to monitor, finding a latency spike | Predicted | [fix-the-service](fix-the-service/) |

---

## System design (separate round, senior and above)

Not coded here. Practise these on a whiteboard: clarify requirements, rough numbers, the simplest design that
works, data model, bottlenecks, failure modes. Start with Postgres + Redis + HTTP and add Kafka only when you
can justify it. A 2026 candidate was rejected for over-engineering with Kafka, Protobuf and gRPC.

| Question | Label | Related exercise |
|---|---|---|
| Design a rate limiter, with DB and caching at scale | Reported (2023) | [rate-limiter](rate-limiter/) |
| Give away N million free burgers in a campaign | Reported (2021) | [order-api](order-api/) (idempotency, rate limits) |
| Order-tracking API (load balancing, caching, consistency) | Reported (2023, lower reliability) | [order-tracking](order-tracking/) |
| Defend your design choices, including why *not* Kafka | Reported (2026) | Any |
| Payment platform, recommendation system, stream processor | Named by Delivery Hero | [order-api](order-api/), [order-tracking](order-tracking/) |
| Design Glovo end to end, rider status pushed every 2 seconds | Sister brand (Glovo) | [rider-dispatch](rider-dispatch/) |
| Real-time chat between rider and customer | Sister brand (foodpanda) | Discussion only |
| Saved-search daily email for 20M users | Sister brand (talabat) | Discussion only |
| Order placement: cart, voucher, payment, restaurant (saga vs orchestration) | Predicted | [order-api](order-api/) |
| Order status tracking and "where is my order" | Predicted | [order-tracking](order-tracking/) |
| Rider dispatch without double assignment | Predicted | [rider-dispatch](rider-dispatch/) |
| Payment platform with idempotent charges and many providers | Predicted | [order-api](order-api/) |
| Quick-commerce (Dmart) inventory without overselling | Predicted | [sql-transfer](sql-transfer/) (locking) |
| Promotions engine: 500k promotions switched on exactly on time | Predicted | Discussion only |
| Menu catalogue: chain menus with outlet overrides | Predicted | Discussion only |
| Feature flags / A/B testing service | Predicted | Discussion only |
| Stream processor: late orders per zone in the last 10 minutes | Predicted | [order-tracking](order-tracking/) Task 3 |
| Surviving the Sunday-dinner spike (3 to 4 times normal traffic) | Predicted | [rate-limiter](rate-limiter/), [order-api](order-api/) |

---

## Sources

- Delivery Hero: [Preparing for your Technical Interview](https://deliveryhero.jobs/blog/preparing-for-your-technical-interview-delivery-hero-2/) (2023),
  [Architecture Reviews](https://deliveryhero.jobs/blog/delivery-hero-architecture-reviews/) (2022),
  [Our Reliability Manifesto](https://deliveryhero.jobs/blog/our-reliability-manifesto/),
  [Event-based architecture (outbox)](https://deliveryhero.jobs/blog/building-event-based-architecture-for-member-system/)
- Candidate reports on LeetCode Discuss: posts 8281420 (2026), 4168834 (2023), 2034030 (2022), 1526122 (2021),
  1618007 (2021), 1413476 (2021), 1201101 (2021); [Delivery Hero Berlin interview experience](https://medium.com/@shilpikumari14049/delivery-hero-berlin-interview-experience-56c3b255119f) (Medium, 2023)
- Sister brands: LeetCode Discuss 6976664, 7378630 and 3889450 (Glovo); talabat and foodpanda write-ups by freezefrancis on Medium
