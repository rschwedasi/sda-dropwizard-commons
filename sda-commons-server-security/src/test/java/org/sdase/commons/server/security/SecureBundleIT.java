package org.sdase.commons.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.dropwizard.Configuration;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.ClassRule;
import org.junit.Test;
import org.sdase.commons.server.security.test.SecurityTestApp;

public class SecureBundleIT extends AbstractSecurityTest<Configuration> {

  @ClassRule
  public static final DropwizardAppRule<Configuration> DW =
      new DropwizardAppRule<>(
          SecurityTestApp.class, ResourceHelpers.resourceFilePath("test-config-no-settings.yaml"));

  @Override
  DropwizardAppRule<Configuration> getAppRule() {
    return DW;
  }

  // additional tests we use here to test the bundle

  @Test
  public void useRegularIpWithoutForwardedByHeader() {
    String caller = getAppClient().path("caller").request(MediaType.TEXT_PLAIN).get(String.class);
    assertThat(caller).contains("127.0.0.1");
  }

  @Test
  public void useForwardedForHeader() {
    String caller =
        getAppClient()
            .path("caller")
            .request(MediaType.TEXT_PLAIN)
            .header("X-Forwarded-For", "192.168.123.123")
            .get(String.class);
    assertThat(caller).contains("192.168.123.123");
  }

  @Test
  public void createLinkWithoutForwardedProtoAndHostHeader() {
    String caller = getAppClient().path("link").request(MediaType.TEXT_PLAIN).get(String.class);
    assertThat(caller).contains("http://localhost:" + DW.getLocalPort());
  }

  @Test
  public void useForwardedProtoAndHostHeaderToCreateLink() {
    String caller =
        getAppClient()
            .path("link")
            .request(MediaType.TEXT_PLAIN)
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "from.external.example.com")
            .get(String.class);
    assertThat(caller).contains("https://from.external.example.com");
  }

  @Test
  public void useCustomErrorHandlersForRuntimeException() {
    Response response = getAppClient().path("throw").request().get();
    assertThat(response.getStatus()).isEqualTo(500);
    String content = response.readEntity(String.class);
    assertThat(content).doesNotMatch(".*\"code\"\\s*:\\s*500.*");
  }

  @Test
  public void useCustomErrorPageHandlerForErrorPages() {
    Response response = getAppClient().path("404").request().get();
    assertThat(response.getStatus()).isEqualTo(404);
    String content = response.readEntity(String.class);
    assertThat(content)
        .doesNotMatch(".*\"code\"\\s*:\\s*500.*") // no default exception mapper
        .doesNotContain("<html") // no html page
        .doesNotContain("<h1>") // no html page
        .doesNotContain("<h2>") // no html page
    ;
  }
}
