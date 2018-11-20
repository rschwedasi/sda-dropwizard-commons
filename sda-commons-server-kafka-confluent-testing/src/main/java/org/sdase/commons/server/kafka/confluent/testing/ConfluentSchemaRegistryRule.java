package org.sdase.commons.server.kafka.confluent.testing;

import io.confluent.kafka.schemaregistry.client.rest.entities.Schema;
import io.confluent.kafka.schemaregistry.exceptions.SchemaRegistryException;
import io.confluent.kafka.schemaregistry.rest.SchemaRegistryConfig;
import io.confluent.kafka.schemaregistry.rest.SchemaRegistryRestApplication;
import io.confluent.rest.Application;
import io.confluent.rest.logging.Slf4jRequestLog;
import org.apache.curator.test.InstanceSpec;
import org.eclipse.jetty.server.Server;

import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Iterator;
import java.util.Locale;
import java.util.Properties;

import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ConfluentSchemaRegistryRule implements TestRule {

   private SchemaRegistryRestApplication application;

   private int port;
   private String protocolType;
   private String hostname;
   private KafkaBrokerRule rule;
   private boolean started = false;

   private static final Logger LOG = LoggerFactory.getLogger(ConfluentSchemaRegistryRule.class);


   private ConfluentSchemaRegistryRule() {
   }

   @Override
   public Statement apply(Statement base, Description description) {
      return RuleChain.outerRule(rule).around((base1, description1) -> new Statement() {
         @Override
         public void evaluate() throws Throwable {
            before();
            try {
               base1.evaluate();
            } finally {
               after();
            }
         }
      }).apply(base, description);
   }


   protected void before() throws Exception {
      Properties schemaRegistryProps = new Properties();

      String bootstrapServerConfig = rule.getBrokerConnectStrings().stream()
            .map(s -> String.format("%s://%s", protocolType, s))
            .collect(Collectors.joining(","));

      if (port <= 0)  {
         // if port is not set, use a random one
         port = InstanceSpec.getRandomPort();
      }

      schemaRegistryProps.put(SchemaRegistryConfig.LISTENERS_CONFIG, "http://0.0.0.0:" + port);
      schemaRegistryProps.put(SchemaRegistryConfig.HOST_NAME_CONFIG, hostname);
      schemaRegistryProps.put(SchemaRegistryConfig.KAFKASTORE_BOOTSTRAP_SERVERS_CONFIG, bootstrapServerConfig);
      SchemaRegistryConfig config = new SchemaRegistryConfig(schemaRegistryProps);
      application = new SchemaRegistryRestApplication(config);

      Server server = application.createServer();
      server.start();
      started = true;
      deactivateLoggingBecauseOfVersionConflicts();
   }

   public int getPort() {
      return port;
   }


   public String getConnectionString() {
      if (!started) {
         throw new IllegalStateException("Cannot access before application is started");
      }
      return String.format(Locale.ROOT, "http://%s:%s", hostname, port);
   }


   private void deactivateLoggingBecauseOfVersionConflicts() {
      Slf4jRequestLog requestLog = (Slf4jRequestLog) Stream.of(Application.class.getDeclaredFields()).filter(f -> "requestLog".equals(f.getName())).findFirst().map(f -> {
         try {
            f.setAccessible(true);
            return f.get(application);
         } catch (IllegalAccessException e) {
            LOG.warn("Error when trying to remove logger for request log. Maybe the application will fail with NoSuchMethodException when logger is still active", e);
         }
         return null;
      }).orElse(null);

      Stream.of(Slf4jRequestLog.class.getDeclaredFields()).filter(f -> f.getName().equals("logger")).findFirst().map(f -> {
         try {
            f.setAccessible(true);
            f.set(requestLog, null);
         } catch (IllegalAccessException e) {
            LOG.warn("Error when trying to remove logger for request log. Maybe the application will fail with NoSuchMethodException when logger is still active", e);
         }
         return null;
      });
   }


   protected void after() {
      started = false;
      application.onShutdown();
   }

   public void registerSchema(String subject, int version, int id, String schema) throws SchemaRegistryException {
      Schema restSchema = new Schema(subject, version, id, schema);
      application.schemaRegistry().register(subject, restSchema);
   }

   public Iterator<Schema> getAllSchemaVersions(String subject) throws SchemaRegistryException {
      return application.schemaRegistry().getAllVersions(subject, false);
   }

   public static OptionalBuilder builder() {
      return new Builder();
   }

   public interface FinalBuilder {
      public ConfluentSchemaRegistryRule build();
   }


   public interface OptionalBuilder {
      public OptionalBuilder withProtocol(String protocolType);
      public OptionalBuilder withPort(int port);
      public OptionalBuilder withHostname(String hostname);
      public FinalBuilder withKafkaBrokerRule(KafkaBrokerRule rule);
   }



   private static class Builder implements FinalBuilder, OptionalBuilder {

      private int port = 0;
      private String hostname = "localhost";
      private String protocolType = "PLAINTEXT";
      private KafkaBrokerRule rule;


      public OptionalBuilder withPort(int port) {
         this.port = port;
         return this;
      }

      public OptionalBuilder withHostname(String hostname) {
         this.hostname = hostname;
         return this;
      }

      @Override
      public FinalBuilder withKafkaBrokerRule(KafkaBrokerRule rule) {
         this.rule = rule;
         return this;
      }

      @Override
      public OptionalBuilder withProtocol(String protocolType) {
         this.protocolType = protocolType;
         return this;
      }

      @Override
      public ConfluentSchemaRegistryRule build() {
         ConfluentSchemaRegistryRule result = new ConfluentSchemaRegistryRule();
         result.rule = rule;
         result.port = port;
         result.hostname = hostname;
         result.protocolType = protocolType;
         return result;
      }
   }
}
