package ru.hh.jclient.common;

import java.util.function.UnaryOperator;

public class DefaultRequestStrategy implements RequestStrategy<RequestEngineBuilder> {
  @Override
  public RequestEngineBuilder createRequestEngineBuilder() {
    return new DefaultEngineBuilder();
  }

  @Override
  public void setTimeoutMultiplier(double timeoutMultiplier) {
  }

  @Override
  public RequestStrategy<RequestEngineBuilder> createCustomizedCopy(UnaryOperator<RequestEngineBuilder> configAction) {
    throw new IllegalCallerException("There is no customization for default strategy");
  }
}
