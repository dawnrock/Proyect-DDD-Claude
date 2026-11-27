// Arranque de Swagger UI (#111). Es un fichero y no un script en línea por la Content-Security-Policy (#206).
// El contrato escrito a mano, el mismo fichero que docs/api/openapi.yaml (lo copia el build).
// «Authorize» con Auth0 (#197, ADR 0024): Swagger UI es un cliente público (SPA) que pide el token con PKCE para la
// audiencia de la API. Su configuración viene del servidor; sin Auth0, enabled es false y la página es la de siempre.
// La vuelta de Auth0 llega a oauth2-redirect.html, copiada sin cambios de swagger-ui-dist@5.34.1 (con su .js).
fetch('/api-docs/oauth-config')
  .then(response => response.ok ? response.json() : {enabled: false})
  .catch(() => ({enabled: false}))
  .then(config => {
    window.ui = SwaggerUIBundle({
      url: '/openapi.yaml',
      dom_id: '#swagger-ui',
      deepLinking: true,
      persistAuthorization: false,
      oauth2RedirectUrl: window.location.origin + '/api-docs/oauth2-redirect.html'
    });
    if (config.enabled) {
      window.ui.initOAuth({
        clientId: config.clientId,
        scopes: 'openid',
        usePkceWithAuthorizationCodeGrant: true,
        additionalQueryStringParams: {audience: config.audience}
      });
    }
  });
