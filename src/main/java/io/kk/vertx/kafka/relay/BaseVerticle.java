package io.kk.vertx.kafka.relay;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author keke
 */
public abstract class BaseVerticle extends AbstractVerticle {
  public static final String BOOTSTRAP_SERVERS = "bootstrap.servers";

  protected static final String ADDRESSES = "vertx.addresses";
  protected static final String KAFAK = "kafka";
  protected static final String END_KAFAK = "." + KAFAK;
  private static final Logger LOG = LoggerFactory.getLogger(BaseVerticle.class);
  protected final List<String> bServers;
  protected List<String> addresses = new ArrayList<>();

  public BaseVerticle(List<String> bServers) {
    this.bServers = Objects.requireNonNull(bServers);
  }

  protected Map<String, Object> loadConfig(JsonObject config) throws IOException {
    JsonObject kafkaConfig = config.getJsonObject("kafka");
    Objects.requireNonNull(kafkaConfig);
    return kafkaConfig.getMap();
  }

  protected void updateBServers(List<String> bServers, Map<String, Object> config) {
    if (bServers != null && !bServers.isEmpty()) {
      config.put(BOOTSTRAP_SERVERS, StringUtils.join(bServers, ","));
    } else {
      String bootServers = System.getenv("KFK_BSERVERS");
      if (StringUtils.isNotBlank(bootServers)) {
        config.put(BOOTSTRAP_SERVERS, bootServers);
      }
    }
  }

  protected Map<String, Object> updateConfig(Map<String, Object> config) {
    String clientId = System.getProperty("CLIENT_ID");
    if (StringUtils.isNotBlank(clientId)) {
      LOG.info("client id: {}", clientId);
      config.put("client.id", clientId);
    }
    updateBServers(bServers, config);
    LOG.debug("Kafka Config : {}", config);
    return config;
  }
}
