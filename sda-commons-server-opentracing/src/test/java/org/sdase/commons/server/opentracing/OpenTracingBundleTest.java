package org.sdase.commons.server.opentracing;

import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static io.opentracing.tag.Tags.COMPONENT;
import static io.opentracing.tag.Tags.HTTP_URL;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;
import static org.sdase.commons.server.opentracing.filter.TagUtils.HTTP_REQUEST_HEADERS;
import static org.sdase.commons.server.opentracing.filter.TagUtils.HTTP_RESPONSE_HEADERS;

import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockSpan.LogEntry;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sdase.commons.server.opentracing.test.TraceTestApp;

public class OpenTracingBundleTest {

  @Rule
  public final DropwizardAppRule<Configuration> dw =
      new DropwizardAppRule<>(TraceTestApp.class, resourceFilePath("test-config.yaml"));

  private MockTracer tracer;

  @Before
  public void setUp() {
    TraceTestApp app = dw.getApplication();
    tracer = app.getTracer();
  }

  @Test
  public void shouldExtractTraceAndSpanIdFromRequestHeaders() {
    Response r =
        createClient()
            .path("respond/mine")
            .request()
            .header("traceid", "1337")
            .header("spanid", "1337")
            .get();

    // Make sure to wait till the request is completed:
    r.readEntity(String.class);

    assertThat(r.getStatus()).isEqualTo(SC_OK);

    await()
        .untilAsserted(
            () -> {
              assertThat(tracer.finishedSpans()).flatExtracting(MockSpan::parentId).contains(1337L);
            });
  }

  @Test
  public void shouldInstrumentJaxRs() {
    Response r = createClient().path("respond/test").request().get();

    // Make sure to wait till the request is completed:
    r.readEntity(String.class);

    assertThat(r.getStatus()).isEqualTo(SC_OK);

    await()
        .untilAsserted(
            () -> {
              assertThat(tracer.finishedSpans())
                  .flatExtracting(MockSpan::tags)
                  .extracting(COMPONENT.getKey())
                  .contains("jaxrs");
            });
  }

  @Test
  public void shouldTraceAndLogExceptions() {
    Response r = createClient().path("error").request().get();

    // Make sure to wait till the request is completed:
    r.readEntity(String.class);

    assertThat(r.getStatus()).isEqualTo(SC_INTERNAL_SERVER_ERROR);

    await()
        .untilAsserted(
            () -> {
              MockSpan span =
                  tracer.finishedSpans().stream()
                      .filter(s -> s.operationName().startsWith("GET:"))
                      .findAny()
                      .orElseThrow(IllegalStateException::new);

              assertThat(span.tags()).contains(entry(Tags.ERROR.getKey(), true));
              assertThat(span.logEntries()).flatExtracting(LogEntry::fields).isNotEmpty();
            });
  }

  @Test
  public void shouldDecorateSpanWithHeaders() {
    Response r = createClient().path("respond/test").request().get();

    // Make sure to wait till the request is completed:
    r.readEntity(String.class);

    assertThat(r.getStatus()).isEqualTo(SC_OK);

    await()
        .untilAsserted(
            () -> {
              MockSpan span =
                  tracer.finishedSpans().stream()
                      .filter(s -> s.operationName().startsWith("GET:"))
                      .findAny()
                      .orElseThrow(IllegalStateException::new);

              assertThat(span.tags())
                  .contains(
                      entry(COMPONENT.getKey(), "jaxrs"),
                      entry(
                          HTTP_URL.getKey(),
                          "http://localhost:" + dw.getLocalPort() + "/respond/test"),
                      entry(HTTP_RESPONSE_HEADERS.getKey(), "[Content-Type = 'text/html']"));
              assertThat(span.tags().containsKey(HTTP_REQUEST_HEADERS.getKey())).isTrue();
            });
  }

  @Test
  public void shouldCollectLogStatementsInTrace() {
    Response r = createClient().path("log").request().get();

    // Make sure to wait till the request is completed:
    r.readEntity(String.class);

    assertThat(r.getStatus()).isEqualTo(SC_NO_CONTENT);

    await()
        .untilAsserted(
            () -> {
              MockSpan span =
                  tracer.finishedSpans().stream()
                      .filter(s -> s.operationName().startsWith("GET:"))
                      .findAny()
                      .orElseThrow(IllegalStateException::new);

              assertThat(span.logEntries().get(0).fields().get("message")).isEqualTo("Hello World");
            });
  }

  private WebTarget createClient() {
    return dw.client().target("http://localhost:" + dw.getLocalPort());
  }
}
