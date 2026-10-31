# Spring interview handbook: understand the "why"

[Overview](../readme.md) | [Learning path](learning-path.md) | [Setup](setup-and-troubleshooting.md)

This handbook gives worked explanations for the main concepts illustrated
here and foundations for related interview topics. It is not an answer key
for every question in the original catalogue. Version-specific APIs must be
interpreted against the module's POM, not copied into a newer application.

## 1. Spring Core and dependency injection

### What are IoC and DI?

**Inversion of Control** transfers responsibility for creating and assembling
objects from application code to a container. **Dependency Injection** is a
technique where collaborators are supplied to an object rather than created
inside it.

In module 2, `BinarySearchImpl` needs a `SortAlgorithm`. Spring creates the
beans and injects the selected strategy. The dependency still exists; what
changes is who supplies it and how tightly the caller is coupled to its
implementation.

**Interview answer:** "DI makes collaborators explicit and replaceable. A
search class can use any strategy implementing `SortAlgorithm`; a test can
provide a fake. Spring's container performs the wiring at startup."

### What is a bean, and what does the container do?

A bean is an object managed by Spring. `ApplicationContext` provides bean
creation, configuration, dependency resolution and lifecycle facilities,
along with capabilities such as events and message resolution.
`BeanFactory` is the core bean-container interface; `ApplicationContext`
extends those capabilities and is normally used by applications.

Do not claim that any object created with `new` automatically becomes a bean.
In module 4, the controller is managed, but each `new Book(...)` is simply a
response data object.

### How does component scanning work?

Spring scans configured packages for component candidates such as
`@Component`, `@Service`, `@Repository` and `@Controller`. Boot normally starts
scanning at the package containing `@SpringBootApplication`.

Common stereotypes communicate responsibility: service for business
operations, repository for persistence, controller for web handling.
`@Repository` can also participate in persistence exception translation when
the relevant infrastructure is configured. An annotation is not proof that
the class actually uses a database: `UserDaoService` is a list-backed component.

Use `@Bean` methods in configuration when you want explicit construction,
particularly for third-party objects you cannot annotate. SOAP configuration
uses them for the servlet, schema and WSDL definition.

### Constructor, setter or field injection?

Prefer constructor injection for required collaborators: requirements are
visible, fields can be final, and ordinary unit tests can construct the object.
Setter injection can suit genuinely optional or reconfigurable dependencies.
Field injection hides requirements and makes plain construction harder.

This is a conceptual refactoring of module 2, **not its current implementation**:

```java
@Component
class BinarySearchImpl {
    private final SortAlgorithm sortAlgorithm;

    BinarySearchImpl(SortAlgorithm sortAlgorithm) {
        this.sortAlgorithm = sortAlgorithm;
    }
}
```

With one constructor, modern Spring does not require `@Autowired` on that
constructor. A test can pass a fake implementation. Constructor injection
does not itself solve an ambiguous bean choice.

### How do `@Primary` and `@Qualifier` differ?

Spring first needs candidates matching the required type. A qualifier narrows
that set; a primary candidate supplies a default preference when applicable.
They do not mean "load this bean earlier".

Module 2 has two sorting components and marks bubble sort primary. Without a
qualifier or primary, this injection point is ambiguous. A missing candidate
and multiple candidates are different problems: inspect scanning,
configuration and the injection point before adding annotations at random.

### What is bean scope? Are singletons thread-safe?

Singleton means one bean instance per bean definition per container, not one
instance across the entire JVM. Prototype creates a new instance when the
container is asked to resolve that bean. Web-aware contexts also support
request and session scopes.

Scope controls lifecycle/identity, not concurrency safety. Module 3's static
todo list and counter can be modified by concurrent requests. Stateless
services are easier to share safely; mutable shared state needs an explicit
concurrency and persistence design.

Injecting a prototype into a singleton normally resolves it when the singleton
is created; it does not magically create a fresh dependency on every method
call. A provider or scoped proxy may be needed for different lifecycle needs.

### What happens in the bean lifecycle?

At a high level: instantiate, populate dependencies, apply lifecycle and
post-processing callbacks, then expose the usable bean (possibly a proxy).
Initialization and destruction callbacks allow resource setup/cleanup.
The container does not manage the complete destruction lifecycle of prototype
beans in the same way as singleton beans.

**Follow-up:** circular dependencies often indicate misplaced responsibilities.
Constructor cycles fail because neither object can be constructed first;
redesigning the collaboration is usually better than hiding the cycle.

