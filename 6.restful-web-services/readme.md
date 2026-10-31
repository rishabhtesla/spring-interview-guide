# REST and JPA: understand an API end to end

[Back to overview](../readme.md) | [Interview handbook](../docs/interview-handbook.md)

## What this module teaches

This Boot `2.0.0.RELEASE` application demonstrates HTTP resources, JSON/XML
conversion, validation, exception mapping, links, localization, versioning,
response filtering and persistence. It has **two separate user stores**:

```text
/users       -> UserResource    -> UserDaoService -> static Java list
/jpa/users   -> UserJPAResource -> repositories   -> JPA/Hibernate -> H2
```

Creating a user under `/users` does not create that user under `/jpa/users`.
The first is a simplified DAO demo; the second demonstrates persistence APIs.
The default H2 setup is in-memory, so it is not durable across restarts.

## Read the code in this order

All Java paths below are relative to
`src/main/java/com/in28minutes/rest/webservices/restfulwebservices/`.

| File | What to learn |
| --- | --- |
| [RestfulWebServicesApplication.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/RestfulWebServicesApplication.java) | Startup and `AcceptHeaderLocaleResolver` |
| [helloworld/HelloWorldController.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/helloworld/HelloWorldController.java) | Plain string, object body, path variable and translated greeting |
| [user/UserResource.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/user/UserResource.java) | CRUD mappings, 201 `Location`, validation and HATEOAS |
| [user/UserDaoService.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/user/UserDaoService.java) | List-backed storage; no SQL |
| [exception/CustomizedResponseEntityExceptionHandler.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/exception/CustomizedResponseEntityExceptionHandler.java) | Shared 400, 404 and 500 response mapping |
| [user/User.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/user/User.java), [user/Post.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/user/Post.java) | Entities, constraints and bidirectional relationship |
| [user/UserJPAResource.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/user/UserJPAResource.java) | Repository-backed requests and creating a user's post |
| [user/UserRepository.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/user/UserRepository.java), [user/PostRepository.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/user/PostRepository.java) | Spring Data repository interfaces |
| [filtering/FilteringController.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/filtering/FilteringController.java) | Per-response Jackson filters |
| [versioning/PersonVersioningController.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/versioning/PersonVersioningController.java) | Four ways to select response versions |
| [SwaggerConfig.java](src/main/java/com/in28minutes/rest/webservices/restfulwebservices/SwaggerConfig.java) | Legacy Springfox Swagger 2 documentation configuration |

## Trace user creation

```text
POST /users with JSON
  -> Spring Security filters
  -> DispatcherServlet selects createUser
  -> HTTP message converter deserializes JSON into User
  -> @Valid applies declared constraints
  -> UserDaoService.save assigns an ID if absent and appends to the list
  -> controller builds the new resource URI
  -> HTTP 201 Created, Location header, no response body
```

If validation fails, the shared exception handler returns 400 instead of
calling the service. `@Size(min=2)` and `@Past` check supplied non-null values;
they do not make name and birth date mandatory. Add required-field constraints
when designing a stricter contract.

For a lookup, a missing list-backed user triggers `UserNotFoundException`,
mapped to 404. A successful single-user response also includes an `all-users`
link. HATEOAS adds navigation links, not permission to access their targets.

## Endpoint reference

| Request | What to observe |
| --- | --- |
| `GET /hello-world` | Plain greeting |
| `GET /hello-world-bean` | Greeting wrapped in an object |
| `GET /hello-world/path-variable/Alex` | Path variable in the response |
| `GET /hello-world-internationalized` | Message selected using the request locale |
| `GET /users`, `GET /users/1` | List-backed users; single-user response has a link |
| `POST /users`, `DELETE /users/1` | List-backed creation/deletion |
| `GET /jpa/users`, `GET /jpa/users/10001` | H2-backed users |
| `POST /jpa/users`, `DELETE /jpa/users/10001` | Repository-backed creation/deletion |
| `GET /jpa/users/10001/posts`, `POST /jpa/users/10001/posts` | A user's posts |
| `GET /filtering` | Only `field1` and `field2` |
| `GET /filtering-list` | Only `field2` and `field3` for each item |
| `GET /v1/person`, `GET /v2/person` | Version in the URI |
| `GET /person/param?version=1` | Version in a query parameter |
| `GET /person/header` with `X-API-VERSION: 1` | Version in a custom header |
| `GET /person/produces` with `Accept: application/vnd.company.app-v1+json` | Version in the requested media type |

