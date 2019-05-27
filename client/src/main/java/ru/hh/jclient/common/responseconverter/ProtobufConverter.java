package ru.hh.jclient.common.responseconverter;

import static java.util.Objects.requireNonNull;
import static ru.hh.jclient.common.util.MediaTypes.PROTOBUF;
import static ru.hh.jclient.common.util.MediaTypes.X_PROTOBUF;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

import com.google.protobuf.GeneratedMessageV3;
import javax.ws.rs.core.MediaType;
import ru.hh.jclient.common.Response;
import ru.hh.jclient.common.ResultWithResponse;
import ru.hh.jclient.common.util.MoreFunctionalInterfaces.FailableFunction;

public class ProtobufConverter<T extends GeneratedMessageV3> extends SingleTypeConverter<T> {

  private static final Set<MediaType> MEDIA_TYPES = Set.of(PROTOBUF, X_PROTOBUF);

  private Class<T> protobufClass;

  public ProtobufConverter(Class<T> protobufClass) {
    this.protobufClass = requireNonNull(protobufClass, "protobufClass");
  }

  @SuppressWarnings("unchecked")
  @Override
  public FailableFunction<Response, ResultWithResponse<T>, Exception> singleTypeConverterFunction() {
    return r -> {
      Method parseFromMethod = protobufClass.getMethod("parseFrom", InputStream.class);
      return new ResultWithResponse<>((T) parseFromMethod.invoke(null, r.getResponseBodyAsStream()), r);
    };
  }

  @Override
  protected Collection<MediaType> getMediaTypes() {
    return MEDIA_TYPES;
  }
}
