package ru.hh.jclient.common.responseconverter;

import javax.ws.rs.core.MediaType;
import ru.hh.jclient.common.Response;
import ru.hh.jclient.common.ResultWithResponse;
import ru.hh.jclient.common.util.MoreFunctionalInterfaces.FailableFunction;

import java.util.Collection;
import java.util.Optional;

public class VoidConverter implements TypeConverter<Void> {

  @Override
  public FailableFunction<Response, ResultWithResponse<Void>, Exception> converterFunction() {
    return r -> new ResultWithResponse<>(null, r);
  }

  @Override
  public Optional<Collection<MediaType>> getSupportedMediaTypes() {
    return Optional.empty();
  }
}
