# Learning path: prepare to explain, not memorise

[Overview](../readme.md) | [Handbook](interview-handbook.md) | [Setup](setup-and-troubleshooting.md)

## How to use this repository

You need basic Java classes/interfaces, collections, exceptions and a rough
understanding of HTTP. If these are unfamiliar, practise them before memorising
Spring annotations. A framework cannot compensate for an unclear Java model.

For each topic, use this loop:

1. Read the module walkthrough and locate the named classes.
2. Predict what happens before you run the example.
3. Trace one success and one failure through the code.
4. Explain the concept aloud without looking at the source.
5. Propose a small change and the test that would prove it works.

Running the application is useful, but explaining its stubs and limitations is
equally important. Do not let an old dependency-resolution problem prevent you
from understanding dependency injection or request routing.

## A suggested seven-session plan

Each session can be split across days. Use the outcome, not the clock, as the
completion criterion.

| Session | Study | Produce before moving on |
| --- | --- | --- |
| 1 | [Spring Core](../2.spring-in-10-steps/readme.md) | Dependency diagram; explain interface injection, scanning and `@Primary` |
| 2 | [Spring MVC](../3.spring-mvc/readme.md) | Browser-to-JSP trace and form success/error flow |
| 3 | [Spring Boot](../4.springboot-in-10-steps/readme.md) | Explain starter versus auto-configuration and trace `/books` to JSON |
| 4 | [REST](../6.restful-web-services/readme.md) | Status/header/body examples for success, validation and missing resources |
| 5 | REST module's JPA code | Explain the two stores, relationship ownership and lazy loading |
| 6 | [SOAP](../5.soap-web-services/readme.md) | Draw XSD/JAXB/WSDL flow and compare SOAP with REST fairly |
| 7 | [Handbook](interview-handbook.md) and [slides](../1.presentation/readme.md) | Complete the mock interview and revisit weak topics |

## Build your own evidence

Keep practice changes on a separate local branch if you want to preserve the
original examples. The following are exercises, **not implemented features**:

| Exercise | Required evidence |
| --- | --- |
| Convert `BinarySearchImpl` to constructor injection | Instantiate with a fake strategy without a Spring context |
| Implement real search | Tests for found/missing targets, empty input, duplicates and index semantics |
| Test `/books` | Assert HTTP status, JSON content type and exact field values |
| Define a stricter user request DTO | Reject required-field omissions and invalid values with 400 |
| Test missing users | Verify 404 for lookup and define consistent deletion semantics |
| Add resource ownership checks to todos | User A cannot update/delete user B's data, even with a guessed ID |
| Test user/post persistence | Save a post, retrieve it for its owner and verify the foreign-key relationship |
| Test SOAP contract edge cases | Missing course, invalid token and empty-course-list behaviour |

Distinguish tests by scope: plain unit tests isolate business logic; MVC slice
tests exercise routing, binding and response conversion; repository tests
exercise persistence; full-context tests check integration. Starting Spring
for every tiny test is slower and can hide which layer actually failed.

## A short mock interview

Give each answer in about 60-90 seconds, then take the follow-up seriously.
Use **definition -> mechanism -> code example -> trade-off**.

| Main question | Follow-up to test understanding |
| --- | --- |
| What are IoC and DI? | How would you test `BinarySearchImpl` without Spring? |
| How does Spring choose an injected bean? | What happens with two candidates and no primary/qualifier? |
| Are singleton beans thread-safe? | What races can occur in `TodoService`? |
| How does Spring MVC handle a request? | Why is `"list-todos"` a view but a REST return value is data? |
| What does Boot add to Spring? | What conditions might cause auto-configuration to back off? |
| How does validation work? | Does `@Size` reject null? What about malformed JSON? |
| What should a create-user response contain? | Does returning `void` automatically mean 204? |
| What are JPA, Hibernate and Spring Data? | Which side owns the user/post relationship? |
| Why can lazy loading become a problem? | How would you avoid N+1 queries without making everything eager? |
| How does SOAP contract-first development work? | What if the server returns an empty list forbidden by its XSD? |
| How would you secure this application? | How do authentication, authorization and CSRF differ? |
| What tests would you add first? | Which existing `contextLoads` checks do not verify business behaviour? |

## Score your answers honestly

Score each response from 0 to 3:

- **0:** Cannot explain the concept yet.
- **1:** Can state a definition, but cannot connect it to code.
- **2:** Can explain the mechanism and point to a real example.
- **3:** Can also discuss a failure mode, trade-off and focused test.

Revisit any answer below 2. For scores of 2, add the missing follow-up rather
than collecting more definitions. A confident incorrect claim should score
lower than a clear, accurate statement of what you have not implemented.

## Talking about this repository in an interview

Say: "I studied and extended learning examples covering DI, MVC, Boot, REST,
JPA and SOAP. I can explain their request flow and the differences between
demo code and production design."

Do not claim this is a deployed production system, a completed binary search
library or a complete automated test suite for every old module.
The new [microservices lab](../7.microservices/readme.md) does implement an
end-to-end auth/gateway/order/event/notification flow with its own tests and
study plan, but is not an enterprise commerce or identity platform.
Credit the original in28minutes material. Describe your actual exercises and
what you learned, especially a bug you understood and how you would test it.

## Final revision checklist

You should be able to draw container startup and web request flow, explain
dependency selection, choose injection and test styles, distinguish HTML from
JSON responses, identify the two user stores, describe JPA ownership and
transactions, compare SOAP and REST, and identify outdated configuration.

The original question catalogue includes additional topics such as JDBC and
AOP. Review the handbook's foundations, but obtain hands-on practice before
claiming implementation experience in areas without dedicated examples here.
