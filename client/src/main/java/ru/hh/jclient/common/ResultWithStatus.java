package ru.hh.jclient.common;

import java.util.Optional;

/**
 * Wrapper object that contains response status code and result of conversion. This wrapper can be used outside of implementing client.
 * 
 * @param <T> type of conversion result
 */
public class ResultWithStatus<T> {

  private Optional<T> value;
  private int statusCode;

  public ResultWithStatus(T value, int statusCode) {
    this.value = Optional.ofNullable(value);
    this.statusCode = statusCode;
  }

  /**
   * @return result of response conversion. Can be {@link Optional#empty() empty} if error has happened
   */
  public Optional<T> get() {
    return value;
  }

  /**
   * @return response status code
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * @return true if response status code complies with {@link HttpClient#OK_STATUS}
   */
  public boolean isSuccess() {
    return HttpClient.OK_STATUS.test(statusCode);
  }
}
