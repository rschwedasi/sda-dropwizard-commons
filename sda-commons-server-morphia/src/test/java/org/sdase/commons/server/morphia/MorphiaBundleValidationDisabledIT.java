package org.sdase.commons.server.morphia;

import static io.dropwizard.testing.ConfigOverride.config;
import static io.dropwizard.testing.ResourceHelpers.resourceFilePath;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.morphia.Datastore;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.sdase.commons.server.mongo.testing.MongoDbRule;
import org.sdase.commons.server.morphia.test.Config;
import org.sdase.commons.server.morphia.test.model.Person;

/** Tests if entities are NOT validated if validation was disabled. */
public class MorphiaBundleValidationDisabledIT {

  private static final MongoDbRule MONGODB = MongoDbRule.builder().build();

  private static final DropwizardAppRule<Config> DW =
      new DropwizardAppRule<>(
          MorphiaTestApp.class,
          resourceFilePath("test-config.yaml"),
          config("mongo.hosts", MONGODB::getHost),
          config("mongo.database", MONGODB::getDatabase));

  @ClassRule public static final RuleChain CHAIN = RuleChain.outerRule(MONGODB).around(DW);

  @Test
  public void shouldStoreInvalidPerson() {
    assertThatCode(
            () -> {
              Datastore datastore = getDatastore();
              datastore.save(new Person());
            })
        .doesNotThrowAnyException();
  }

  @Test
  public void shouldStoreValidPerson() {
    assertThatCode(
            () -> {
              Datastore datastore = getDatastore();
              datastore.save(new Person().setName("Name"));
            })
        .doesNotThrowAnyException();
  }

  private Datastore getDatastore() {
    return DW.<MorphiaTestApp>getApplication().getMorphiaBundle().datastore();
  }

  public static class MorphiaTestApp extends Application<Config> {

    private MorphiaBundle<Config> morphiaBundle =
        MorphiaBundle.builder()
            .withConfigurationProvider(Config::getMongo)
            .withEntity(Person.class)
            .withoutValidation()
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
  }
}
