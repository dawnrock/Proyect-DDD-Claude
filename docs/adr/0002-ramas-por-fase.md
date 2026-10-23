# 0002 · Una rama por fotografía

- **Estado:** aceptada · 2026-10-23

## Contexto

Se quieren conservar por separado dos versiones completas de la aplicación (monolito en capas y hexagonal) para poder compararlas, además del punto de partida del curso.

## Decisión

| Rama | Contenido | Regla |
| ---- | --------- | ----- |
| `master` | Copia de DDD-Lab-Java (Lab 22) | Congelada: no recibe commits ni PRs |
| `Monolithic` | Arquitectura de IA + fase 1 | Base de las PRs del milestone *Fase 1*. Rama por defecto mientras dura la fase 1 |
| `Hexagonal` | Fase 2 | Se crea desde `Monolithic` al cerrar la fase 1 (etiqueta `v1-monolito`). Pasa a ser la rama por defecto |

Las PRs de cada issue van contra la rama de su fase (las skills la deducen de la etiqueta `fase:*`). Cada fase termina con una etiqueta (`v1-monolito`, `v2-hexagonal`).

## Alternativas

- Dos repositorios: duplica la configuración de IA, las issues y los secretos.
- Un único `main` con etiquetas: las fotografías no quedarían visibles como ramas navegables.

## Consecuencias

- `master` no es la rama por defecto del repositorio (GitHub ejecuta los workflows de issues y comentarios desde la rama por defecto).
- La comparación entre fases es un diff directo: `git diff v1-monolito v2-hexagonal`.
