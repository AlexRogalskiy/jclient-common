package ru.hh.jclient.common;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.AsyncHttpProviderConfig;
import com.ning.http.client.filter.RequestFilter;
import com.ning.http.client.providers.netty.NettyAsyncHttpProvider;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import ru.hh.jclient.common.metric.MetricConsumer;
import ru.hh.jclient.common.util.MDCCopy;
import ru.hh.jclient.common.util.stats.SlowRequestsLoggingHandler;
import ru.hh.jclient.common.util.storage.Storage;

import javax.net.ssl.SSLContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

//TODO rename to HttpClientFactoryBuilder
public final class HttpClientConfig {
  public static final double DEFAULT_TIMEOUT_MULTIPLIER = 1;

  private final AsyncHttpClientConfig.Builder configBuilder;

  private UpstreamManager upstreamManager = new DefaultUpstreamManager();
  private Executor callbackExecutor;
  private NettyAsyncHttpProviderConfig nettyConfig;
  private Set<String> hostsWithSession;
  private Storage<HttpClientContext> contextSupplier;
  private double timeoutMultiplier = DEFAULT_TIMEOUT_MULTIPLIER;
  private boolean provideExtendedMetrics;

  private MetricConsumer metricConsumer;

  public static HttpClientConfig basedOn(Properties properties) {
    AsyncHttpClientConfig.Builder configBuilder = new AsyncHttpClientConfig.Builder();
    ofNullable(properties.getProperty(ConfigKeys.USER_AGENT)).ifPresent(configBuilder::setUserAgent);
    ofNullable(properties.getProperty(ConfigKeys.MAX_CONNECTIONS)).map(Integer::parseInt).ifPresent(configBuilder::setMaxConnections);
    ofNullable(properties.getProperty(ConfigKeys.MAX_REQUEST_RETRIES)).map(Integer::parseInt).ifPresent(configBuilder::setMaxRequestRetry);
    ofNullable(properties.getProperty(ConfigKeys.CONNECTION_TIMEOUT_MS)).map(Integer::parseInt).ifPresent(configBuilder::setConnectTimeout);
    ofNullable(properties.getProperty(ConfigKeys.READ_TIMEOUT_MS)).map(Integer::parseInt).ifPresent(configBuilder::setReadTimeout);
    ofNullable(properties.getProperty(ConfigKeys.REQUEST_TIMEOUT_MS)).map(Integer::parseInt).ifPresent(configBuilder::setRequestTimeout);
    ofNullable(properties.getProperty(ConfigKeys.FOLLOW_REDIRECT)).map(Boolean::parseBoolean).ifPresent(configBuilder::setFollowRedirect);
    ofNullable(properties.getProperty(ConfigKeys.COMPRESSION_ENFORCED)).map(Boolean::parseBoolean).ifPresent(configBuilder::setCompressionEnforced);
    ofNullable(properties.getProperty(ConfigKeys.ALLOW_POOLING_CONNECTIONS)).map(Boolean::parseBoolean)
        .ifPresent(configBuilder::setAllowPoolingConnections);
    ofNullable(properties.getProperty(ConfigKeys.ACCEPT_ANY_CERTIFICATE)).map(Boolean::parseBoolean)
        .ifPresent(configBuilder::setAcceptAnyCertificate);
    HttpClientConfig httpClientConfig = new HttpClientConfig(configBuilder);
    ofNullable(properties.getProperty(ConfigKeys.TIMEOUT_MULTIPLIER)).map(Double::parseDouble).ifPresent(httpClientConfig::withTimeoutMultiplier);
    ofNullable(properties.getProperty(ConfigKeys.PROVIDE_EXTENDED_METRICS)).map(Boolean::parseBoolean)
        .ifPresent(httpClientConfig::withExtendedMetrics);
    httpClientConfig.nettyConfig = new NettyAsyncHttpProviderConfig();
    //to be able to monitor netty boss thread pool. See: com.ning.http.client.providers.netty.channel.ChannelManager
    httpClientConfig.nettyConfig.setBossExecutorService(Executors.newCachedThreadPool());
    configBuilder.setAsyncHttpClientProviderConfig(httpClientConfig.nettyConfig);
    ofNullable(properties.getProperty(ConfigKeys.SLOW_REQ_THRESHOLD_MS)).map(Integer::parseInt).ifPresent(slowRequestThreshold ->
        setSlowRequestHandler(httpClientConfig, slowRequestThreshold));
    return httpClientConfig;
  }

