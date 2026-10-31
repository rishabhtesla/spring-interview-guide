# Spring Boot: from main method to a JSON API

[Back to overview](../readme.md) | [Setup guide](../docs/setup-and-troubleshooting.md)

## What this module demonstrates

The application exposes a single hard-coded book through `GET /books`.
It shows how Boot assembles a servlet web application using starters,
auto-configuration, and an embedded server. It is not a database-backed
book-management system.

## Code map

Classes are under
`src/main/java/com/in28minutes/springboot/basics/springbootin10steps/`.

| File | Role |
| --- | --- |
| [SpringbootIn10StepsApplication.java](src/main/java/com/in28minutes/springboot/basics/springbootin10steps/SpringbootIn10StepsApplication.java) | Application entry point and component-scan starting package |
| [BooksController.java](src/main/java/com/in28minutes/springboot/basics/springbootin10steps/BooksController.java) | Maps `GET /books` to a list of books |
| [Book.java](src/main/java/com/in28minutes/springboot/basics/springbootin10steps/Book.java) | Java record holding `id`, `name`, and `author` |
| [pom.xml](pom.xml) | Boot parent, web and Actuator starters, development and test dependencies |
| [application.properties](src/main/resources/application.properties) | Configuration file; includes a legacy management property |

## Request flow

```text
GET /books
  -> embedded servlet server
  -> DispatcherServlet
  -> BooksController.getAllBooks()
  -> List<Book>
  -> Jackson HTTP message converter
  -> JSON array in the response body
```

`@RestController` combines controller registration with response-body handling.
The returned value is data, not a JSP view name. Jackson serializes the
record's components; the custom `toString()` is not what produces the JSON.

`List.of(...)` returns an unmodifiable list. The controller constructs one
record on each call; there is no service, repository, entity, or CRUD workflow.
The JPA starter in the POM is commented out.

## Run and observe

Use JDK 17. The parent is Boot `3.0.0-M4`, an old milestone; this is not a
template for choosing a production Boot version.

```bash
mvn -f 4.springboot-in-10-steps/pom.xml spring-boot:run
```

After successful startup, in another terminal:

```bash
curl -i http://localhost:8080/books
curl -i http://localhost:8080/actuator/health
```

`/books` should return HTTP 200 and a JSON array with this data (whitespace
and property order are not significant):

```json
[
  {
    "id": 1,
    "name": "Mastering Spring 5.2",
    "author": "Ranga Karanam"
  }
]
```

Actuator provides health information when the application starts successfully.
Other Actuator endpoints are not necessarily exposed over HTTP by default.
The existing `management.security.enabled=false` is an obsolete property;
do not assume it disables management security in Boot 3.

Stop with Ctrl+C before starting another web module; they otherwise compete
for port 8080.

## Interview questions with worked answers

**Is Spring Boot a replacement for Spring MVC?**
No. MVC handles request routing and responses. Boot configures and starts
the application, often using MVC internally. This endpoint is a Boot-launched
MVC application.

**What does `@SpringBootApplication` combine?**
Boot configuration, auto-configuration, and component scanning. Scanning
normally starts at the annotated class's package. A controller outside that
package tree needs explicit scanning or import configuration.

**How does auto-configuration decide what to create?**
It evaluates conditions such as classes on the classpath, configuration
properties, and existing beans. Many defaults back off when you provide your
own bean. A starter brings related dependencies; auto-configuration supplies
conditional configuration. These are complementary, not identical.

**Why does this application not need a manually installed Tomcat?**
The web starter normally includes embedded Tomcat, which Boot starts with the
application. Traditional WAR applications rely on a separate servlet
container. Embedded does not mean "no server"; the server runs in the same
process.

**What is the difference between the parent and a starter?**
The parent provides dependency and build defaults. A starter is a convenient
set of dependencies for a capability such as web or testing. Dependency
management aligns versions; it does not add every managed library to the
classpath.

**Why use a record here, but be careful with JPA entities?**
A record is a concise immutable data carrier suitable for this response.
Traditional JPA entities need persistence-provider-compatible construction
and state management; this `Book` is not an entity.

## Practice tasks

1. Enable debug startup reporting and identify why MVC and the embedded
   server were configured. Explain what would change without the web starter.
2. Add a controller test asserting status 200, JSON content type, array size,
   and all three book fields. `contextLoads` alone does not check these.
3. Add `GET /books/{id}` with an explicit 404 for a missing book. Keep a
   service between controller and storage when introducing business rules.
4. Add a profile-specific property and explain configuration precedence
   rather than putting environment-specific values into controller code.

**Ready to move on:** trace a Java return value into JSON and distinguish
Spring, MVC, Boot, a starter, and auto-configuration.
