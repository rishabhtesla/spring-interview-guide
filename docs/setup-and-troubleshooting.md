# Setup and troubleshooting

[Overview](../readme.md) | [Learning path](learning-path.md)

## Before you run anything

These are independent teaching modules with mixed dependency generations.
There is no root `pom.xml`, Maven wrapper, shared JDK requirement, or promise
that every historical example starts unchanged on a current machine.

Install Maven and select an appropriate JDK in your shell and IDE. Check:

```bash
java -version
mvn -version
```

`mvn -version` shows the JDK Maven actually uses; an IDE may use another one.
Do not try to solve an old API incompatibility by repeatedly switching random
dependencies. Read the module POM, imports and first meaningful error together.

| Module | Declared baseline | Important compatibility detail |
| --- | --- | --- |
| `2.spring-in-10-steps` | Java 17, Boot 3.0.0-M4 | Old milestone parent and repositories |
| `3.spring-mvc` | Java 17, Spring MVC 5.3.20, WAR | Old Tomcat 7 plugin; `javax`/Jakarta validation mismatch |
| `4.springboot-in-10-steps` | Java 17, Boot 3.0.0-M4 | Record-based response; old milestone repositories and management property |
| `5.soap-web-services` | Java 8, Boot 2.0.0.RELEASE | Old JAXB generation and XWSS security stack |
| `6.restful-web-services` | Java 8, Boot 2.0.0.RELEASE | Old HATEOAS/Springfox APIs and H2 initialization conventions |

Java 8 is the conservative starting environment for the Boot 2.0 examples,
not a claim that every newer JDK will work or that Java 8 is a modern
deployment recommendation. Boot 3's baseline is Java 17, but this repository
uses an early milestone, not a maintained stable Boot release.

## Build one module at a time

From the repository root, select the relevant POM:

```bash
mvn -f 2.spring-in-10-steps/pom.xml test
mvn -f 4.springboot-in-10-steps/pom.xml test
mvn -f 6.restful-web-services/pom.xml test
```

These are alternatives for their respective JDK environments, not a single
cross-version test suite. The existing tests are context-startup checks;
passing them does not prove all endpoints or algorithms are correct.

Boot applications use `spring-boot:run`. The MVC WAR's original command is
`tomcat7:run`; see its walkthrough before using that legacy path.
In an IDE, import each module as a Maven project and select its JDK. Boot
modules have an application class with `main`; the MVC WAR does not.

For web modules, the default port is 8080 unless overridden. Run only one on
that port at a time. To try an alternative Boot port:

```bash
mvn -f 4.springboot-in-10-steps/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments=--server.port=8081
```

Use the corresponding port in curl/browser URLs. This Boot argument is not
the configuration mechanism for the traditional MVC Tomcat plugin.

## Diagnose the symptom, not just the final error

| Symptom | What to check first |
| --- | --- |
| "No POM" / missing project | Run in a module or pass `-f module/pom.xml` |
| Parent/dependency cannot be resolved | Network/repository access, exact coordinates, milestone repository availability; an HTML login page is not a Maven artifact |
| Invalid target release / record syntax error | Maven's JDK; modules 2-4 require Java 17 |
| JAXB class/tool missing | Selected JDK and SOAP generation plugin; JAXB was removed from the JDK in Java 11 |
| MVC validation import/provider failure | Spring 5 code uses `javax.validation`, Validator 7 uses Jakarta Validation; the API and provider must match |
| MVC target-date binding failure | `LocalDate` field versus the registered `Date` editor |
| JSP form/tag or rendering failure | Spring tag-library compatibility, view resolver prefix/suffix, and actual JSP path |
| Port already in use | Stop your own earlier application or select a different port; do not kill unrelated processes |
| HTTP 401 | Authentication failed or was not supplied |
| HTTP 403 | Authorization or CSRF rejected the request; inspect the security configuration |
| HTTP 404 | Wrong application/port/path, or a resource that the application reports as missing |
| HTTP 405 | Path exists but does not support that HTTP method |
| HTTP 415 / 406 | Request content type / acceptable response type does not match converters or mappings |
| SQL initialization failure | H2 version, generated table names, seed SQL and schema initialization order |

For SOAP generation failures, inspect which schema files the JAXB plugin
selected. The resources directory also contains a security policy XML, and
the old plugin writes generated files into tracked source directories.
Review `git diff` after generating classes.

## Useful local observations

Read the first relevant `Caused by` in startup logs. Confirm the server has
started before debugging curl. For Boot condition reporting, use `--debug`
as a run argument; Spring Core also already enables framework debug logging.
Use `curl -i` to inspect the status and headers as well as the response body.

For REST, use `curl -u username ...` so the password is prompted locally.
Do not paste real secrets into documentation, scripts, logs or commits.

Do not blindly expose every Actuator endpoint or disable security to make a
request pass. A diagnostic endpoint and database console may reveal sensitive
data. The existing demo configurations are intentionally not deployment
guidance.

## Modernization is a separate task

A coherent upgrade would align the Boot/Framework baseline, JDK, servlet
container, validation/persistence APIs, security configuration, HATEOAS,
API-documentation libraries and tests. Spring Framework 6 / Boot 3 use
Jakarta EE APIs where these old examples often use `javax.*`.
This is not a blanket rule to rename every Java `javax` package.

Modern Spring Security uses bean-based `SecurityFilterChain` configuration
rather than extending `WebSecurityConfigurerAdapter`. That architectural
difference is worth explaining in interviews, but copying modern snippets
into an old module without aligning its dependencies can break it.

The educational update preserves the original executable behaviour and
documents existing limitations; it does not claim to have modernized these
applications.
