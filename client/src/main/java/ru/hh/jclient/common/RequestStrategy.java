package ru.hh.jclient.common;

import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public interface RequestStrategy<REB extends RequestEngineBuilder<REB>> {

  @FunctionalInterface
  interface RequestExecutor {
    CompletableFuture<ResponseWrapper> executeRequest(Request request, int retryCount, RequestContext context);
  }
  REB createRequestEngineBuilder(HttpClient<REB> client);
  void setTimeoutMultiplier(double timeoutMultiplier);
  RequestStrategy<REB> createCustomizedCopy(UnaryOperator<REB> configAction);
}
