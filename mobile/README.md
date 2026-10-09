# SaveMon Mobile

The Flutter foundation is organized by application concerns and features:

```text
lib/
  app/          app shell, routing, and theme
  core/         configuration, dependency providers, API/session abstractions, widgets
  features/     feature-oriented screens and future feature modules
test/           unit and widget tests
```

## Run

From this directory:

```sh
flutter pub get
flutter analyze
flutter test
```

Configure the API root (including the documented `/api/v1` prefix) at build or
run time with `--dart-define=API_BASE_URL=https://your-api-host/api/v1`.
`APP_ENV` is optional and defaults to `development`.

The session repository stores access tokens with platform secure storage.
`authStateProvider` exposes only authenticated or unauthenticated status.
Registration and login persist the documented access token through the secure
session repository. Refresh and logout are not implemented. Authenticated requests use
the stored token as a Bearer token. An authenticated `401` clears the session
and updates the auth state without retrying the request.
