package io.kk.vertx.kafka.relay;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author keke
 */
public class ProducerTest {
  /**
   *
   */
  @Test
  public void startTest_bsInVertxJson() throws Exception {

    List<String> servers = new ArrayList<>();
    Producer producer = new Producer(servers);
    Vertx mockVertx = mock(Vertx.class);
    Context mockContext = mock(Context.class);
    JsonObject config = new JsonObject();
    config.put("kafka", new JsonObject());
    config.getJsonObject("kafka").put("bootstrap.servers", "localhost:9020")
        .put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        .put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    EventBus mockEventBus = mock(EventBus.class);
    when(mockContext.config()).thenReturn(config);
    when(mockVertx.eventBus()).thenReturn(mockEventBus);

    producer.init(mockVertx, mockContext);
    Future<Void> mockFuture = mock(Future.class);
    producer.start(mockFuture);
    verify(mockFuture).complete();
  }

  @Test
  public void startTest_badConfigInVertxJson() throws Exception {

    List<String> servers = new ArrayList<>();
    Producer producer = new Producer(servers);
    Vertx mockVertx = mock(Vertx.class);
    Context mockContext = mock(Context.class);
    JsonObject config = new JsonObject();
    config.put("kafka", new JsonObject());

    EventBus mockEventBus = mock(EventBus.class);
    when(mockContext.config()).thenReturn(config);
    when(mockVertx.eventBus()).thenReturn(mockEventBus);

    producer.init(mockVertx, mockContext);
    Future<Void> mockFuture = mock(Future.class);
    producer.start(mockFuture);
    verify(mockFuture).fail(any(Throwable.class));
  }

  @Test
  public void stop() throws Exception {
    List<String> servers = new ArrayList<>();
    Producer producer = new Producer(servers);
    KafkaProducer mockKp = mock(KafkaProducer.class);
    producer.setProducer(mockKp);
    Future<Void> mockFuture = mock(Future.class);
    producer.stop(mockFuture);
    verify(mockKp).close();
  }
}
