package ru.hh.jclient.common.balancing;

import com.google.common.annotations.VisibleForTesting;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import ru.hh.jclient.common.Monitoring;
import ru.hh.jclient.consul.UpstreamConfigService;
import ru.hh.jclient.consul.UpstreamService;
import ru.hh.jclient.consul.model.ApplicationConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class BalancingUpstreamManager extends UpstreamManager {

  public static final String SCHEMA_SEPARATOR = "://";
  private static final int SCHEMA_SEPARATOR_LEN = 3;

  private final Map<String, UpstreamGroup> upstreams = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduledExecutor;
  private final Set<Monitoring> monitoring;
  private final String datacenter;
  private final boolean allowCrossDCRequests;
  private final UpstreamConfigService upstreamConfigService;
  private final UpstreamService upstreamService;

  public BalancingUpstreamManager(ScheduledExecutorService scheduledExecutor, Set<Monitoring> monitoring,
                                  String datacenter,
                                  boolean allowCrossDCRequests, UpstreamConfigService upstreamConfigService,
                                  UpstreamService upstreamService) {
    this(List.of(), scheduledExecutor, monitoring, datacenter, allowCrossDCRequests, upstreamConfigService, upstreamService);
  }

  public BalancingUpstreamManager(Collection<String> upstreamsList,
                                  ScheduledExecutorService scheduledExecutor,
                                  Set<Monitoring> monitoring,
                                  String datacenter,
                                  boolean allowCrossDCRequests, UpstreamConfigService upstreamConfigService,
                                  UpstreamService upstreamService) {
    this.scheduledExecutor = requireNonNull(scheduledExecutor, "scheduledExecutor must not be null");
    this.monitoring = requireNonNull(monitoring, "monitorings must not be null");
    this.datacenter = datacenter == null ? null : datacenter.toLowerCase();
    this.allowCrossDCRequests = allowCrossDCRequests;
    this.upstreamService = upstreamService;
    this.upstreamConfigService = upstreamConfigService;

    requireNonNull(upstreamsList, "upstreamsList must not be null");
    upstreamsList.forEach(this::updateUpstream);
  }

  @Override
  public List<Server> getServersForService(String serviceName) {
    return upstreamService.getServers(serviceName);
  }

  @Override
  public void updateUpstream(@Nonnull String upstreamName) {
    var upstreamKey = Upstream.UpstreamKey.ofComplexName(upstreamName);

    ApplicationConfig upstreamConfig = upstreamConfigService.getUpstreamConfig(upstreamKey.getServiceName());
    var newConfig = UpstreamConfig.fromApplicationConfig(upstreamConfig, UpstreamConfig.DEFAULT, upstreamKey.getProfileName());
    List<Server> servers = upstreamService.getServers(upstreamName);
    upstreams.compute(upstreamKey.getServiceName(), (serviceName, existingGroup) -> {
      if (existingGroup == null) {
        return new UpstreamGroup(serviceName, upstreamKey.getProfileName(), createUpstream(upstreamKey, newConfig, servers));
      }
      return existingGroup.addOrUpdate(upstreamKey.getProfileName(), newConfig, servers,
          (profileName, config) -> createUpstream(upstreamKey, newConfig, servers)
      );
    });
  }

  private Upstream createUpstream(Upstream.UpstreamKey key, UpstreamConfig config, List<Server> servers) {
    return new Upstream(key, config, servers, scheduledExecutor, datacenter, allowCrossDCRequests, true);
  }

  @Override
  public Upstream getUpstream(String serviceName, @Nullable String profile) {
    return ofNullable(upstreams.get(getNameWithoutScheme(serviceName)))
        .map(group -> group.getUpstreamOrDefault(profile)).orElse(null);
  }

  @Override
  public Set<Monitoring> getMonitoring() {
    return Set.copyOf(monitoring);
  }

  static String getNameWithoutScheme(String host) {
    int beginIndex = host.indexOf(SCHEMA_SEPARATOR) + SCHEMA_SEPARATOR_LEN;
    return beginIndex > 2 ? host.substring(beginIndex) : host;
  }

  @Override
  @VisibleForTesting
  Map<String, UpstreamGroup> getUpstreams() {
    return upstreams;
  }
}
