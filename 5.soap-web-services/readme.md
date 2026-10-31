# SOAP: understand contract-first web services

[Back to overview](../readme.md) | [Interview handbook](../docs/interview-handbook.md)

## What this application does

Clients can retrieve one course, list courses, or delete a course using XML
messages inside SOAP envelopes. Spring Web Services selects a Java endpoint
method based on the **namespace and local name of the request payload**.
Unlike the REST module, operations are not selected using `/users/{id}` paths.

**Contract-first** means defining the XML contract before the implementation.
Here the XSD defines message shapes, JAXB classes represent those shapes in
Java, and Spring exposes a WSDL describing how clients call the service.

## Code map and reading order

| File | Purpose |
| --- | --- |
| [course-details.xsd](src/main/resources/course-details.xsd) | Defines request/response elements, course fields and status values |
| [pom.xml](pom.xml) | Includes the JAXB generation plugin, Spring WS and legacy SOAP security libraries |
| [Generated classes](src/main/java/com/in28minutes/courses/) | JAXB request/response models; these are not the business service |
| [WebServiceConfig.java](src/main/java/com/in28minutes/soap/webservices/soapcoursemanagement/soap/WebServiceConfig.java) | Registers `/ws/*`, publishes the `courses` WSDL, and installs security interception |
| [CourseDetailsEndpoint.java](src/main/java/com/in28minutes/soap/webservices/soapcoursemanagement/soap/CourseDetailsEndpoint.java) | Routes payloads, calls the service and maps domain objects to contract objects |
| [CourseDetailsService.java](src/main/java/com/in28minutes/soap/webservices/soapcoursemanagement/soap/service/CourseDetailsService.java) | In-memory course lookup and deletion |
| [securityPolicy.xml](src/main/resources/securityPolicy.xml) | Requires a SOAP UsernameToken under the configured legacy security interceptor |

## Trace a single-course lookup

```text
POST /ws containing a SOAP envelope
  -> MessageDispatcherServlet
  -> configured security interceptor validates the UsernameToken
  -> payload QName:
     {http://in28minutes.com/courses}GetCourseDetailsRequest
  -> JAXB unmarshalling into GetCourseDetailsRequest
  -> CourseDetailsEndpoint.processCourseDetailsRequest(...)
  -> CourseDetailsService.findById(...)
  -> domain Course mapped to JAXB CourseDetails
  -> response marshalled to XML in a SOAP envelope
```

`@Endpoint` registers a SOAP endpoint.
`@PayloadRoot` selects a method by XML qualified name.
`@RequestPayload` receives the unmarshalled body.
`@ResponsePayload` sends the return value through XML marshalling.
The namespace URI is an identifier; it is not a URL that the client needs to
download to invoke the operation.

## Operations and their actual behaviour

| Request element | Behaviour |
| --- | --- |
| `GetCourseDetailsRequest` | Looks up the supplied ID; throws `CourseNotFoundException` if absent |
| `GetAllCourseDetailsRequest` | Maps every course in the list to the response |
| `DeleteCourseDetailsRequest` | Removes a matching course and returns `SUCCESS` or `FAILURE` |

Four demo courses are initially loaded. Storage is a static in-memory list,
not JPA persistence, even though the POM includes a JPA starter.
There is no create/update endpoint implemented. Missing-course exceptions are
SOAP-side failures, not the REST module's JSON 404 response mechanism.

## Running and inspecting the contract

Use a JDK 8 environment for this Boot `2.0.0.RELEASE` example. From the root:

```bash
mvn -f 5.soap-web-services/pom.xml spring-boot:run
```

After successful startup, inspect:

```text
http://localhost:8080/ws/courses.wsdl
```

The bean name `courses` determines the WSDL filename. `setLocationUri("/ws")`
defines the service location, and transformed WSDL locations help reflect the
incoming host. Import the WSDL into a SOAP client such as SoapUI to inspect
operations and generate an envelope.

This is the body shape for course 1, **not a complete authenticated request**:

```xml
<course:GetCourseDetailsRequest xmlns:course="http://in28minutes.com/courses">
  <course:id>1</course:id>
</course:GetCourseDetailsRequest>
```

A full request also needs the SOAP envelope/body and the UsernameToken header
required by the policy. HTTP Basic authentication and a WS-Security token are
different mechanisms. The existing callback handler contains local demo
credentials; do not replace them with real credentials in source control.
The policy does not require a password digest or nonce and is not a
production security recommendation.

## Generated code and compatibility cautions

The `jaxb2-maven-plugin` writes generated classes into `src/main/java`.
Changing the XSD and building may modify tracked generated files.
Edit the contract and regenerate intentionally; do not teach a contract change
by manually modifying one generated response class.

The plugin scans the resources schema directory, which also contains the
non-XSD security policy XML. If generation fails, check exactly which files
the old plugin is treating as schema input. Java 11+ no longer bundles JAXB;
using a newer JDK without adapting this legacy stack can cause additional
generation/runtime errors.

The XSD's course list has `maxOccurs="unbounded"` and the default
`minOccurs="1"`. After deleting every course, the implementation produces an
empty list that does not satisfy that contract. Recognising this mismatch is
an excellent contract-design exercise; runtime schema validation is not
explicitly enabled by this configuration.

## Interview questions with worked answers

**How do XSD and WSDL differ?**
XSD defines XML structure and data types. WSDL describes service operations,
messages, bindings and locations and can include/reference schemas. JAXB
maps schema-defined XML to Java; it does not replace WSDL.

**Why have both `Course` and generated `CourseDetails`?**
The domain model and external contract serve different purposes. Mapping
between them isolates internal changes from the public message format.
Changing a public schema can break clients even when Java code compiles.

**Is SOAP always HTTP and REST always JSON?**
No. SOAP is an XML messaging protocol and can use different transports.
REST is an architectural style and can use multiple representations.
Compare contract needs, interoperability and operational constraints instead
of reducing the choice to XML versus JSON.

## Practice tasks

1. Identify the namespace and local name for each operation without looking
   at the endpoint class, then verify the mappings.
2. Test a valid lookup, a missing course, missing/invalid authentication,
   deletion twice, and listing after all courses are deleted.
3. Decide whether an empty list should be legal in the schema. Explain how
   to update, regenerate and test the contract consistently.
4. Design a create-course operation in XSD before writing Java code.

**Ready to move on:** explain the complete XSD -> JAXB -> endpoint -> WSDL
relationship and distinguish message security from transport security.
