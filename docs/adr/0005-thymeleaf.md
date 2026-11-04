# 0005 · Web interface with Thymeleaf

- **Status:** accepted · 2026-10-23

## Context

The application needs a simple web interface, with the best possible integration with Spring and Java, that becomes one more input adapter in phase 2.

## Decision

Thymeleaf with `spring-boot-starter-thymeleaf` and Spring MVC: server-rendered templates, forms with Spring validation, no separate frontend project and no Node in the build.

## Alternatives

- SPA (Angular/React): requires maintaining a second project and moves the focus away from the domain.
- JSP: limited support with Spring Boot and an executable JAR.
- Vaadin: well integrated, but it adds its own component model that hides the separation into layers.

## Consequences

- `presentation.web` (phase 1) and `adapter.in.web` (phase 2) are tested with `@WebMvcTest`.
- The same logic is exposed through REST and the web, which in phase 2 shows two input adapters on the same ports.
