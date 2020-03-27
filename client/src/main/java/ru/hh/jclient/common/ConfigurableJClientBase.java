package ru.hh.jclient.common;

public abstract class ConfigurableJClientBase<T extends ConfigurableJClientBase<T>>
    extends AbstractConfigurableJClientBase<RequestEngineBuilder<?>, T> {

  public ConfigurableJClientBase(String host, HttpClientFactory<RequestEngineBuilder<?>> http) {
    super(host, http);
  }

  public ConfigurableJClientBase(String host, String path, HttpClientFactory<RequestEngineBuilder<?>> http) {
    super(host, path, http);
  }
}
