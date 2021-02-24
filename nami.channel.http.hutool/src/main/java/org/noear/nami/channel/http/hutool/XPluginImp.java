package org.noear.nami.channel.http.hutool;

import org.noear.nami.NamiManager;
import org.noear.solon.SolonApp;
import org.noear.solon.core.Plugin;

/**
 * @author noear
 */
public class XPluginImp implements Plugin {
    @Override
    public void start(SolonApp app) {
        NamiManager.regIfAbsent("http", HttpChannel.instance);
        NamiManager.regIfAbsent("https", HttpChannel.instance);
    }
}
