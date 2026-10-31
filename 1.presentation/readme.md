# Presentation and reference material

[Back to project overview](../readme.md)

This directory contains the original interview slides and bundled reveal.js
presentation framework. It is not a Spring application and has no Java build.

## What to open

| File or directory | Purpose |
| --- | --- |
| `spring-interview-questions.html` | The actual interview presentation |
| `index.html` | A two-slide reveal.js placeholder, not the course |
| `images/` | Request-flow diagrams, course overviews, and illustrations |
| `css/`, `js/`, `lib/`, `plugin/` | The bundled presentation renderer |
| `test/` | reveal.js tests, not Spring application tests |
| `package.json`, `Gruntfile.js` | Original presentation development tooling |
| `../spring-interview-questions.pdf` | Offline revision PDF |
| `../1.presentation.zip` | Archived presentation; it is not automatically updated |
| `../notes.md` | Original video timestamps and topic index |

## Viewing the slides locally

Open `spring-interview-questions.html` in your browser. If browser file-access
rules prevent a feature from working, serve the repository locally. If Python 3
is installed, run this from the repository root:

```bash
python3 -m http.server 8000 --bind 127.0.0.1
```

Then visit
`http://localhost:8000/1.presentation/spring-interview-questions.html`.
Use the arrow keys to navigate and Escape for the slide overview. Stop the
server with Ctrl+C. This server is only for static slides; it cannot run Java
controllers.

You do not need to install the old Node/Grunt dependencies just to view the
bundled slides. They include old native build tooling that may not work with
modern Node versions.

## Turning slides into interview practice

Read one topic, close the slide, and explain it in your own words. Find the
matching class using the [module map](../readme.md#what-is-in-the-project).
Draw its request or dependency flow, then answer one follow-up:
"What could go wrong, and how would I test it?"

Treat version-specific slides as historical material. For example, old
security configuration and HATEOAS APIs should not be quoted as current
configuration advice. Use the
[interview handbook](../docs/interview-handbook.md) for the distinction.
