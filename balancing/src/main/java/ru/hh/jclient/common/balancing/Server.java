package ru.hh.jclient.common.balancing;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static java.util.Objects.requireNonNull;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static ru.hh.jclient.common.balancing.AdaptiveBalancingStrategy.DOWNTIME_DETECTOR_WINDOW;
import static ru.hh.jclient.common.balancing.AdaptiveBalancingStrategy.RESPONSE_TIME_TRACKER_WINDOW;

//TODO move to ru.hh.jclient.common.balancing.internal
public class Server {
  private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);
  private static final String DELIMITER = ":";

  private final String address;
  private final String datacenter;

  private final AtomicLong requests;
  private final AtomicInteger fails;

  private final DowntimeDetector downtimeDetector;
  private final ResponseTimeTracker responseTimeTracker;

  private volatile int weight;
  private volatile Map<String, String> meta;
  private volatile List<String> tags;

  /**
   * not volatile for optimization. Should protect writes with {@link Server#slowStartEndMillis}
   */
  private boolean slowStartModeEnabled;
  private volatile long slowStartEndMillis = 0;

  /**
   * not volatile for optimization. Should protect writes with {@link Server#slowStartEndMillis}
   */
  private boolean statisticsFilledWithInitialValues;

  private volatile StampedLock lock;
  private volatile int statLimit;

  public Server(String address, int weight, String datacenter) {
    this.address = requireNonNull(address, "address should not be null");
    this.weight = weight;
    this.datacenter = datacenter;

    this.downtimeDetector = new DowntimeDetector(DOWNTIME_DETECTOR_WINDOW);
    this.responseTimeTracker = new ResponseTimeTracker(RESPONSE_TIME_TRACKER_WINDOW);

    this.requests = new AtomicLong();
    this.fails = new AtomicInteger();
  }

  public static String addressFromHostPort(String host, int port) {
    return host + DELIMITER + port;
  }

  void acquire() {
    executeWithLockIfAvailable(()-> requests.addAndGet(packRequests(1, 1)));
  }

  void release(boolean isRetry, boolean isError) {
    executeWithLockIfAvailable(()-> {
      requests.updateAndGet(i -> {
        int stat = unpackStatRequests(i);
        if (isRetry) {
          stat = stat > 0 ? stat - 1 : 0;
        }
        int current = unpackCurrentRequests(i);
        current = current > 0 ? current - 1 : 0;
        return packRequests(stat, current);
      });
      fails.updateAndGet(i -> {
        if (isError) {
          return i < Integer.MAX_VALUE ? i + 1 : Integer.MAX_VALUE;
        } else {
          return 0;
        }
      });
    });
  }

  void releaseAdaptive(boolean isError, long responseTimeMicros) {
    if (isError) {
      downtimeDetector.failed();
    } else {
      downtimeDetector.success();
      responseTimeTracker.time(responseTimeMicros);
    }
  }

  /**
   * protected by {@link Upstream#rescale(java.util.List)}
   */
  void rescaleStatsRequests() {
    requests.updateAndGet(reqs -> {
      int statRequests = unpackStatRequests(reqs);
      if (statRequests < statLimit) {
        return reqs;
      }
      int currentRequests = unpackCurrentRequests(reqs);
      return packRequests(statRequests >> 1, currentRequests);
    });
  }

  public String getAddress() {
    return address;
  }

  public int getWeight() {
    return weight;
  }

  public String getDatacenter() {
    return datacenter;
  }

  public int getFails() {
    return fails.get();
  }

  public DowntimeDetector getDowntimeDetector() {
    return downtimeDetector;
  }

  public ResponseTimeTracker getResponseTimeTracker() {
    return responseTimeTracker;
  }

  public Map<String, String> getMeta() {
    return meta;
  }

  //TODO can have effect on server selection but changes without locking
  public void setMeta(Map<String, String> meta) {
    this.meta = meta;
  }

  public List<String> getTags() {
    return tags;
  }

  //TODO can have effect on server selection but changes without locking
  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  //TODO can have effect on server selection but changes without locking
  public Server setWeight(int weight) {
    this.weight = weight;
    return this;
  }

  public float getStatLoad(Collection<Server> currentServers, Clock clock) {
    if (slowStartModeEnabled) {
      long currentTimeMillis = getCurrentTimeMillis(clock);
      if (slowStartEndMillis > 0 && currentTimeMillis <= slowStartEndMillis) {
        LOGGER.trace(
          "Server {} is on slowStart, returning infinite load. Current epoch millis: {}, slow start end epoch millis: {}",
          this, currentTimeMillis, slowStartEndMillis
        );
        return Float.POSITIVE_INFINITY;
      }
      LOGGER.trace("Slow start for server {} ended", this);
      slowStartModeEnabled = false;
    }
    if (!statisticsFilledWithInitialValues) {
      statisticsFilledWithInitialValues = true;
      slowStartEndMillis = -1;
      requests.updateAndGet(value -> {
        if (!slowStartModeEnabled) {
          int initialStat = (int) Math.floor(calculateMaxRealStatLoad(currentServers) * weight);
          LOGGER.trace("Server {} statistics has no init value. Calculated initial statRequests={}", this, initialStat);
          return packRequests(initialStat, unpackCurrentRequests(value));
        }
        return value;
      });
    }
    return calculateLoad();
  }

  public int getRequests() {
    return unpackCurrentRequests(requests.get());
  }

  public int getStatsRequests() {
    return unpackStatRequests(requests.get());
  }

  boolean needToRescale() {
    return getStatsRequests() >= statLimit;
  }

  static double calculateMaxRealStatLoad(Collection<Server> servers) {
    return servers.stream().mapToDouble(Server::calculateLoad).max().orElse(0d);
  }

  private float calculateLoad() {
    long requests = this.requests.get();
    return (float) (unpackStatRequests(requests) + unpackCurrentRequests(requests)) / this.weight;
  }

  protected long getCurrentTimeMillis(Clock clock) {
    return clock.millis();
  }

  public void setSharedLock(StampedLock lock) {
    this.lock = lock;
  }

  public void setStatLimit(int statLimit) {
    this.statLimit = statLimit;
  }

  public void setSlowStartEndTimeIfNeeded(int slowStartSeconds, Clock clock) {
    if (slowStartSeconds > 0) {
      if (slowStartEndMillis == 0) {
        slowStartModeEnabled = true;
        this.slowStartEndMillis = (long) (getCurrentTimeMillis(clock) + (Math.random() * Duration.ofSeconds(slowStartSeconds).toMillis()));
        LOGGER.trace("Set slow start for server {}. Slow start is going to end at {} epoch millis", this, slowStartEndMillis);
      }
    }
  }

  /**
   * pack two metrics in one int to be consistent on read
   * Attention: each value is effectively constraint by {@link java.lang.Short} with max value ~32k
   * so be careful with rescaling
   * @param statRequests stat requests
   * @param currentRequests current requests
   * @return int value containing two metrics
   */
  private static long packRequests(int statRequests, int currentRequests) {
    long packedValue = (((long) statRequests) << 32) + currentRequests;
    if (packedValue < 0) {
      LOGGER.warn("Packed stats value overflow: stat={}, current={}. Setting MAX value", statRequests, currentRequests);
      packedValue = Long.MAX_VALUE;
    }
    return packedValue;
  }

  private static int unpackStatRequests(long requestsValue) {
    return (int) (requestsValue >> 32);
  }

  private static int unpackCurrentRequests(long requestsValue) {
    return (int) requestsValue;
  }

  private void executeWithLockIfAvailable(Runnable action) {
    if (lock == null) {
      action.run();
      return;
    }

    long stamp = lock.writeLock();
    try {
      action.run();
    } finally {
      lock.unlockWrite(stamp);
    }
  }

  @Override
  public String toString() {
    long requestsValue = requests.get();
    return "Server{" +
       "address='" + address + '\'' +
       ", weight=" + weight +
       ", datacenter='" + datacenter + '\'' +
       ", meta=" + meta +
       ", tags=" + tags +
       ", requests=" + unpackCurrentRequests(requestsValue) +
       ", fails=" + fails +
       ", statsRequests=" + unpackStatRequests(requestsValue) +
       '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Server server = (Server) o;
    return address.equals(server.address) && Objects.equals(datacenter, server.datacenter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(address, datacenter);
  }
}
