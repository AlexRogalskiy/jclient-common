package ru.hh.jclient.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.core.MediaType;

public class MediaTypes {
  
  private static final String CHARSET = "charset";
  
  public static MediaType addUtf8Charset(MediaType base) {
    return addCharset(base, StandardCharsets.UTF_8.name());
  }
  
  public static MediaType addCharset(MediaType base, String charset) {
    return new MediaType(base.getType(), base.getSubtype(), Map.of(CHARSET, charset));
  }
  
  public static Optional<String> getCharset(MediaType mediaType) {
    return Optional.ofNullable(mediaType.getParameters().get(CHARSET));
  }
  
  public static final MediaType PLAIN_TEXT_UTF_8 = addUtf8Charset(MediaType.TEXT_PLAIN_TYPE);
  
  public static final MediaType XML_UTF_8 = addUtf8Charset(MediaType.TEXT_XML_TYPE);
  public static final MediaType APPLICATION_XML_UTF_8 = addUtf8Charset(MediaType.APPLICATION_XML_TYPE);
  
  public static final MediaType JSON_UTF_8 = addUtf8Charset(MediaType.APPLICATION_JSON_TYPE);
  
  public static final MediaType PROTOBUF = new MediaType("application", "protobuf");
  public static final MediaType X_PROTOBUF = new MediaType("application", "x-protobuf");
}
