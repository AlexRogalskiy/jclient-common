package ru.hh.jclient.common.balancing;

import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.junit.Test;
import ru.hh.jclient.common.HttpClientContext;
import ru.hh.jclient.common.HttpClientFactory;
import ru.hh.jclient.common.HttpClientFactoryBuilder;
import ru.hh.jclient.common.RequestBuilder;
import ru.hh.jclient.common.Response;
import ru.hh.jclient.common.util.storage.SingletonStorage;
import ru.hh.jclient.consul.UpstreamConfigService;
import ru.hh.jclient.consul.UpstreamService;
import ru.hh.jclient.consul.ValueNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static ru.hh.jclient.common.HttpStatuses.INTERNAL_SERVER_ERROR;

public class BalancingStrategyIntegrationTest {
  private static final AsyncHttpClientConfig defaultHttpClientConfig = new DefaultAsyncHttpClientConfig.Builder().build();

  private HttpClientFactory http;
  private HttpClientContext httpClientContext;
  private BalancingRequestStrategy strategy;

  @Test
  public void testConnectionReset() throws ExecutionException, InterruptedException {
    String testUpstream = "test-upstream";
    var resettingServerData = createRstServer();
    var commonServerData = createSuccessServer(300);
    ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    BalancingUpstreamManager upstreamManager = new BalancingUpstreamManager(scheduledExecutorService, Set.of(), "test", false, new UpstreamConfigService() {
      @Override
      public ValueNode getUpstreamConfig() {
        ValueNode root = new ValueNode();
        ValueNode upstreamNode = root.computeMapIfAbsent(testUpstream);
        ValueNode hostNode = upstreamNode.computeMapIfAbsent(UpstreamConfig.DEFAULT);
        ValueNode profileNode = hostNode.computeMapIfAbsent(UpstreamConfig.PROFILE_NODE);
        ValueNode profile = profileNode.computeMapIfAbsent(UpstreamConfig.DEFAULT);
        profile.putValue("max_tries", "2");
        profile.putValue("max_fails", "1");
        return root;
      }

      @Override
      public void setupListener(Consumer<String> callback) {
      }
    }, new UpstreamService() {
      @Override
      public void setupListener(Consumer<String> callback) {
      }

      @Override
      public List<Server> getServers(String serviceName) {
        return List.of(new Server(resettingServerData.getKey(), 1, "test"), new Server(commonServerData.getKey(), 1, "test"));
      }
    });
    upstreamManager.updateUpstream(testUpstream);
    var strategy = new BalancingRequestStrategy(upstreamManager);
    var contextSupplier = new SingletonStorage<>(() -> new HttpClientContext(Map.of(), Map.of(), List.of()));
    var httpClientFactory = new HttpClientFactoryBuilder(contextSupplier, List.of())
      .withRequestStrategy(strategy)
      .withCallbackExecutor(Runnable::run)
      .build();
    CompletableFuture<Response> future = httpClientFactory.with(new RequestBuilder("GET").setUrl("http://" + testUpstream).build()).unconverted();
    Response response = future.get();
    assertEquals(INTERNAL_SERVER_ERROR, response.getStatusCode());
  }

  private Map.Entry<String, Thread> createRstServer() {
    Exchanger<Integer> portHolder = new Exchanger<>();
    var t = new Thread(() -> {
      try (ServerSocket ss = new ServerSocket(0)) {
        portHolder.exchange(ss.getLocalPort());
        while (true) {
          try (Socket socket = ss.accept()) {
            socket.setSoLinger(true, 0);
            var is = socket.getInputStream();
            //to not eliminate read
            System.out.println(is.read());
          }
        }
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new RuntimeException(e);
      }
    });
    t.setDaemon(true);
    t.start();
    return Map.entry(tryGetAddress(portHolder), t);
  }

  private Map.Entry<String, Thread> createSuccessServer(long sleepMs) {
    Exchanger<Integer> portHolder = new Exchanger<>();
    var t = new Thread(() -> {
      try (ServerSocket ss = new ServerSocket(0)) {
        portHolder.exchange(ss.getLocalPort());
        while (true) {
          try (Socket socket = ss.accept();
               var is = new InputStreamReader(socket.getInputStream());
               var bis = new BufferedReader(is);
               var output = new PrintWriter(socket.getOutputStream())
          ) {
            long start = System.currentTimeMillis();
            while (bis.ready()) {
              System.out.println(bis.readLine());
            }
            if (sleepMs > 0) {
              Thread.sleep(sleepMs);
            }
            System.out.println("Read body in " + (System.currentTimeMillis() - start) + "ms");
            output.println("HTTP/1.1 200 OK");
            output.println("");
            output.flush();
            System.out.println("Handled request in " + (System.currentTimeMillis() - start) + "ms");
          }
        }
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new RuntimeException(e);
      }
    });
    t.setDaemon(true);
    t.start();
    return Map.entry(tryGetAddress(portHolder), t);
  }

  private String tryGetAddress(Exchanger<Integer> portHolder) {
    try {
      return "http://localhost:" + portHolder.exchange(0);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
