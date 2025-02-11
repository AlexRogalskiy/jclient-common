package ru.hh.jclient.common.balancing;

import java.util.Optional;
import java.util.Set;
import ru.hh.jclient.common.Monitoring;
import ru.hh.jclient.common.Request;
import ru.hh.jclient.common.RequestContext;
import ru.hh.jclient.common.RequestStrategy;
import ru.hh.jclient.common.Response;
import ru.hh.jclient.common.ResponseWrapper;
import ru.hh.jclient.common.Uri;

public class ExternalUrlRequestor extends RequestBalancer {
  public static final String DC_FOR_EXTERNAL_REQUESTS = "externalRequest";
  private static final RetryPolicy DEFAULT_RETRY_POLICY = new RetryPolicy();

  private final String upstreamName;
  private final String dc;
  private final Set<Monitoring> monitorings;
  private int firstStatusCode;

  public ExternalUrlRequestor(Upstream upstream, Request request, RequestStrategy.RequestExecutor requestExecutor,
                              int requestTimeoutMs, int maxRequestTimeoutTries, int maxTries,
                              Double timeoutMultiplier, boolean forceIdempotence,
                              Set<Monitoring> monitorings
  ) {
    super(request, requestExecutor, requestTimeoutMs, maxRequestTimeoutTries, maxTries, timeoutMultiplier, forceIdempotence);
    this.upstreamName = Optional.ofNullable(upstream).map(Upstream::getName).orElse(null);
    this.dc = Optional.ofNullable(upstream).map(Upstream::getDatacenter).orElse(DC_FOR_EXTERNAL_REQUESTS);
    this.monitorings = monitorings;
  }

  @Override
  protected ImmediateResultOrPreparedRequest getResultOrContext(Request request) {
    return new ImmediateResultOrPreparedRequest(RequestContext.EMPTY_CONTEXT, request);
  }

  @Override
  protected void onRequestReceived(ResponseWrapper wrapper, long timeToLastByteMicros) {

  }

  @Override
  protected void onResponse(ResponseWrapper wrapper, int triesUsed, boolean willFireRetry) {
    for (Monitoring monitoring : monitorings) {
      int statusCode = wrapper.getResponse().getStatusCode();
      long requestTimeMicros = wrapper.getTimeToLastByteMicros();

      String serverAddress;
      Uri originalUri = request.getUri();
      Uri baseUri = new Uri(originalUri.getScheme(), null, originalUri.getHost(), originalUri.getPort(), null, null);
      serverAddress = baseUri.toString();
      String name = upstreamName != null ? upstreamName : serverAddress;

      monitoring.countRequest(name, dc, serverAddress, statusCode, requestTimeMicros, !willFireRetry);
      monitoring.countRequestTime(name, dc, requestTimeMicros);

      if (triesUsed > 1) {
        monitoring.countRetry(name, dc, serverAddress, statusCode, firstStatusCode, triesUsed);
      }
    }
  }

  @Override
  protected boolean checkRetry(Response response, boolean isIdempotent) {
    return DEFAULT_RETRY_POLICY.isRetriable(response, isIdempotent);
  }

  @Override
  protected void onRetry(int statusCode, Response response, int triesUsed) {
    if (triesUsed == 1) {
      firstStatusCode = statusCode;
    }
  }
}
