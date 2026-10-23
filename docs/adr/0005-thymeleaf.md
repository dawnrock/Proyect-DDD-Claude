# 0005 · Interfaz web con Thymeleaf

- **Estado:** aceptada · 2026-10-23

## Contexto

La aplicación necesita una interfaz web sencilla, con la mejor integración posible con Spring y Java, y que en la fase 2 se convierta en un adaptador de entrada más.

## Decisión

Thymeleaf con `spring-boot-starter-thymeleaf` y Spring MVC: plantillas renderizadas en el servidor, formularios con validación de Spring, sin proyecto de frontend aparte ni Node en el build.

## Alternativas

- SPA (Angular/React): obliga a mantener un segundo proyecto y desvía el foco del dominio.
- JSP: soporte limitado con Spring Boot y JAR ejecutable.
- Vaadin: muy integrado, pero añade un modelo de componentes propio que oculta la separación en capas.

## Consecuencias

- `presentation.web` (fase 1) y `adapter.in.web` (fase 2) se prueban con `@WebMvcTest`.
- La misma lógica se expone por REST y por web, lo que en la fase 2 demuestra dos adaptadores de entrada sobre los mismos puertos.
