package org.sdase.commons.client.jersey;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.util.Duration;

/**
 * A configuration class to be used for API clients that are created as Proxy from annotated
 * interfaces.
 */
public class ApiHttpClientConfiguration extends JerseyClientConfiguration {
  /**
   * The default (read) timeout to wait for data in an established connection. 2 seconds is used as
   * a trade between "fail fast" and "better return late than no result". The timeout may be changed
   * according to the use case considering how long a user is willing to wait and how long backend
   * operations need.
   */
  public static final int DEFAULT_TIMEOUT_MS = 2_000;

  /**
   * The default timeout to wait until a connection is established. 500ms should be suitable for all
   * communication in the platform. Clients that request information from external services may
   * extend this timeout if foreign services are usually slow.
   */
  public static final int DEFAULT_CONNECTION_TIMEOUT_MS = 500;

  private String apiBaseUrl;

  public ApiHttpClientConfiguration() {
    // Chunked encoding is disabled by default, because in combination with the
    // underlying Apache Http Client it breaks support for multipart/form-data
    setChunkedEncodingEnabled(false);

    // Gzip for request is disabled by default, because it breaks with some
    // server implementations in combination with the Content-Encoding: gzip
    // header if supplied accidentally to a GET request. This happen after
    // calling a POST and receiving a SEE_OTHER response that is executed with
    // the same headers as the POST request.
    setGzipEnabledForRequests(false);

    // override the default timeouts
    setTimeout(Duration.milliseconds(DEFAULT_TIMEOUT_MS));
    setConnectionTimeout(Duration.milliseconds(DEFAULT_CONNECTION_TIMEOUT_MS));
  }

  /** @return the base URL of the implemented API, e.g. {@code http://the-service:8080/api} */
  public String getApiBaseUrl() {
    return apiBaseUrl;
  }

  /**
   * @param apiBaseUrl the base URL of the implemented API, e.g. {@code http://the-service:8080/api}
   * @return this instance for fluent configuration
   */
  public ApiHttpClientConfiguration setApiBaseUrl(String apiBaseUrl) {
    this.apiBaseUrl = apiBaseUrl;
    return this;
  }
}
