package ru.hh.jclient.common;

import java.util.function.UnaryOperator;

public class DefaultRequestStrategy implements RequestStrategy<DefaultEngineBuilder> {

  public DefaultRequestStrategy() {
  }

  @Override
  public DefaultEngineBuilder createRequestEngineBuilder(HttpClient<DefaultEngineBuilder> client) {
    return new DefaultEngineBuilder(client);
  }

  @Override
  public void setTimeoutMultiplier(double timeoutMultiplier) {
  }

  @Override
  public RequestStrategy<DefaultEngineBuilder> createCustomizedCopy(UnaryOperator<DefaultEngineBuilder> configAction) {
    throw new IllegalCallerException("There is no customization for default strategy");
  }
}