  private static void setSlowRequestHandler(HttpClientConfig httpClientConfig, Integer slowRequestThreshold) {
    if (slowRequestThreshold <= 0) {
      return;
    }
    NettyAsyncHttpProviderConfig.AdditionalPipelineInitializer initializer = pipeline ->
        pipeline.addFirst("slowRequestLogger", new SlowRequestsLoggingHandler(slowRequestThreshold, slowRequestThreshold << 2, MILLISECONDS));
    httpClientConfig.nettyConfig.setHttpAdditionalPipelineInitializer(initializer);
  }

  /**
   * use this only if there's not enough "with*" methods to cover all requirements
   * example: you need to set {@link AsyncHttpProviderConfig}
   * example: you need to set {@link RequestFilter}
   * @param asyncClientConfig instance of {@link AsyncHttpClientConfig}
   * @return instance of HttpClientConfig based on passed config to continue building
   */
  public static HttpClientConfig basedOnNativeConfig(Object asyncClientConfig) {
    if (!(asyncClientConfig instanceof AsyncHttpClientConfig)) {
      throw new IllegalArgumentException("Argument must be of " + AsyncHttpClientConfig.class.getName());
    }
    return new HttpClientConfig(new AsyncHttpClientConfig.Builder((AsyncHttpClientConfig) asyncClientConfig));
  }

  private HttpClientConfig(AsyncHttpClientConfig.Builder configBuilder) {
    this.configBuilder = configBuilder;
  }

  public HttpClientConfig withUpstreamManager(UpstreamManager upstreamManager) {
    this.upstreamManager = upstreamManager;
    return this;
  }

  public HttpClientConfig withExecutorService(ExecutorService applicationThreadPool) {
    this.configBuilder.setExecutorService(applicationThreadPool);
    return this;
  }

  public HttpClientConfig withCallbackExecutor(Executor callbackExecutor) {
    this.callbackExecutor = callbackExecutor;
    return this;
  }

  public HttpClientConfig withBossNettyExecutor(ExecutorService nettyBossExecutorService) {
    this.nettyConfig.setBossExecutorService(nettyBossExecutorService);
    return this;
  }

  public HttpClientConfig withHostsWithSession(Collection<String> hostsWithSession) {
    this.hostsWithSession = new HashSet<>(hostsWithSession);
    return this;
  }

  public HttpClientConfig withStorage(Storage<HttpClientContext> contextSupplier) {
    this.contextSupplier = contextSupplier;
    return this;
  }

  public HttpClientConfig withMetricConsumer(MetricConsumer metricConsumer) {
    this.metricConsumer = metricConsumer;
    return this;
  }

  public HttpClientConfig withSSLContext(SSLContext sslContext) {
    this.configBuilder.setSSLContext(sslContext);
    return this;
  }

  public HttpClientConfig acceptAnyCetificate(boolean enabled) {
    this.configBuilder.setAcceptAnyCertificate(enabled);
    return this;
  }

  public HttpClientConfig withTimeoutMultiplier(double timeoutMultiplier) {
    this.timeoutMultiplier = timeoutMultiplier;
    return this;
  }

  public HttpClientConfig withSlowRequestLogging(int slowRequestThreshold) {
    setSlowRequestHandler(this, slowRequestThreshold);
    return this;
  }

  public HttpClientConfig withExtendedMetrics(boolean provideExtendedMetrics) {
    this.provideExtendedMetrics = provideExtendedMetrics;
    return this;
  }

