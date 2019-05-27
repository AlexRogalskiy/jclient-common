package ru.hh.jclient.common;

import static java.util.Objects.requireNonNull;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import ru.hh.jclient.common.responseconverter.TypeConverter;
import ru.hh.jclient.common.exception.ClientResponseException;
import ru.hh.jclient.common.exception.ResponseConverterException;

public class ResultOrErrorProcessor<T, E> {

  private ResultProcessor<T> responseProcessor;
  private TypeConverter<E> errorConverter;
  private Predicate<Integer> errorStatus = HttpClient.OK_STATUS.negate();

  ResultOrErrorProcessor(ResultProcessor<T> responseProcessor, TypeConverter<E> errorConverter) {
    this.responseProcessor = requireNonNull(responseProcessor, "responseProcessor must not be null");
    this.errorConverter = requireNonNull(errorConverter, "errorConverter must not be null");
  }

  /**
   * Specifies HTTP status code that is eligible for ERROR response parsing. It must not comply with {@link HttpClient#OK_STATUS}.
   *
   * @param status HTTP status code that converter will be used for
   */
  public ResultOrErrorProcessor<T, E> forStatus(int status) {
    return forStatus(Set.of(status));
  }

  /**
   * Specifies range of HTTP status codes that are eligible for ERROR response parsing. These must not comply with {@link HttpClient#OK_STATUS}.
   *
   * @param statusCodes HTTP status codes that converter will be used for
   */
  public ResultOrErrorProcessor<T, E> forStatus(Set<Integer> statusCodes) {
    if (statusCodes.stream().anyMatch(HttpClient.OK_STATUS)) {
      throw new IllegalArgumentException(String.format("Statuses %s intersect with non-error statuses", statusCodes.toString()));
    }
    this.errorStatus = statusCodes::contains;
    return this;
  }

  /**
   * Returns future containing wrapper that consists of:
   * <ul>
   * <li>expected result, if HTTP status code complies with {@link HttpClient#OK_STATUS}, otherwise {@link Optional#empty()}</li>
   * <li>error result, if HTTP status code does NOT comply with {@link HttpClient#OK_STATUS}, otherwise {@link Optional#empty()}</li>
   * <li>response object</li>
   * </ul>
   *
   * By default ERROR result will be parsed if HTTP status code does NOT comply with {@link HttpClient#OK_STATUS}. More specific range can be
   * specified using {@link #forStatus(Set)} method. Once called, any errors not in that range will NOT be parsed and can be handled manually.
   *
   * @return {@link ResultOrErrorWithResponse} object with results of response processing
   * @throws ResponseConverterException if failed to process response with either normal or error converter
   */
  public CompletableFuture<ResultOrErrorWithResponse<T, E>> resultWithResponse() {
    return responseProcessor.getHttpClient().unconverted().thenApply(this::wrapResponseAndError);
  }

  /**
   * Returns future containing wrapper that consists of:
   * <ul>
   * <li>expected result, if HTTP status code complies with {@link HttpClient#OK_STATUS}, otherwise {@link Optional#empty()}</li>
   * <li>error result, if HTTP status code does NOT comply with {@link HttpClient#OK_STATUS}, otherwise {@link Optional#empty()}</li>
   * <li>response status code</li>
   * </ul>
   *
   * By default ERROR result will be parsed if HTTP status code does NOT comply with {@link HttpClient#OK_STATUS}. More specific range can be
   * specified using {@link #forStatus(Set)} method. Once called, any errors not in that range will NOT be parsed and can be handled manually.
   *
   * @return {@link ResultOrErrorWithStatus} object with results of response processing
   * @throws ResponseConverterException if failed to process response with either normal or error converter
   */
  public CompletableFuture<ResultOrErrorWithStatus<T, E>> resultWithStatus() {
    return resultWithResponse().thenApply(ResultOrErrorWithResponse::hideResponse);
  }

  private ResultOrErrorWithResponse<T, E> wrapResponseAndError(Response response) {
    Optional<T> value;
    Optional<E> errorValue;
    try {
      if (HttpClient.OK_STATUS.test(response.getStatusCode())) {
        value = responseProcessor.getConverter().converterFunction().apply(response).get();
        errorValue = Optional.empty();

        responseProcessor.getHttpClient().getDebug().onResponseConverted(value);
      }
      else {
        value = Optional.empty();
        errorValue = parseError(response);

        responseProcessor.getHttpClient().getDebug().onResponseConverted(errorValue);
      }
      return new ResultOrErrorWithResponse<>(value, errorValue, response);
    }
    catch (ClientResponseException e) {
      throw e;
    }
    catch (Exception e) {
      ResponseConverterException rce = new ResponseConverterException("Failed to convert response", e);
      responseProcessor.getHttpClient().getDebug().onConverterProblem(rce);
      throw rce;
    }
    finally {
      responseProcessor.getHttpClient().getDebug().onProcessingFinished();
    }
  }

  private Optional<E> parseError(Response response) throws Exception {
    if (errorStatus.test(response.getStatusCode()) && !(response.getDelegate() instanceof MappedTransportErrorResponse)) {
      return errorConverter.converterFunction().apply(response).get();
    }
    return Optional.empty();
  }

}
