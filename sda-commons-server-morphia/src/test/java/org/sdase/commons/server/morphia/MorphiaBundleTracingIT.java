package org.sdase.commons.server.morphia;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.assertj.core.api.Assertions.assertThat;

import dev.morphia.Datastore;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.sdase.commons.server.mongo.testing.MongoDbRule;
import org.sdase.commons.server.morphia.test.Config;
import org.sdase.commons.server.morphia.test.model.Person;

/** Tests if entities can be added by exact definition. */
public class MorphiaBundleTracingIT {

  private static final MongoDbRule MONGODB = MongoDbRule.builder().build();

  private static final DropwizardAppRule<Config> DW =
      new DropwizardAppRule<>(
          MorphiaTestApp.class,
          resourceFilePath("test-config.yaml"),
          config("mongo.hosts", MONGODB::getHost),
          config("mongo.database", MONGODB::getDatabase));

  @ClassRule public static final RuleChain CHAIN = RuleChain.outerRule(MONGODB).around(DW);

  @Test
  public void shouldHaveInstrumentation() {
    Datastore datastore = getDatastore();
    Person person = new Person().setAge(18).setName("Max");
    datastore.save(person);

    MockTracer tracer = getMockTracer();
    assertThat(tracer.finishedSpans())
        .extracting(MockSpan::operationName)
        .contains("createIndexes", "insert");
    assertThat(
            tracer.finishedSpans().stream()
                .map(s -> s.tags().keySet())
                .flatMap(Set::stream)
                .filter(Tags.DB_STATEMENT.getKey()::equals))
        .isNotEmpty();
  }

  @Test
  public void shouldNotTracePersonalData() {
    Datastore datastore = getDatastore();
    Person person = new Person().setAge(18).setName("Max");
    datastore.save(person);

    MockTracer tracer = getMockTracer();
    assertThat(tracer.finishedSpans())
        .extracting(MockSpan::operationName)
        .contains("createIndexes", "insert");
    List<String> dbStatements =
        tracer.finishedSpans().stream()
            .map(MockSpan::tags)
            .map(Map::entrySet)
            .flatMap(Set::stream)
            .filter(e -> Tags.DB_STATEMENT.getKey().equals(e.getKey()))
            .map(Map.Entry::getValue)
            .map(Object::toString)
            .collect(Collectors.toList());
    assertThat(dbStatements)
        .anyMatch(v -> v.contains("\"name\": \"…\""))
        .anyMatch(v -> v.contains("\"age\": \"…\""))
        .noneMatch(v -> v.contains("\"age\": 18"))
        .noneMatch(v -> v.contains("\"name\": \"Max\""));
  }

  private Datastore getDatastore() {
    return DW.<MorphiaTestApp>getApplication().getMorphiaBundle().datastore();
  }

  private MockTracer getMockTracer() {
    return DW.<MorphiaTestApp>getApplication().getMockTracer();
  }

  public static class MorphiaTestApp extends Application<Config> {

    private MockTracer mockTracer = new MockTracer();

    private MorphiaBundle<Config> morphiaBundle =
        MorphiaBundle.builder()
            .withConfigurationProvider(Config::getMongo)
            .withEntity(Person.class)
            .withTracer(mockTracer)
            .build();

    @Override
    public void initialize(Bootstrap<Config> bootstrap) {
      bootstrap.addBundle(morphiaBundle);
    }

    @Override
    public void run(Config configuration, Environment environment) {
      // nothing to run
    }

    MorphiaBundle<Config> getMorphiaBundle() {
      return morphiaBundle;
    }

    MockTracer getMockTracer() {
      return mockTracer;
    }
  }
}
