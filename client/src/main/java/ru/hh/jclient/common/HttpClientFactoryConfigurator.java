package ru.hh.jclient.common;

import java.util.function.UnaryOperator;

public class HttpClientFactoryConfigurator<R extends RequestEngineBuilder<?>> {
  protected UnaryOperator<R> requestStrategyConfigurator() {
    return UnaryOperator.identity();
  }

  public HttpClientFactory<R> configure(HttpClientFactory<R> factory) {
    return factory.createCustomizedCopy(requestStrategyConfigurator());
  }
}
