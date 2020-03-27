package ru.hh.jclient.common;

public class DefaultEngineBuilder implements RequestEngineBuilder<DefaultEngineBuilder> {
  private final HttpClient<DefaultEngineBuilder> httpClient;

  public DefaultEngineBuilder(HttpClient<DefaultEngineBuilder> httpClient) {
    this.httpClient = httpClient;
  }

  @Override
  public RequestEngine build(Request request, RequestStrategy.RequestExecutor executor) {
    return () -> executor.executeRequest(request, 0, RequestContext.EMPTY_CONTEXT)
        .thenApply(ResponseWrapper::getResponse);
  }

  @Override
  public HttpClient<DefaultEngineBuilder> backToClient() {
    return httpClient;
  }
}
