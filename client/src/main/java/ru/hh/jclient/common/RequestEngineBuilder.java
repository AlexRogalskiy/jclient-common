package ru.hh.jclient.common;

public interface RequestEngineBuilder<R extends RequestEngineBuilder<?>> {
  RequestEngine build(Request request, RequestStrategy.RequestExecutor executor);
  HttpClient<R> backToClient();
}