## 2. Spring MVC and Spring Boot

### Explain a request from browser to controller

For a servlet MVC application: filters run, the `DispatcherServlet` delegates
handler lookup, an adapter invokes the handler after argument resolution, and
the result is rendered or written through a message converter.

Module 3 returns a logical JSP name and model data. Module 4 returns
`List<Book>` as the response body. Both use MVC request handling; only one
uses server-rendered views. Business logic should not be placed inside the
front controller or JSP.

### Binding versus validation versus exception handling

**Binding** converts input into method arguments or form objects.
**Validation** evaluates declared constraints.
**Exception handling** maps failures to an appropriate page or API response.

Malformed JSON can fail conversion before bean validation. A short valid JSON
name can deserialize successfully and then fail `@Size`. `@Size` does not
reject null; required values need appropriate constraints such as `@NotNull`
or `@NotBlank`.

Module 3 handles form errors with adjacent `BindingResult` and redisplays
the form. Module 6 centralizes API validation errors in controller advice.
Browser validation is a convenience, never the only input defence.

### `@PathVariable`, `@RequestParam`, `@RequestBody`, or model attribute?

| Mechanism | Typical use | Repository example |
| --- | --- | --- |
| `@PathVariable` | Identifier in a route | `/users/{id}` |
| `@RequestParam` | Query/form parameter | MVC `/update-todo?id=1` |
| `@RequestBody` | Deserialize a body through a converter | REST create-user JSON |
| Model attribute binding | Populate form objects and view data | MVC `Todo` form |

Choose based on the public request contract. A Java type alone does not tell
Spring whether data should come from JSON or form fields.

### Spring versus Spring MVC versus Spring Boot

Spring Framework includes the container and modules. Spring MVC is its
servlet web framework. Boot supplies opinionated startup and conditional
configuration around Spring applications.

Module 3 configures MVC explicitly with XML and a WAR. Module 4 starts from a
Boot main method and a web starter. Boot does not eliminate MVC, and adding
Boot does not automatically turn an application into microservices.

### Starter, parent, BOM and auto-configuration

A **starter** brings a useful dependency set. A **parent** supplies Maven build
and dependency defaults. A **BOM** manages a coordinated set of versions.
**Auto-configuration** registers configuration conditionally based on
classpath, properties and existing beans.

Dependency management does not add every library it manages. Including a
library does not mean every possible feature is enabled. Use Boot's condition
report to investigate why configuration matched or backed off.

### Configuration, profiles and Actuator

External configuration separates environment values from code. Properties,
environment variables and command-line inputs have defined precedence;
check the documentation for the Boot version you use. Profiles select
environment-specific bean/configuration sets; they are not secret storage.
`@ConfigurationProperties` groups typed configuration instead of scattering
unrelated string lookups across classes.

Actuator exposes operational capabilities such as health and metrics.
Endpoint enablement, HTTP exposure and authorization are separate concerns.
Module 6's wildcard web exposure and H2 console are demo settings, not
production defaults to copy.

## 3. REST and HTTP API design

### What makes an API RESTful?

REST is an architectural style involving resources, representations and
constraints including stateless interaction and a uniform interface.
HTTP endpoints returning JSON are not automatically a complete REST design.

Use resource-oriented paths, appropriate methods and meaningful statuses.
Stateless interaction means each request carries the information needed for
processing; it does not mean the server has no database or cannot store data.

### HTTP methods, safety and idempotency

GET is intended to be safe: it should not request state changes. PUT and
DELETE are idempotent in intended effect; repeating the same request should
not create additional different effects. POST is not generally idempotent.
Idempotency does not require every repeated response to have the same status
or body.

Module 3's GET deletion is a teaching limitation. A crawler or prefetcher
could follow a link; use a protected state-changing request instead.
For retryable create operations, an idempotency-key design may be appropriate.

### Which status should I return?

| Status | Meaning | Example |
| --- | --- | --- |
| 200 | Successful request with a normal response | List users |
| 201 | Resource created | Create user with `Location` |
| 204 | Successful response with no body | An explicitly designed delete response |
| 400 | Invalid client input | Validation or request parsing failure |
| 401 | Authentication required/failed | Missing valid credentials |
| 403 | Request forbidden | Insufficient permissions or a security rejection |
| 404 | Resource not found | Unknown user ID |
| 409 | Conflict with current state | A designed duplicate/consistency conflict |
| 500 | Unexpected server failure | Unhandled internal error |

