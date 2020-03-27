package ru.hh.jclient.common;

public interface RequestEngineBuilder<R extends RequestEngineBuilder<R>> {
  RequestEngine build(Request request, RequestStrategy.RequestExecutor executor);
  HttpClient<R> backToClient();
}
