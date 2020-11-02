package org.sdase.commons.server.kafka.health;

import com.codahale.metrics.health.HealthCheck;
import org.sdase.commons.server.healthcheck.ExternalHealthCheck;
import org.sdase.commons.server.kafka.KafkaConfiguration;

@ExternalHealthCheck
public class ExternalKafkaHealthCheck extends HealthCheck {
  private final KafkaConfiguration config;

  public ExternalKafkaHealthCheck(KafkaConfiguration config) {
    this.config = config;
  }

  @Override
  protected HealthCheck.Result check() throws Exception {
    KafkaHealthCheck check = new KafkaHealthCheck(config);
    HealthCheck.Result result = check.execute();

    if (result.isHealthy()) {
      return HealthCheck.Result.healthy();
    } else {
      return HealthCheck.Result.unhealthy(result.getMessage());
    }
  }
}