These are semantics, not a promise that every demo implements them.
Module 6 explicitly returns 201 with `Location` for creation. Its `void`
delete method does not explicitly choose 204. Do not claim that Java return
types alone define every HTTP status.

### How should errors be designed?

Use a stable error contract and correct status. Validation errors should help
clients correct input without exposing stack traces or database details.
Central advice can keep API error handling consistent; local controller
handlers can handle controller-specific cases.

Module 6 builds `ExceptionResponse` in shared advice. Its catch-all response
is a demo, not a recommendation to expose raw exception messages. Modern
Spring also supports Problem Details representations; adopting them in this
old module requires compatible framework APIs.

### Content negotiation, filtering and versioning

`Content-Type` describes the request representation; `Accept` describes what
the client wants back. Unsupported input can lead to 415; no acceptable
response representation can lead to 406.

Jackson filtering chooses fields for a response. Module 6 uses different
field selections for a single bean and a list. This is not a substitute for
authorization or a carefully designed response DTO.

Versioning helps evolve a contract without silently breaking clients.
URI versions are visible and easy to explore; header/media-type versions keep
paths stable but require client/cache awareness. Choose consistently and
plan deprecation. Module 6 demonstrates mechanics, not a rollout policy.

### What are HATEOAS and localization?

HATEOAS includes links that guide clients to related actions/resources.
The single-user response in module 6 adds `all-users`. Links should be
meaningful and do not bypass authentication or resource authorization.

Localization chooses language-specific messages. The REST application's
locale resolver reads `Accept-Language`, and its message source looks up
`good.morning.message` in resource bundles. Localization is different from
changing API versions or translating database schemas.

## 4. Persistence: JDBC, JPA and Spring Data

### JDBC versus Spring JDBC versus JPA

JDBC gives direct database access with SQL and connection/statement/result
handling. Spring JDBC's `JdbcTemplate` reduces resource-handling boilerplate
and translates database exceptions; a `RowMapper` maps rows to objects.
You still design and write the SQL.

JPA specifies object-relational persistence. Hibernate is a provider.
Spring Data JPA generates repository implementations on top of JPA.
H2/PostgreSQL/MySQL are databases, not JPA providers.
This repository demonstrates JPA in module 6 but has no standalone
`JdbcTemplate` implementation.

### What is an entity and persistence context?

An entity has persistent identity and mappings. The persistence context tracks
managed entities; changes to managed state can be synchronized with the
database through dirty checking. Detached objects are no longer managed by
that context.

`save` is not always "run an INSERT now": provider behaviour, entity state,
flushing and transaction boundaries matter. A DTO is an external data
contract, not automatically an entity. Module 4's `Book` record is a DTO-like
response object with no persistence mapping.

### Explain relationship ownership

In module 6, `Post.user` owns the relationship; `User.posts` declares
`mappedBy="user"` as the inverse side. The create-post controller sets
`Post.user` before saving so the association can be persisted.

In bidirectional models, keep both in-memory sides consistent as needed.
Cascade choices govern propagation of persistence operations; they are not
the same as fetching choices. Do not enable removal cascade casually when
deleting one entity could remove important related data.

### Lazy loading and the N+1 problem

Lazy fetching delays loading associated data until access. It can avoid
unneeded work, but access after the persistence context closes can fail.
Serializing entities can also cause accidental queries.

N+1 occurs when one query loads a collection and additional queries load
related data for each result. Diagnose using SQL/query counts, then choose
fetch joins, entity graphs, DTO projections or batching for the use case.
Making everything eager can load too much and still does not guarantee
efficient SQL.

### Transactions and `@Transactional`

A transaction groups operations into a unit that succeeds or fails together
under the database's isolation rules. Put multi-step business operations at
appropriate service transaction boundaries rather than relying on separate
repository calls to form one atomic workflow.

Spring's common declarative approach uses proxies. Calls must go through the
proxy for advice to apply; self-invocation is a classic pitfall. Default
rollback rules normally include unchecked exceptions and errors, not every
checked exception. Configure rules intentionally.

`readOnly=true` expresses a read-only intent/optimization hint; it is not a
universal access-control mechanism. Module 6 does not demonstrate an explicit
service-level transaction spanning a larger business workflow.

### How would you prevent lost updates?

