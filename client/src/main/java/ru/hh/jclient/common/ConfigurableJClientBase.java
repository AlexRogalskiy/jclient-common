package ru.hh.jclient.common;

import java.util.function.UnaryOperator;

public abstract class ConfigurableJClientBase<R extends RequestEngineBuilder<R>, T extends ConfigurableJClientBase<R, T>> extends JClientBase<R> {

  private final HttpClientFactory<R> http;

  public ConfigurableJClientBase(String host, HttpClientFactory<R> http) {
    super(host, http);
    this.http = super.http;
  }

  public ConfigurableJClientBase(String host, String path, HttpClientFactory<R> http) {
    super(host, path, http);
    this.http = super.http;
  }

  protected HttpClientFactory<R> getHttp() {
    return this.http;
  }

  /**
   * method to get NEW preconfigured instance of the client
   * @param configurator configurationAction to apply to engineBuilder instance *before* passing into {@link HttpClient} instance
   * @return copy of the client with preconfigured engine
   * @throws IllegalStateException if copy is the same instance or has the same {@link HttpClientFactory} instance
   */
  public T withPreconfiguredEngine(UnaryOperator<R> configurator) {
    T copy;
    try {
      copy = createCustomizedCopy(new HttpClientFactoryConfigurator<>() {
        @Override
        protected UnaryOperator<R> requestStrategyConfigurator() {
          return configurator;
        }
      });
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create client copy", e);
    }
    if (copy == this || copy.getHttp() == this.getHttp()) {
      throw new IllegalStateException(
          "You returned an instance with same ru.hh.jclient.common.HttpClientFactory. "
              + "If you need customization - use HttpClientFactory#customized(java.util.function.UnaryOperator) to obtain new HttpClientFactory"
              + "Otherwise consider to use ru.hh.jclient.common.JClientBase as base class"
      );
    }
    return copy;
  }

  /**
   * The method should return a customized copy of the current client
   * We assume you will use {@link HttpClientFactoryConfigurator#configure(ru.hh.jclient.common.HttpClientFactory)}
   * to get a new instance of {@link HttpClientFactory}
   * @param configurator configurationAction to apply to {@link HttpClientFactory} instance *before* creating {@link HttpClient} instance
   * All manipulations with builder from {@link HttpClient} instance have priority over configurator
   * @return NEW instance of the same type as this, but with customized {@link HttpClientFactory}
   */
  protected abstract T createCustomizedCopy(HttpClientFactoryConfigurator<R> configurator) throws Exception;
}
