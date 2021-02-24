package org.noear.nami.coder.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.noear.nami.Decoder;
import org.noear.nami.NamiConfig;
import org.noear.nami.common.Constants;
import org.noear.nami.common.Result;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author noear
 * @since 1.2
 */
public class JacksonDecoder implements Decoder {
    public static final JacksonDecoder instance = new JacksonDecoder();


    ObjectMapper mapper_type = new ObjectMapper();

    public JacksonDecoder(){
        mapper_type.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper_type.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper_type.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper_type.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper_type.activateDefaultTypingAsProperty(
                mapper_type.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE, "@type");
    }

    @Override
    public String enctype() {
        return Constants.CONTENT_TYPE_JSON;
    }

    @Override
    public <T> T decode(Result rst, Type type) {
        String str = rst.bodyAsString();

        Object returnVal = null;
        try {
            if (str == null) {
                return (T) str;
            }
            returnVal = mapper_type.readValue(str, new TypeReferenceImp(type));

        } catch (Throwable ex) {
            returnVal = ex;
        }

        if (returnVal != null && Throwable.class.isAssignableFrom(returnVal.getClass())) {
            if (returnVal instanceof RuntimeException) {
                throw (RuntimeException) returnVal;
            } else {
                throw new RuntimeException((Throwable) returnVal);
            }
        } else {
            return (T) returnVal;
        }
    }

    @Override
    public void filter(NamiConfig cfg, String action, String url, Map<String, String> headers, Map<String, Object> args) {
        headers.put(Constants.HEADER_SERIALIZATION, Constants.AT_TYPE_JSON);
        headers.put(Constants.HEADER_ACCEPT, Constants.CONTENT_TYPE_JSON);
    }
}
