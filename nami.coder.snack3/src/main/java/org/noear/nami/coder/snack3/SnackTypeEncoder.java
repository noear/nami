package org.noear.nami.coder.snack3;

import org.noear.nami.Encoder;
import org.noear.nami.common.Constants;
import org.noear.snack.ONode;

import java.nio.charset.StandardCharsets;

public class SnackTypeEncoder implements Encoder {
    public static final SnackTypeEncoder instance = new SnackTypeEncoder();

    @Override
    public String enctype() {
        return Constants.CONTENT_TYPE_JSON_TYPE;
    }

    @Override
    public byte[] encode(Object obj) {
        return ONode.serialize(obj).getBytes(StandardCharsets.UTF_8);
    }
}
