package io.kk.vertx.kafka.relay;

/**
 * @author keke
 * @version 0.0.4
 * @since 0.0.4
 */
public interface VertxMessageFactory<T> {
  /**
   * Create a Vertx message object from String
   *
   * @param value - a string which is received from Kafka
   * @return message object <code>T</code> from the strong
   */
  T message(String value);
}
