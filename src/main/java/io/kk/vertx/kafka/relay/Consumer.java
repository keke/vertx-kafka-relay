package io.kk.vertx.kafka.relay;

import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author keke
 */
public class Consumer extends BaseVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(Consumer.class);
  private static final String RELAY_CONSUMER_TOPICS_UPDATE = "relay.consumer.topics.update";
  private VertxMessageFactory<?> factory;
  private ExecutorService executors;
  private List<String> topics;
  private KafkaConsumer<String, String> consumer;
  private AtomicBoolean consuming = new AtomicBoolean(true);

  public Consumer(List<String> bServers) {
    super(bServers);
  }


  @Override
  public void start(Future<Void> startFuture) throws Exception {
    LOG.debug("Topics {}", addresses);
    factory = (VertxMessageFactory<?>) Class.forName(config().getString("messageFactory")).newInstance();
    executors = Executors.newSingleThreadExecutor();
    addresses = config().getJsonArray(ADDRESSES, new JsonArray()).getList();
    if (addresses.isEmpty()) {
      vertx.eventBus().localConsumer(RELAY_CONSUMER_TOPICS_UPDATE, (Message<JsonArray> e) -> {
        addresses.addAll(e.body().getList());
        submitConsumerJob();
      });
    } else {
      submitConsumerJob();
    }
    super.start(startFuture);
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    consuming.set(false);
    executors.shutdown();
    consumer.close();
    super.stop(stopFuture);
  }

  private void submitConsumerJob() {
    topics = addresses.stream().map(s -> s + END_KAFAK).collect(Collectors.toList());
    executors.submit(() -> {
      try {
        consumer = new KafkaConsumer<String, String>(updateConfig(loadConfig(config())));
        startConsume(topics);
      } catch (IOException ex) {
        LOG.error("Unable to start Consumer", ex);
        throw new RuntimeException(ex);
      }
    });
  }

  private void startConsume(List<String> topics) {
    LOG.debug("To subscript topics : {}", topics);
    consumer.subscribe(topics);
    long interval = config().getLong("interval");
    long pullTimeout = config().getLong("pullTimeout");
    LOG.debug("To run consumers with pulltimeout={}, interval={}", pullTimeout, interval);
    while (consuming.get()) {
      ConsumerRecords<String, String> records = consumer.poll(pullTimeout);
      if (records != null) {
        records.forEach((ConsumerRecord<String, String> record) -> {
          String topic = record.topic();
          String address = StringUtils.remove(topic, END_KAFAK);
          LOG.debug("Relaying a kafka message: {}, publish to vertx address: {}", topic, address);
          DeliveryOptions deliveryOptions = new DeliveryOptions();
          String key = record.key();
          if (StringUtils.isNotBlank(key)) {
            try {
              JsonObject keyObj = new JsonObject(key);
              keyObj.forEach(keyEntry -> {
                String value = keyEntry.getKey();
                JsonArray ary = (JsonArray) keyEntry.getValue();
                ary.forEach(item -> {
                  deliveryOptions.addHeader(value, item.toString());
                });
              });
            } catch (DecodeException e) {
              LOG.warn("Unable to decode string [" + key + "] to JSON");
            }
          }
          vertx.eventBus().publish(address, factory.message(record.value()), deliveryOptions);
        });
      }
      try {
        Thread.sleep(interval);
      } catch (InterruptedException e) {
        LOG.warn("Sleep was interrupted", e);
      }
    }
    consumer.close();
  }


  protected Map<String, Object> updateConfig(Map<String, Object> config) {
    String group = System.getProperty("CONSUMER_GROUP_ID");
    if (StringUtils.isNotBlank(group)) {
      config.put("group.id", group);
      LOG.info("Consumer group.id: {}", group);
    }
    return super.updateConfig(config);
  }
}
