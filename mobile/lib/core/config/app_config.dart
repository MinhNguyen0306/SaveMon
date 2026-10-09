class AppConfig {
  const AppConfig({
    required this.apiBaseUrl,
    this.environment = 'development',
  });

  factory AppConfig.fromEnvironment() {
    return const AppConfig(
      apiBaseUrl: String.fromEnvironment('API_BASE_URL'),
      environment: String.fromEnvironment(
        'APP_ENV',
        defaultValue: 'development',
      ),
    );
  }

  final String apiBaseUrl;
  final String environment;

  Uri get apiBaseUri {
    final parsedUri = Uri.tryParse(apiBaseUrl);
    if (parsedUri == null ||
        !parsedUri.hasAuthority ||
        parsedUri.host.isEmpty ||
        (parsedUri.scheme != 'http' && parsedUri.scheme != 'https') ||
        parsedUri.userInfo.isNotEmpty ||
        parsedUri.hasQuery ||
        parsedUri.hasFragment) {
      throw StateError(
        'API_BASE_URL must be an HTTP or HTTPS URL without credentials, '
        'a query, or a fragment.',
      );
    }

    final path = parsedUri.path.endsWith('/')
        ? parsedUri.path
        : '${parsedUri.path}/';
    return parsedUri.replace(path: path);
  }
}
