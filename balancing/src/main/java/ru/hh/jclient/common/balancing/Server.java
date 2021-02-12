package ru.hh.jclient.common.balancing;

import static java.util.Objects.requireNonNull;
import static ru.hh.jclient.common.balancing.AdaptiveBalancingStrategy.DOWNTIME_DETECTOR_WINDOW;
import static ru.hh.jclient.common.balancing.AdaptiveBalancingStrategy.RESPONSE_TIME_TRACKER_WINDOW;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class Server {
  private static final String DELIMITER = ":";

  private final String address;
  private final String datacenter;
  private volatile int weight;
  private volatile Map<String, String> meta;
  private volatile List<String> tags;
  //no matter if concurrently consistent
  private boolean warmupEnded;
  private long warmupEndNanos;

  private volatile int fails = 0;
  private volatile int statsRequests = 0;

  private final DowntimeDetector downtimeDetector;
  private final ResponseTimeTracker responseTimeTracker;

  public Server(String address, int weight, String datacenter) {
    this.address = requireNonNull(address, "address should not be null");
    this.weight = weight;
    this.datacenter = datacenter;

    this.downtimeDetector = new DowntimeDetector(DOWNTIME_DETECTOR_WINDOW);
    this.responseTimeTracker = new ResponseTimeTracker(RESPONSE_TIME_TRACKER_WINDOW);
  }

  public static String addressFromHostPort(String host, int port) {
    return host + DELIMITER + port;
  }

  synchronized void acquire() {
    statsRequests++;
  }

  synchronized void release(boolean isError) {
    if (isError) {
      fails++;
    } else {
      fails = 0;
    }
  }

  void releaseAdaptive(boolean isError, long responseTimeMicros) {
    if (isError) {
      downtimeDetector.failed();
    } else {
      downtimeDetector.success();
      responseTimeTracker.time(responseTimeMicros);
    }
  }

  synchronized void rescaleStatsRequests() {
    statsRequests -= weight;
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

  public String getDatacenterLowerCased() {
    return datacenter == null ? null : datacenter.toLowerCase();
  }

  public int getFails() {
    return fails;
  }

  public int getStatsRequests() {
    return statsRequests;
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

  public void setMeta(Map<String, String> meta) {
    this.meta = meta;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public void setWeight(int weight) {
    this.weight = weight;
  }

  @Override
  public String toString() {
    return "Server{" +
      "address='" + address + '\'' +
      ", weight=" + weight +
      ", datacenter='" + datacenter + '\'' +
      ", meta=" + meta +
      ", tags=" + tags +
      ", fails=" + fails +
      ", statsRequests=" + statsRequests +
      '}';
  }

  public boolean tryEndWarmup(int statRequestsToSetAfterWarmup) {
    if (!warmupEnded && System.nanoTime() > warmupEndNanos) {
      warmupEnded = true;
      statsRequests = statRequestsToSetAfterWarmup;
    }
    return warmupEnded;
  }

  public void setWarmupEnded(boolean warmupEnded) {
    this.warmupEnded = warmupEnded;
  }

  public void setWarmupEndNanosIfNeeded(int slowStartSeconds) {
    if (!warmupEnded && slowStartSeconds > 0) {
      this.warmupEndNanos = System.nanoTime() + (long) (Math.random() * Duration.ofSeconds(slowStartSeconds).toNanos());
    }
  }
}
