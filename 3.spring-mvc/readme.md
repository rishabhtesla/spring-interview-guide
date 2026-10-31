# Spring MVC: follow a todo request from browser to view

[Back to overview](../readme.md) | [Setup guide](../docs/setup-and-troubleshooting.md)

## Purpose and architecture

This is a traditional servlet-based WAR application, not a Spring Boot
application. It demonstrates HTML pages rendered with JSP, form binding,
validation, session attributes, login, and a small JSON controller.

```text
Browser request
  -> security filter chain
  -> DispatcherServlet
  -> TodoController
  -> TodoService (in-memory list)
  -> model attributes + logical view name
  -> InternalResourceViewResolver
  -> JSP renders HTML
```

MVC means **Model, View, Controller**. The model carries data for rendering,
the view presents it, and the controller translates a request into an
application action. The service owns the todo operations; it is not a database.

## Code map and reading order

| File | What to learn |
| --- | --- |
| [web.xml](src/main/webapp/WEB-INF/web.xml) | Registers `DispatcherServlet` at `/` and `DelegatingFilterProxy` for security |
| [todo-servlet.xml](src/main/webapp/WEB-INF/todo-servlet.xml) | Component scanning, MVC support, view resolution, messages and locale handling |
| [TodoController.java](src/main/java/com/in28minutes/todo/TodoController.java) | Request mappings, model attributes, validation, redirects and local error handling |
| [Todo.java](src/main/java/com/in28minutes/todo/Todo.java) | Form data, description constraint, `LocalDate`, equality by ID |
| [TodoService.java](src/main/java/com/in28minutes/todo/TodoService.java) | Add, retrieve, update and delete operations on a shared list |
| [todo.jsp](src/main/webapp/WEB-INF/views/todo.jsp) | Bound form fields and field-error display |
| [list-todos.jsp](src/main/webapp/WEB-INF/views/list-todos.jsp) | Rendering the model's todo list |
| [SecurityConfiguration.java](src/main/java/com/in28minutes/security/SecurityConfiguration.java) | Legacy form-login and in-memory authentication configuration |
| [TodoRestController.java](src/main/java/com/in28minutes/todo/TodoRestController.java) | Returns objects as JSON rather than selecting a JSP |

## Walk through two requests

**Listing:** `GET /list-todos` calls `listTodos`. The controller gets the
authenticated username, asks the service for that user's todos, and adds them
under the model key `todos`. Returning `"list-todos"` selects
`/WEB-INF/views/list-todos.jsp`, using the resolver's prefix and suffix.
It does not return the literal text `list-todos` to the browser.

**Submitting:** `GET /add-todo` supplies a form-backing `Todo` under the key
`todo`. On `POST /add-todo`, Spring binds submitted fields to that object.
`@Valid` requests validation; the adjacent `BindingResult` contains binding
and validation errors. When there are errors, returning `"todo"` redisplays
the form. Otherwise the service saves a todo and the controller redirects to
the list. This is the Post/Redirect/Get pattern: refreshing the resulting
page normally repeats the GET, not the form submission.

The add handler currently supplies `LocalDate.now()` to the service rather
than using the submitted target date. Read the implementation, not just the
form labels, when describing behaviour.

## Endpoint map

| Request | Intended role |
| --- | --- |
| `GET /` | Welcome page |
| `GET /list-todos` | List the logged-in user's todos |
| `GET /add-todo`, `POST /add-todo` | Display and submit the add form |
| `GET /update-todo?id=1`, `POST /update-todo` | Display and submit the update form |
| `GET /delete-todo?id=1` | Demo deletion link; not a safe production method choice |
| `GET /todos`, `GET /todos/1` | JSON examples; list uses a fixed demo username |

## Run instructions and limitations

The original launch command, from the repository root, is:

```bash
mvn -f 3.spring-mvc/pom.xml tomcat7:run
```

Then visit `http://localhost:8080/`. The original case-sensitive local demo
login is documented in the existing security configuration. It is not a real
account and must not be reused for deployment.

**This is a legacy launch path, not a guaranteed working modern setup.**
The POM targets Java 17 and Spring MVC 5.3.20, but uses the old Tomcat 7 plugin.
It also declares Hibernate Validator 7 (Jakarta Validation), while application
constraints use `javax.validation`. Read the
[setup guide](../docs/setup-and-troubleshooting.md) before diagnosing failures.

Other teaching limitations to recognise:

- The custom binder registers a `java.util.Date` editor, but `Todo.targetDate`
  is `LocalDate`; it does not define conversion for that field.
- `todo.jsp` uses the legacy `commandName` form attribute. View compatibility
  needs checking against the selected Spring tag library.
- The service list and ID counter are shared mutable state, not thread-safe
  persistent storage. Restarting discards changes.
- Looking up by ID is not the same as checking ownership. User-specific list
  filtering alone does not enforce access control for update and delete.
- `web.xml` points its error page at `/WEB-INF/views/jsp/error.jsp`, whereas
  the tracked file is `/WEB-INF/views/error.jsp`.

These notes describe existing issues; the learning update does not change
application behaviour or claim to repair the legacy runtime.

## Interview answers to practise

**What is the DispatcherServlet?**
Spring MVC's front controller. It delegates request matching, argument
resolution, controller execution, and response rendering to framework
components. It does not contain the business logic itself.

**What is the difference between `@Controller` and `@RestController`?**
A regular controller can return a logical view name for HTML rendering.
`@RestController` includes response-body semantics, so return values are
written through HTTP message converters. A regular controller can also use
`@ResponseBody` on selected methods.

**Why must `BindingResult` follow the validated argument?**
Its position associates the errors with that bound argument. Binding errors
include conversion failures; validation errors include violated constraints.
Client-side `required` fields do not replace server-side validation.

**What does `@SessionAttributes("name")` mean?**
Selected model attributes are stored across requests in the HTTP session.
It does not turn all model data into session data and is not an authentication
mechanism. Authentication is managed by the security context.

## Practice exercises

1. Draw the exact route from `/list-todos` to its JSP and model key.
2. Plan tests for a valid description, a short description, a malformed date,
   an unknown todo ID, and one user attempting to modify another user's todo.
3. Explain why deletion should not use GET: GET is meant to be safe and may
   be prefetched, crawled or cached. Design a protected state-changing request.
4. Compare a missing todo in HTML and JSON: choose an explicit user-facing
   error/status instead of treating `null` as successful data.

**Ready to move on:** explain view resolution, binding versus validation,
redirects, and authentication versus resource ownership.
