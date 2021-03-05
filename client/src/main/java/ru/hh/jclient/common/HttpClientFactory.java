package ru.hh.jclient.common;

import javax.annotation.Nullable;
import org.asynchttpclient.AsyncHttpClient;
import ru.hh.jclient.common.metrics.MetricsProvider;
import ru.hh.jclient.common.telemetry.TelemetryListener;
import ru.hh.jclient.common.util.storage.Storage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;

public class HttpClientFactory {

  private final AsyncHttpClient http;
  private final Set<String> hostsWithSession;
  private final Storage<HttpClientContext> contextSupplier;
  private final Executor callbackExecutor;
  private final TelemetryListener telemetryListener;
  private final RequestStrategy<? extends RequestEngineBuilder<?>> requestStrategy;
  private final List<HttpClientEventListener> eventListeners;

  public HttpClientFactory(AsyncHttpClient http, Set<String> hostsWithSession, Storage<HttpClientContext> contextSupplier,
                           TelemetryListener telemetryListener) {
    this(http, hostsWithSession, contextSupplier, Runnable::run, telemetryListener);
  }

  public HttpClientFactory(AsyncHttpClient http,
                           Set<String> hostsWithSession,
                           Storage<HttpClientContext> contextSupplier,
                           Executor callbackExecutor, @Nullable TelemetryListener telemetryListener) {
    this(http, hostsWithSession, contextSupplier, callbackExecutor, new DefaultRequestStrategy()
        , telemetryListener
    );
  }

  public HttpClientFactory(AsyncHttpClient http,
                           Set<String> hostsWithSession,
                           Storage<HttpClientContext> contextSupplier,
                           Executor callbackExecutor,
                           RequestStrategy<?> requestStrategy,
                           @Nullable TelemetryListener telemetryListener
  ) {
    this(http, hostsWithSession, contextSupplier, callbackExecutor, requestStrategy, List.of(), telemetryListener);
  }

  public HttpClientFactory(AsyncHttpClient http,
                           Set<String> hostsWithSession,
                           Storage<HttpClientContext> contextSupplier,
                           Executor callbackExecutor,
                           RequestStrategy<?> requestStrategy,
                           List<HttpClientEventListener> eventListeners,
                           @Nullable TelemetryListener telemetryListener) {
    this.http = requireNonNull(http, "http must not be null");
    this.hostsWithSession = requireNonNull(hostsWithSession, "hostsWithSession must not be null");
    this.contextSupplier = requireNonNull(contextSupplier, "contextSupplier must not be null");
    this.callbackExecutor = requireNonNull(callbackExecutor, "callbackExecutor must not be null");
    this.requestStrategy = requireNonNull(requestStrategy, "upstreamManager must not be null");
    this.eventListeners = eventListeners;
    this.telemetryListener = telemetryListener;
  }

  /**
   * Specifies request to be executed. This is a starting point of request execution chain.
   *
   * @param request
   *          to execute
   */
  public HttpClient with(Request request) {
    return new HttpClientImpl(
        http,
        requireNonNull(request, "request must not be null"),
        hostsWithSession,
        requestStrategy,
        contextSupplier,
        callbackExecutor,
        eventListeners,
        telemetryListener
        );
  }

  /**
   * @return returns copy (within case insensitive map) of headers contained within global (incoming) request
   */
  public Map<String, List<String>> getHeaders() {
    Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    headers.putAll(contextSupplier.get().getHeaders());
    return headers;
  }

  AsyncHttpClient getHttp() {
    return http;
  }

  MetricsProvider getMetricProvider() {
    return MetricsProviderFactory.from(getHttp());
  }

  Storage<HttpClientContext> getContextSupplier() {
    return contextSupplier;
  }

  /**
   * create customized copy of the factory
   * @param mapper action to customize {@link RequestStrategy}
   * @return new instance of httpClientFactory
   * @throws ClassCastException if strategy type differs from required customization
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public HttpClientFactory createCustomizedCopy(UnaryOperator<? extends RequestEngineBuilder> mapper) {
    return new HttpClientFactory(this.http, this.hostsWithSession, this.contextSupplier, this.callbackExecutor,
                                 this.requestStrategy.createCustomizedCopy((UnaryOperator) mapper),
                                 this.eventListeners, this.telemetryListener);
  }
}
