package ru.hh.jclient.common;

public class DefaultEngineBuilder implements RequestEngineBuilder {
  @Override
  public RequestEngine build(Request request, RequestStrategy.RequestExecutor executor) {
    return () -> executor.executeRequest(request, 0, RequestContext.EMPTY_CONTEXT)
        .thenApply(ResponseWrapper::getResponse);
  }
}