Versions 1 and 2 demonstrate a flat name versus a structured name, not two
different database schemas.

## Run and try requests

Use JDK 8 for this legacy example and stop other applications on port 8080:

```bash
mvn -f 6.restful-web-services/pom.xml spring-boot:run
```

Security is included. The username is configured in `application.properties`;
the password should be checked locally in that configuration or the startup
log if Boot generates one. The commands below let curl prompt for it rather
than putting it in shell history:

```bash
curl -i -u username http://localhost:8080/users
curl -i -u username \
  -H 'Accept-Language: fr' \
  http://localhost:8080/hello-world-internationalized
curl -i -u username \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alex","birthDate":"1990-01-01"}' \
  http://localhost:8080/users
curl -i -u username http://localhost:8080/users/99999
```

After successful authentication, expect 200 for listing, a translated
greeting, 201 with a `Location` for creation, and 404 for the absent ID.
Try a one-character name to observe 400 validation handling. If you receive
401, diagnose authentication before controller logic; if a state-changing
request receives 403, inspect the active security/CSRF configuration rather
than simply disabling protection.

The historical Swagger UI path is `/swagger-ui.html`; API JSON is typically
at `/v2/api-docs`. These depend on successful startup and security access.
They use old Springfox APIs, not a modern OpenAPI configuration.

## Understand JPA using the user/post example

`User` is an `@Entity` with an `@Id` and generated identifier.
`UserRepository extends JpaRepository<User, Integer>` lets Spring Data create
the repository implementation. Hibernate handles persistence behind the JPA
API; H2 is the actual database in this demo.

`User.posts` uses `@OneToMany(mappedBy="user")`. The `mappedBy` value refers to
the Java field `Post.user`, not a database column. `Post.user` is the owning
`@ManyToOne` side. The create-post handler sets that side before saving.
`@JsonIgnore` on `Post.user` prevents serializing the back-reference; it does
not remove the database relationship.

The post-to-user association is explicitly lazy. Accessing lazy state requires
an available persistence context, and serializing entities can trigger queries.
Do not rely on controller serialization or open-session behaviour as your
production fetching strategy. Prefer deliberate queries and DTO mapping.

[data.sql](src/main/resources/data.sql) seeds users with IDs 10001-10003 and
two posts for user 10001. Those records differ from the list's IDs 1-3.
Deleting a user with posts needs a deliberate foreign-key/cascade policy;
no removal cascade is declared here.

## What this demo does not guarantee

The mutable static list and ID counter are not thread-safe. Request bodies
use persistence entities directly rather than a separated API DTO. Delete
methods return `void`, so do not claim they explicitly send 204; the controller
does not set that status. JPA deletion does not perform the same explicit
missing-user check as the list-backed endpoint.

The general exception handler exposes exception messages and validation
internals. Production error responses should be stable and avoid leaking
implementation details. Actuator exposes all web endpoints through `*`, and
the H2 console is enabled; these are local-learning settings, not safe
deployment defaults. Dynamic field filtering is representation control, not
an authorization system.

## Interview practice

1. Explain 400 versus 401 versus 403 versus 404 versus 500 with a request
   from this module. Describe 201 and `Location` for creation.
2. Compare `/users` with `/jpa/users` without confusing the stores.
3. Explain why setting `post.setUser(user)` matters and why `mappedBy` belongs
   on the inverse side.
4. Compare URI and header versioning: discuss discoverability, caching,
   client tooling and backward compatibility.
5. Design tests for validation, missing resources, links, filtering, locale,
   persistence and unauthorized access. The existing `contextLoads` test
   exercises startup only.

**Ready for the mock interview:** trace both a successful request and a
failure from the security filters through to the final HTTP response.
