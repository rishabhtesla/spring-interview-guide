# Spring Core: understand dependency injection

[Back to overview](../readme.md) | [Interview handbook](../docs/interview-handbook.md)

## What you are learning

This is a small console application demonstrating how Spring creates objects
and chooses their dependencies. It uses Boot to start the container, but the
main lesson is **Spring Core**, not HTTP or databases.

A dependency is an object another object needs to do its job. Here the search
class needs a sorting strategy. Depending on an interface lets us change the
strategy without rewriting the search class.

## Read the code in this order

All classes below are under
`src/main/java/com/in28minutes/spring/basics/springin5steps/`.

| File | What to notice |
| --- | --- |
| [SortAlgorithm.java](src/main/java/com/in28minutes/spring/basics/springin5steps/SortAlgorithm.java) | The interface is the contract both sorting strategies implement |
| [BubbleSortAlgorithm.java](src/main/java/com/in28minutes/spring/basics/springin5steps/BubbleSortAlgorithm.java) | `@Component` registers a candidate; `@Primary` makes it the default |
| [QuickSortAlgorithm.java](src/main/java/com/in28minutes/spring/basics/springin5steps/QuickSortAlgorithm.java) | A second bean implements the same interface |
| [BinarySearchImpl.java](src/main/java/com/in28minutes/spring/basics/springin5steps/BinarySearchImpl.java) | `@Autowired` requests a `SortAlgorithm`, not a concrete class |
| [SpringIn5StepsApplication.java](src/main/java/com/in28minutes/spring/basics/springin5steps/SpringIn5StepsApplication.java) | Starts Spring and retrieves the managed search bean |

## How the wiring works

```text
main()
  -> SpringApplication.run(...)
  -> component scan of the application package and its descendants
  -> registers BinarySearchImpl, BubbleSortAlgorithm, QuickSortAlgorithm
  -> BinarySearchImpl needs a SortAlgorithm
  -> both sorting beans match the required type
  -> @Primary selects BubbleSortAlgorithm
  -> getBean(BinarySearchImpl.class) returns the wired object
  -> binarySearch(...) calls its injected sorting strategy
```

**IoC (Inversion of Control)** means the container controls object creation and
assembly. **DI (Dependency Injection)** is how it supplies collaborators.
`@Component` makes an object eligible to be managed; `@Autowired` marks an
injection point. Neither annotation implements the sorting algorithm.

## Important: these are teaching stubs

Both `sort` methods return the input array unchanged. `binarySearch` calls
the sorting dependency, prints it, and always returns `3`. It does not search
the array, and `sortedNumbers` is not used.

For `{12, 4, 6}` and a target of `3`, the printed result is therefore `3`,
**not proof that the target exists or that an index was found**. In an
interview, call this a dependency-injection demo, not a completed search
implementation.

## Running locally

Use JDK 17; the POM uses Boot `3.0.0-M4`, an old milestone rather than a stable
release. Follow the [setup guide](../docs/setup-and-troubleshooting.md) if
dependency resolution fails.

From the repository root:

```bash
mvn -f 2.spring-in-10-steps/pom.xml spring-boot:run
```

Or import this module's `pom.xml` into your IDE and run
`SpringIn5StepsApplication.main`. Expect Spring debug logging, the selected
sorting object's class name, and `3`. There is no `/books` endpoint or embedded
web application in this module.

```bash
mvn -f 2.spring-in-10-steps/pom.xml test
```

The existing `contextLoads` test checks startup, not algorithm correctness.

## Interview questions with worked answers

**Why depend on `SortAlgorithm` instead of `BubbleSortAlgorithm`?**
The interface describes the capability needed. The caller does not need to
know how sorting is implemented. That is loose coupling and the Strategy
pattern. DI supplies a chosen strategy; it does not remove the dependency.

**What happens if you remove `@Primary`?**
With these two candidates and no explicit qualifier, Spring cannot select a
unique `SortAlgorithm` for this field. Startup fails with a bean resolution
error whose cause is typically `NoUniqueBeanDefinitionException`.

**What is the difference between `@Primary` and `@Qualifier`?**
`@Primary` declares a preferred candidate when several beans match.
`@Qualifier` narrows the candidates at an injection point, such as explicitly
requesting the quick-sort bean. A matching qualifier is more explicit than
relying on a default.

**Why would you prefer constructor injection?**
Required dependencies become visible at object creation. They can be final,
and a test can instantiate the class with a fake strategy without starting
Spring. This example uses field injection to illustrate autowiring; that
style should not be mistaken for the preferred design for required dependencies.

## Practice tasks (not already implemented)

1. Move `@Primary` to the quick-sort bean. Predict the printed class before
   running. The result still stays `3` because search is a stub.
2. Refactor to constructor injection and write a test with a fake
   `SortAlgorithm` that records whether it was called.
3. Implement real sorting and searching with an explicit contract: return the
   found index or `-1`. Decide whether sorting mutates the caller's array and
   whether the index refers to the sorted array or the original input.
4. Cover empty input, missing target, duplicates, already sorted input, and
   the agreed null-input behaviour. Explain binary search's `O(log n)` search
   cost separately from the cost of sorting.

**Ready to move on:** explain the complete wiring flow and why singleton scope
does not automatically make a bean thread-safe.
