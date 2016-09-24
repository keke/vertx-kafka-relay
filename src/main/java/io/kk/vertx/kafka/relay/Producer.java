package io.kk.vertx.kafka.relay;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * @author keke
 */
public class Producer extends BaseVerticle {
  private static final Logger LOG = LoggerFactory.getLogger(Producer.class);
  private static final String RELAY_PRODUCER_TOPICS_UPDATE = "relay.producer.topics.update";
  private KafkaProducer<String, String> producer;

  public Producer(List<String> bServers) {
    super(bServers);
  }

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    List<String> addrs = config().getJsonArray(ADDRESSES, new JsonArray()).getList();
    addTopics(addrs);
    vertx.eventBus().localConsumer(RELAY_PRODUCER_TOPICS_UPDATE, (Message<JsonArray> e) -> {
      addTopics(e.body().getList());
      e.reply("ok");
    });
    producer = new KafkaProducer<String, String>(updateConfig(loadConfig(config())));

    super.start(startFuture);
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    producer.close();
    super.stop(stopFuture);
  }

  private void addTopics(List<String> addrs) {
    addrs.forEach(add -> {
      if (!addresses.contains(add)) {
        vertx.eventBus().consumer(add, this::sendToKafka);
        addresses.add(add);
      }
    });
    LOG.debug("Addresses added - {}", addresses);
  }

  private <T> void sendToKafka(Message<T> message) {
    LOG.debug("Receive a Vertx message on address={}", message.address());
    JsonObject keyObj = new JsonObject();
    message.headers().forEach(e -> {
      JsonArray ary = keyObj.getJsonArray(e.getKey());
      if (ary == null) {
        ary = new JsonArray();
        keyObj.put(e.getKey(), ary);
      }
      ary.add(e.getValue());
    });
    ProducerRecord<String, String> record = new ProducerRecord<String, String>(message.address() + END_KAFAK,
        keyObj.toString(), message.body().toString());

    producer.send(record, (metadata, exception) -> {
      if (exception != null) {
        LOG.error("Unable to send message :{}", message.body());
        LOG.error("Error", exception);
      }
      LOG.debug("Sent a message {}", metadata);
    });
  }
}
