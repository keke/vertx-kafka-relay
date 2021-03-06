package io.kk.vertx.kafka.relay;

import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.retry.RetryForever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author keke
 * @version 0.0.1
 */
public class RelayService extends AbstractVerticle {
  /**
   * Event to trigger relay deployment.
   * <pre>
   * <code>
   *   eventbus.publish(RelayService.DEPLOY_RELAY);
   * </code>
   * </pre>
   */
  public static final String DEPLOY_RELAY = "deploy.relay";
  /**
   * Config attribute: <code>lazyDeploy</code>: whether want to do lazy deploy of relay service.
   */
  public static final String LAZY_DEPLOY = "lazyDeploy";
  private static final Logger LOG = LoggerFactory.getLogger(RelayService.class);
  private static final String CONSUMER = "consumer";
  private static final String PRODUCER = "producer";

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    if (config().getBoolean(LAZY_DEPLOY, false))
      vertx.eventBus().consumer(DEPLOY_RELAY, (message) -> doDeploy(startFuture));
    else
      doDeploy(startFuture);
  }

  @Override
  public void stop(Future<Void> stopFuture) throws Exception {
    super.stop(stopFuture);
  }

  private <T> void doDeploy(Future<Void> startFuture) {
    List<Future> fs = new ArrayList<>();
    Future<List<String>> future = getBootServers();
    future.setHandler(h -> {
      if (h.succeeded()) {
        List<String> bServers = h.result();
        deployInner(fs, bServers, startFuture);

      } else {
        startFuture.fail(h.cause());
      }
    });
  }

  private void deployInner(List<Future> fs, List<String> bServers, Future<Void> startFuture) {
    LOG.debug("Bootstrap servers are {}", bServers);
    fs.add(deploy(new Consumer(bServers), CONSUMER));
    fs.add(deploy(new Producer(bServers), PRODUCER));
    CompositeFuture.all(fs).setHandler(r -> {
      if (r.succeeded()) {
        startFuture.complete();
        LOG.info("Relay were deployed");
      } else {
        startFuture.fail(r.cause());
      }
    });
  }

  private Future<String> deploy(Verticle verticle, String name) {
    Future<String> f = Future.future();
    vertx.deployVerticle(verticle, new DeploymentOptions(config().getJsonObject(name)), f.completer());
    return f;
  }

  private String getZkConnectionString() {
    String zkConnectString = System.getenv("ZK_CONNECT_STRING");
    if (StringUtils.isBlank(zkConnectString)) {
      zkConnectString = config().getString("zkConnectString");
    }
    return zkConnectString;
  }

  private boolean getBootstrapsFromZookeeper() {
    String value = System.getenv("BS_FROM_ZOOKEEPER");
    if (StringUtils.isBlank(value)) {
      value = config().getString("bsFromZookeeper");
    }
    if (StringUtils.isNotBlank(value))
      return Boolean.parseBoolean(value);
    return false;
  }


  private Future<List<String>> getBootServers() {
    Future<List<String>> future = Future.future();
    List<String> bServers = new ArrayList<>();
    if (getBootstrapsFromZookeeper()) {
      String zkConnectionString = getZkConnectionString();
      if (StringUtils.isNotBlank(zkConnectionString)) {
        LOG.info("To connect zookeeper at {}", zkConnectionString);
        CuratorFramework zookeeperClient = CuratorFrameworkFactory.builder()
            .connectString(zkConnectionString).retryPolicy(new RetryForever(100))
            .build();
        zookeeperClient.start();
        String root = "/brokers/ids";
        PathChildrenCache cache = new PathChildrenCache(zookeeperClient, root, true);
        cache.getListenable().addListener((client, event) -> {
          LOG.debug("Event: {}-size={}", event.getType(), cache.getCurrentData().size());
          if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_ADDED)) {
            LOG.info("Got kafka brokers, number={}", cache.getCurrentData().size());

            cache.getCurrentData().forEach(data -> {
              JsonObject obj = new JsonObject(new String(data.getData()));
              bServers.add(obj.getString("host") + ":" + obj.getInteger("port"));
            });
            future.complete(bServers);
            cache.close();
            zookeeperClient.close();
          }
        });
        try {
          cache.start();
        } catch (Exception e) {
          LOG.error("Unable to start cache", e);
          future.fail(e);
        }
      } else {
        future.complete(bServers);
      }
    } else {
      future.complete(bServers);
    }
    return future;
  }
}
