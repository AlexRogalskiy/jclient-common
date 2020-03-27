package ru.hh.jclient.common;

import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public interface RequestStrategy<R extends RequestEngineBuilder<?>> {

  @FunctionalInterface
  interface RequestExecutor {
    CompletableFuture<ResponseWrapper> executeRequest(Request request, int retryCount, RequestContext context);
  }
  R createRequestEngineBuilder(HttpClient<R> client);
  void setTimeoutMultiplier(double timeoutMultiplier);
  RequestStrategy<R> createCustomizedCopy(UnaryOperator<R> configAction);
}
