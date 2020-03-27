package ru.hh.jclient.common.balancing;

import ru.hh.jclient.common.AbstractConfigurableJClientBase;
import ru.hh.jclient.common.HttpClientFactory;

public abstract class BalancingJClientBase<T extends BalancingJClientBase<T>> extends AbstractConfigurableJClientBase<RequestBalancerBuilder, T> {

  public BalancingJClientBase(String host, HttpClientFactory<RequestBalancerBuilder> http) {
    super(host, http);
  }

  public BalancingJClientBase(String host, String path, HttpClientFactory<RequestBalancerBuilder> http) {
    super(host, path, http);
  }
}
