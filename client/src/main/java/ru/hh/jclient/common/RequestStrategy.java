package ru.hh.jclient.common;

import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public interface RequestStrategy<REB extends RequestEngineBuilder> {

  @FunctionalInterface
  interface RequestExecutor {
    CompletableFuture<ResponseWrapper> executeRequest(Request request, int retryCount, RequestContext context);
  }
  REB createRequestEngineBuilder();
  void setTimeoutMultiplier(double timeoutMultiplier);
  RequestStrategy<REB> createCustomizedCopy(UnaryOperator<REB> configAction);
}
