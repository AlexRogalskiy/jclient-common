package ru.hh.jclient.common.responseconverter;

import static java.util.Objects.requireNonNull;
import java.util.Collection;
import java.util.Set;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import ru.hh.jclient.common.Response;
import ru.hh.jclient.common.ResultWithResponse;
import ru.hh.jclient.common.util.MoreFunctionalInterfaces.FailableFunction;

public class XmlConverter<T> extends SingleTypeConverter<T> {

  private static final Set<MediaType> MEDIA_TYPES = Set.of(MediaType.TEXT_XML_TYPE, MediaType.APPLICATION_XML_TYPE);
  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private JAXBContext context;
  private Class<T> xmlClass;

  public XmlConverter(JAXBContext context, Class<T> xmlClass) {
    this.context = requireNonNull(context, "context must not be null");
    this.xmlClass = requireNonNull(xmlClass, "xmlClass must not be null");
  }

  @Override
  public FailableFunction<Response, ResultWithResponse<T>, Exception> singleTypeConverterFunction() {
    return r -> {
      Source source = new StreamSource(r.getResponseBodyAsStream());
      XMLStreamReader reader = XML_INPUT_FACTORY.createXMLStreamReader(source);
      JAXBElement<T> root = context.createUnmarshaller().unmarshal(reader, xmlClass);
      return new ResultWithResponse<>(root.getValue(), r);
    };
  }

  @Override
  protected Collection<MediaType> getMediaTypes() {
    return MEDIA_TYPES;
  }
}
