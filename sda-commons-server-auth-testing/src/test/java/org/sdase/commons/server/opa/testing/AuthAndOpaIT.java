package org.sdase.commons.server.opa.testing;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.HttpHeaders.WWW_AUTHENTICATE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.util.Lists.newArrayList;
import static org.sdase.commons.server.opa.testing.OpaRule.onAnyRequest;
import static org.sdase.commons.server.opa.testing.OpaRule.onRequest;

import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.sdase.commons.server.auth.testing.AuthRule;
import org.sdase.commons.server.opa.testing.test.AuthAndOpaBundeTestAppConfiguration;
import org.sdase.commons.server.opa.testing.test.AuthAndOpaBundleTestApp;
import org.sdase.commons.server.opa.testing.test.ConstraintModel;
import org.sdase.commons.server.opa.testing.test.PrincipalInfo;
import org.sdase.commons.server.testing.Retry;
import org.sdase.commons.server.testing.RetryRule;

public class AuthAndOpaIT {

  private static final AuthRule AUTH = AuthRule.builder().build();

  private static final OpaRule OPA_RULE = new OpaRule();

  private static final DropwizardAppRule<AuthAndOpaBundeTestAppConfiguration> DW =
      new DropwizardAppRule<>(
          AuthAndOpaBundleTestApp.class,
          resourceFilePath("test-config.yaml"),
          config("opa.baseUrl", OPA_RULE::getUrl));

  @ClassRule
  public static final RuleChain chain = RuleChain.outerRule(AUTH).around(OPA_RULE).around(DW);

  @Rule public final RetryRule retryRule = new RetryRule();

  private static String path = "resources";
  private static String method = "GET";
  private String jwt;

  @Before
  public void before() {
    jwt = AUTH.auth().buildToken();
    OPA_RULE.reset();
  }

  @Test
  @Retry(5)
  public void shouldNotAccessSimpleWithInvalidToken() {
    Response response =
        getResources(
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    assertThat(response.getHeaderString(WWW_AUTHENTICATE)).contains("Bearer");
    assertThat(response.getHeaderString(CONTENT_TYPE)).isEqualTo(APPLICATION_JSON);
    assertThat(response.readEntity(new GenericType<Map<String, Object>>() {}))
        .containsKeys("title", "invalidParams");
    OPA_RULE.verify(0, onAnyRequest());
  }

  @Test
  @Retry(5)
  public void shouldAllowAccessSimple() {
    OPA_RULE.mock(onRequest().withHttpMethod(method).withPath(path).withJwt(jwt).allow());

    Response response = getResources(true);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
    PrincipalInfo principalInfo = response.readEntity(PrincipalInfo.class);
    assertThat(principalInfo.getJwt()).isEqualTo(jwt);
    assertThat(principalInfo.getConstraints().getConstraint()).isNull();
    assertThat(principalInfo.getConstraints().isFullAccess()).isFalse();
  }

  @Test
  @Retry(5)
  public void shouldAllowAccessConstraints() {
    OPA_RULE.mock(
        onRequest()
            .withHttpMethod(method)
            .withPath(path)
            .withJwt(jwt)
            .allow()
            .withConstraint(
                new ConstraintModel().setFullAccess(true).addConstraint("customer_ids", "1")));

    Response response = getResources(true);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
    PrincipalInfo principalInfo = response.readEntity(PrincipalInfo.class);
    assertThat(principalInfo.getName()).isEqualTo("OpaJwtPrincipal");
    assertThat(principalInfo.getJwt()).isNotNull();
    assertThat(principalInfo.getConstraints().isFullAccess()).isTrue();
    assertThat(principalInfo.getConstraints().getConstraint())
        .contains(entry("customer_ids", newArrayList("1")));
    assertThat(principalInfo.getConstraintsJson()).contains("\"customer_ids\":").contains("\"1\"");
    assertThat(principalInfo.getSub()).isEqualTo("test");
  }

  @Test
  @Retry(5)
  public void shouldAllowAccessWithoutTokenSimple() {
    OPA_RULE.mock(onRequest().withHttpMethod(method).withPath(path).withJwt(null).allow());

    Response response = getResources(false);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
    PrincipalInfo principalInfo = response.readEntity(PrincipalInfo.class);
    assertThat(principalInfo.getJwt()).isNull();
    assertThat(principalInfo.getConstraints().getConstraint()).isNull();
    assertThat(principalInfo.getConstraints().isFullAccess()).isFalse();
  }

  @Test
  @Retry(5)
  public void shouldAllowAccessWithoutTokenConstraints() {
    OPA_RULE.mock(
        onRequest()
            .withHttpMethod(method)
            .withPath(path)
            .withJwt(null)
            .allow()
            .withConstraint(
                new ConstraintModel().setFullAccess(true).addConstraint("customer_ids", "1")));

    Response response = getResources(false);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
    PrincipalInfo principalInfo = response.readEntity(PrincipalInfo.class);
    assertThat(principalInfo.getJwt()).isNull();
    assertThat(principalInfo.getConstraints().isFullAccess()).isTrue();
    assertThat(principalInfo.getConstraints().getConstraint())
        .contains(entry("customer_ids", newArrayList("1")));
  }

  private Response getResources(boolean withJwt) {
    return getResources(withJwt ? jwt : null);
  }

  private Response getResources(String jwt) {
    MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
    if (jwt != null) {
      headers.put(HttpHeaders.AUTHORIZATION, newArrayList("Bearer " + jwt));
    }

    return DW.client()
        .target("http://localhost:" + DW.getLocalPort()) // NOSONAR
        .path(path)
        .request()
        .headers(headers)
        .get();
  }
}
