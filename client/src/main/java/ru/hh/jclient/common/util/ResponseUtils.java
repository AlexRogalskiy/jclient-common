package ru.hh.jclient.common.util;

public class ResponseUtils {

  public static String toString(ru.hh.jclient.common.Response response) {
    return "r.h.j.c.Response {" +
        "uri=" + response.getUri() + "," +
        "statusCode=" + response.getStatusCode() + "," +
        "headers=" + response.getHeaders() + "," +
        "statusText=" + response.getStatusText() + ",";
  }

}
