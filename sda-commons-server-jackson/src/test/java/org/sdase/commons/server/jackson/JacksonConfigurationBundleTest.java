package org.sdase.commons.server.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.sdase.commons.server.jackson.test.ResourceWithLink;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.openapitools.jackson.dataformat.hal.HALLink;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.net.URI;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static com.google.common.truth.Truth.assertThat;


public class JacksonConfigurationBundleTest {

   private ObjectMapper objectMapper;

   private Environment environment;

   private Bootstrap<?> bootstrap;

   @Before
   public void setUp() {
      bootstrap = Mockito.mock(Bootstrap.class);
      objectMapper = Jackson.newObjectMapper();
      environment = Mockito.mock(Environment.class);
      Mockito.when(environment.getObjectMapper()).thenReturn(objectMapper);
      Mockito.when(environment.jersey()).thenReturn(Mockito.mock(JerseyEnvironment.class));
   }

   @Test
   public void shouldAllowEmptyBean() throws Exception {
      init(JacksonConfigurationBundle.builder().build());

      String json = objectMapper.writeValueAsString(new Object());

      assertThat(json).isEqualTo("{}");
   }

   @Test
   public void shouldRenderSelfLink() throws Exception {

      init(JacksonConfigurationBundle.builder().build());
      HALLink link = new HALLink.Builder(URI.create("http://test/1")).build();
      ResourceWithLink resource = new ResourceWithLink().setSelf(link);

      String json = objectMapper.writeValueAsString(resource);

      assertThat(json).isEqualTo("{\"_links\":{\"self\":{\"href\":\"http://test/1\"}}}");

   }

   @Test
   public void shouldDisableHalSupport() throws Exception {

      init(JacksonConfigurationBundle.builder().withoutHalSupport().build());
      HALLink link = new HALLink.Builder(URI.create("http://test/1")).build();
      ResourceWithLink resource = new ResourceWithLink().setSelf(link);

      String json = objectMapper.writeValueAsString(resource);

      assertThat(json).isEqualTo("{\"self\":{\"href\":\"http://test/1\"}}");

   }

   @Test
   public void shouldCustomizeObjectMapper() throws Exception {

      init(JacksonConfigurationBundle.builder().withCustomization(om -> om.enable(INDENT_OUTPUT)).build());
      HALLink link = new HALLink.Builder(URI.create("http://test/1")).build();
      ResourceWithLink resource = new ResourceWithLink().setSelf(link);

      String json = objectMapper.writeValueAsString(resource);

      assertThat(json).isEqualTo("{\n" +
            "  \"_links\" : {\n" +
            "    \"self\" : {\n" +
            "      \"href\" : \"http://test/1\"\n" +
            "    }\n" +
            "  }\n" +
            "}");

   }

   private void init(JacksonConfigurationBundle config) {
      config.initialize(bootstrap);
      config.run(environment);
   }
}