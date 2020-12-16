package ru.hh.jclient.common.balancing;

import ru.hh.jclient.common.Monitoring;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

public interface UpstreamManager {

  void updateUpstream(@Nonnull String serviceName);

  Upstream getUpstream(String serviceName, @Nullable String profile);

  default Upstream getUpstream(String serviceName) {
    return getUpstream(serviceName, null);
  }

  Set<Monitoring> getMonitoring();
}