  public HttpClientBuilder build() {
    HttpClientBuilder httpClientBuilder = new HttpClientBuilder(
      buildClient(),
      hostsWithSession,
      contextSupplier,
      callbackExecutor,
      buildUpstreamManager()
    );
    ofNullable(metricConsumer).ifPresent(consumer -> consumer.accept(httpClientBuilder.getMetricProvider(provideExtendedMetrics)));
    return httpClientBuilder;
  }

  private AsyncHttpClient buildClient() {
    AsyncHttpClientConfig clientConfig = applyTimeoutMultiplier(configBuilder).build();
    return MDCCopy.doWithoutContext(() -> new AsyncHttpClient(new NettyAsyncHttpProvider(clientConfig), clientConfig));
  }

  private UpstreamManager buildUpstreamManager() {
    upstreamManager.setTimeoutMultiplier(timeoutMultiplier);
    return upstreamManager;
  }

  private AsyncHttpClientConfig.Builder applyTimeoutMultiplier(AsyncHttpClientConfig.Builder clientConfigBuilder) {
    AsyncHttpClientConfig config = clientConfigBuilder.build();
    AsyncHttpClientConfig.Builder builder = new AsyncHttpClientConfig.Builder(config);
    builder.setConnectTimeout(config.getConnectTimeout() > 0 ? (int)(config.getConnectTimeout() * timeoutMultiplier) : config.getConnectTimeout());
    builder.setReadTimeout(config.getReadTimeout() > 0 ? (int)(config.getReadTimeout() * timeoutMultiplier) : config.getReadTimeout());
    builder.setRequestTimeout(config.getRequestTimeout() > 0 ? (int)(config.getRequestTimeout() * timeoutMultiplier) : config.getRequestTimeout());
    return builder;
  }

  public HttpClientConfig withUserAgent(String userAgent) {
    configBuilder.setUserAgent(userAgent);
    return this;
  }

  public HttpClientConfig withMaxConnections(int maxConnections) {
    configBuilder.setMaxConnections(maxConnections);
    return this;
  }

  public HttpClientConfig withMaxRequestRetries(int maxRequestRetries) {
    configBuilder.setMaxRequestRetry(maxRequestRetries);
    return this;
  }

  public HttpClientConfig withConnectTimeoutMs(int connectTimeoutMs) {
    configBuilder.setConnectTimeout(connectTimeoutMs);
    return this;
  }

  public HttpClientConfig withReadTimeoutMs(int readTimeoutMs) {
    configBuilder.setReadTimeout(readTimeoutMs);
    return this;
  }

  public HttpClientConfig withRequestTimeoutMs(int requestTimeoutMs) {
    configBuilder.setRequestTimeout(requestTimeoutMs);
    return this;
  }

  public static final class ConfigKeys {
    private ConfigKeys() {
    }

    public static final String PROVIDE_EXTENDED_METRICS = "provideExtendedMetrics";
    public static final String SLOW_REQ_THRESHOLD_MS = "slowRequestThresholdMs";
    public static final String USER_AGENT = "userAgent";

    public static final String MAX_CONNECTIONS = "maxTotalConnections";
    public static final String MAX_REQUEST_RETRIES = "maxRequestRetries";

    public static final String CONNECTION_TIMEOUT_MS = "connectionTimeoutMs";
    public static final String READ_TIMEOUT_MS = "readTimeoutMs";
    public static final String REQUEST_TIMEOUT_MS = "requestTimeoutMs";

    public static final String TIMEOUT_MULTIPLIER = "timeoutMultiplier";

    public static final String FOLLOW_REDIRECT = "followRedirect";
    public static final String COMPRESSION_ENFORCED = "compressionEnforced";
    public static final String ALLOW_POOLING_CONNECTIONS = "allowPoolingConnections";

    public static final String ACCEPT_ANY_CERTIFICATE = "acceptAnyCertificate";
  }
}