Use database constraints and a deliberate concurrency strategy. Optimistic
locking commonly uses a version field to detect conflicting updates;
pessimistic locking acquires database locks with different contention risks.
An incrementing static Java counter is not a distributed ID strategy.
These are design extensions, not features implemented by the list demos.

## 5. SOAP, AOP, security and testing

### Explain SOAP contract-first development

Define the XSD message contract, generate Java models, implement endpoint
mapping, and expose a WSDL describing service access. SOAP wraps XML messages
in an envelope; faults represent processing failures.

Module 5 routes using payload namespace/local name. It maps internal `Course`
objects to generated `CourseDetails`. Do not manually edit generated classes
to contradict the schema. An empty response collection forbidden by the XSD
is a contract mismatch even if Java successfully serializes it.

### SOAP versus REST: what is a fair comparison?

SOAP is a protocol with an XML envelope and a standards ecosystem; REST is an
architectural style commonly implemented over HTTP. SOAP can suit contract
and WS-* interoperability needs; HTTP resource APIs often suit broadly
accessible clients and web tooling. Either can be poorly designed or insecure.
Choose based on requirements, not "SOAP is always slow" or "REST is always JSON".

### What is AOP, and what is the proxy pitfall?

Aspect-oriented programming separates cross-cutting concerns such as
transaction advice, auditing or timing from business methods. An aspect
contains advice; a pointcut selects join points where it applies.

Spring AOP commonly intercepts method calls through proxies. A call from one
method directly to another on the same instance bypasses that proxy path.
AspectJ weaving has broader capabilities than Spring's proxy-based AOP.
This repository's catalogue covers AOP, but there is no dedicated custom
aspect implementation to run.

### Authentication, authorization and CSRF

Authentication establishes who is making a request. Authorization decides
whether that identity may perform a particular action on a particular resource.
Logging in is not proof of ownership of every todo ID.

CSRF concerns browsers sending ambient credentials such as cookies on an
attacker-induced request. Do not disable CSRF reflexively for every API;
consider how the client authenticates. CORS is a browser cross-origin policy,
not authentication and not a replacement for CSRF/authorization design.

For production, use appropriately encoded passwords, external secrets,
restricted diagnostic endpoints and transport protection. Module 3's legacy
adapter/no-op password setup and module 5's demo UsernameToken policy are not
deployment templates. Modern security uses `SecurityFilterChain` beans.

### What should the tests actually prove?

| Test scope | What it proves | Useful example |
| --- | --- | --- |
| Plain unit | Business behaviour with isolated collaborators | Real search and fake sorting strategy |
| MVC slice / MockMvc | Routing, conversion, validation and HTTP response | `/books` shape or create-user 400 |
| Repository integration | Mappings and database behaviour | Persist a post with its user |
| Full application context | Wiring and integration of selected components | Startup plus an explicitly exercised workflow |
| SOAP endpoint/contract | Payload routing, XML shape and faults | Lookup and empty-list contract case |

`MockMvc` exercises the MVC infrastructure without requiring a real HTTP
server. `@WebMvcTest` selects a web-focused context; collaborators must be
provided appropriately. `@SpringBootTest` loads a broader Boot context and
only starts a real server when configured to do so.

The current `contextLoads` methods contain no business assertions. They can
detect startup failures, not correct sorting, status codes or relationship
semantics. Older modules use JUnit 4; modules 2 and 4 use JUnit Jupiter.
Mocking APIs also vary by Spring generation: use the version-compatible
facility rather than copying every old `@MockBean` example unchanged.

## 6. Answering a scenario question

**Interviewer:** "The app starts, but create-user requests fail. How do you
investigate?"

**A strong response:** "First I capture status, headers, body and the relevant
log entry. A 401 points to authentication before controller execution; a 403
may indicate authorization or CSRF. A 415 suggests content type; a 400 may
mean JSON binding or constraint validation. I confirm the correct application
and route, reproduce with a small request, then write a focused test for the
layer that failed. I do not disable security or catch every exception merely
to return 200."

**Interviewer:** "How would you make these demos production-ready?"

**A strong response:** "I would first define the public contract and required
behaviour, then align supported dependencies, isolate API DTOs from entities,
add service transaction boundaries, durable storage, resource authorization,
safe error responses and observability. I would verify these through unit,
web, persistence and integration tests. The current collection is educational;
I would not describe a static list or a passing context-load test as production
readiness."
