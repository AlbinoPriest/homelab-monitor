# API

The API is rooted at `/api/v1`. OpenAPI JSON is available only with the `dev` Spring profile at `/v3/api-docs`.

| Method | Path | Behavior |
| --- | --- | --- |
| `GET` | `/api/v1/csrf` | Obtain the session-bound CSRF header name/token and initialize the session |
| `GET` | `/api/v1/auth/status` | Report whether first-owner setup is required and whether the current session is authenticated |
| `POST` | `/api/v1/auth/setup` | Create and sign in the only owner; available only before an owner exists |
| `POST` | `/api/v1/auth/login` | Authenticate the owner and rotate the session identifier |
| `POST` | `/api/v1/auth/logout` | Invalidate the authenticated session |
| `GET` | `/api/v1/monitors` | List monitors ordered by name |
| `POST` | `/api/v1/monitors` | Create a monitor |
| `GET` | `/api/v1/monitors/{id}` | Read one monitor |
| `PUT` | `/api/v1/monitors/{id}` | Replace monitor configuration |
| `DELETE` | `/api/v1/monitors/{id}` | Delete a monitor and its history |
| `POST` | `/api/v1/monitors/{id}/checks` | Run one manual check; returns `409` if one is already in flight |
| `GET` | `/api/v1/monitors/{id}/checks?page=0&size=50` | Read newest checks; size is bounded to 100 |
| `GET` | `/api/v1/monitors/{id}/history?page=0&size=50` | Read newest state transitions; size is bounded to 100 |

Mutating endpoints require the CSRF token from `/api/v1/csrf` in the returned header name. Monitor APIs and logout require an authenticated owner session. Auth status, the CSRF bootstrap, setup, and login are public; setup returns `409` after the singleton owner has been created. Authentication failures deliberately use a generic message.
Login attempts are bounded over an expiring window; excess attempts return generic `429` responses with a `Retry-After` header.

HTTP monitors put any explicit port in `target`, such as `https://server.example:8443/health`, and default `expectedHttpStatus` to `200`. TCP monitors use a hostname/IP `target` plus a required `port`. Intervals range from 5 seconds to 24 hours and timeouts from 100 milliseconds to 30 seconds.

Checks return one of `SUCCESS`, `TIMEOUT`, `DNS_FAILURE`, `CONNECTION_REFUSED`, `TLS_ERROR`, `UNEXPECTED_STATUS`, `INVALID_TARGET`, or `UNKNOWN_FAILURE`. Error messages are safe summaries rather than raw exception text.
