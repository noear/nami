package org.noear.nami;

import org.noear.nami.annotation.NamiClient;
import org.noear.nami.common.Constants;
import org.noear.nami.common.Result;
import org.noear.solon.core.util.PrintUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Nami（Solon rest * rpc client）
 *
 * @author noear
 * @since 1.0
 * */
public class Nami {
    /**
     * 默认的序列化器（涉及第三方框架引用，不做定义）
     */
    public static Encoder defaultEncoder;

    /**
     * 默认的反序列化器（涉及第三方框架引用，不做定义）
     */
    public static Decoder defaultDecoder;


    private String _url;
    private String _action = "POST";
    private Method _method;
    private final NamiConfig _config;

    public Nami() {
        _config = new NamiConfig().init();
    }

    /**
     * 给Builder使用
     */
    protected Nami(NamiConfig config) {
        _config = config;
        config.init();
    }

    /**
     * 设置请求方法
     */
    public Nami method(Method method) {
        if (method != null) {
            _method = method;
        }
        return this;
    }


    /**
     * 设置请求动作
     */
    public Nami action(String action) {
        if (action != null && action.length() > 0) {
            _action = action;
        }
        return this;
    }

    /**
     * 设置请求地址
     */
    public Nami url(String url) {
        _url = url;
        return this;
    }

    /**
     * 设置请求地址
     */
    public Nami url(String url, String fun) {
        if (url.indexOf("{fun}") > 0) {
            _url = url.replace("{fun}", fun);
        } else {
            if (fun == null) {
                _url = url;
            } else {
                StringBuilder sb = new StringBuilder(200);

                sb.append(url);
                if (url.endsWith("/")) {
                    if (fun.startsWith("/")) {
                        sb.append(fun.substring(1));
                    } else {
                        sb.append(fun);
                    }
                } else {
                    if (fun.startsWith("/")) {
                        sb.append(fun);
                    } else {
                        sb.append("/").append(fun);
                    }
                }

                _url = sb.toString();
            }
        }
        return this;
    }

    /**
     * 执行完成呼叫
     */
    public Nami call(Map<String, String> headers, Map args) {
        return call(headers, args, null);
    }

    public Nami call(Map<String, String> headers, Map args, Object body) {
        try {
            if (headers == null) {
                headers = new HashMap<>();
            }

            if (args == null) {
                args = new HashMap<>();
            }

            for (Filter filter : _config.getFilters()) {
                filter.filter(_config, _action, _url, headers, args);
            }

            NamiChannel channel = _config.getChannel();

            if (channel == null) {
                //通过 scheme 获取通道
                int idx = _url.indexOf("://");
                if (idx > 0) {
                    String scheme = _url.substring(0, idx);
                    channel = NamiManager.getChannel(scheme);
                }
            }

            if (channel == null) {
                throw new NamiException("There are no channels available");
            }

            //执行通道过滤器
            channel.filter(_config, _action, _url, headers, args);

            if (body == null) {
                body = args;
            }

            if (_config.getDebug()) {
                PrintUtil.info("Nami", "call: " + _url);
            }

            _result = channel.call(_config, _method, _action, _url, headers, args, body);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }

        return this;
    }

    private Result _result;

    public Result result() {
        return _result;
    }

    /**
     * 获取结果（以string形式）
     */
    public String getString() {
        if(_result == null){
            return null;
        }else {
            return _result.bodyAsString();
        }
    }

    /**
     * 获取结果（返序列化为object）
     */
    public <T> T getObject(Type returnType) {
        if(_result == null){
            return null;
        }

        if (Void.TYPE.equals(returnType)) {
            return null;
        } else {
            Decoder decoder = _config.getDecoder();

            if (decoder == null) {
                decoder = NamiManager.getDecoder(Constants.CONTENT_TYPE_JSON);
            }

            return decoder.decode(_result, returnType);
        }
    }


    //////////////////////////////////
    //
    // 下面为动态代理部分
    //
    //////////////////////////////////


    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final NamiConfig _config;

        protected Builder() {
            _config = new NamiConfig();
        }

        protected Builder(NamiConfig config) {
            _config = config;
        }

        /**
         * 设置负载代理
         */
        public Builder upstream(Supplier<String> upstream) {
            _config.setUpstream(upstream);
            return this;
        }

        /**
         * 设置序列化器
         */
        public Builder encoder(Encoder encoder) {
            _config.setEncoder(encoder);
            return this;
        }

        /**
         * 设置反序列器
         */
        public Builder decoder(Decoder decoder) {
            _config.setDecoder(decoder);
            return this;
        }

        /**
         * 设置头
         * */
        public Builder headerSet(String name,String val) {
            _config.setHeader(name, val);
            return this;
        }

        /**
         * 设置反序列器
         */
        public Builder channel(NamiChannel channel) {
            _config.setChannel(channel);
            return this;
        }

        /**
         * 添加过滤器
         */
        public Builder filterAdd(Filter filter) {
            _config.filterAdd(filter);
            return this;
        }

        public Builder debug(boolean debug){
            _config.setDebug(debug);
            return this;
        }


        /**
         * 设置服务端
         */
        public Builder url(String url) {
            _config.setUrl(url);
            return this;
        }

        public Builder name(String name) {
            _config.setName(name);
            return this;
        }

        public Builder path(String path) {
            _config.setPath(path);
            return this;
        }

        public Builder group(String group) {
            _config.setGroup(group);
            return this;
        }

        public Nami build() {
            return new Nami(_config);
        }

        /**
         * 创建接口代理
         */
        public <T> T create(Class<?> clz) {
            NamiClient client = clz.getAnnotation(NamiClient.class);

            return (T) create(clz, client);
        }

        /**
         * 创建接口代理
         */
        public Object create(Class<?> clz, NamiClient client) {
            if (clz == null) {
                return null;
            }

            if (clz.isInterface() == false) {
                throw new NamiException("NamiClient only support interfaces");
            }

            NamiHandler handler = new NamiHandler(clz, _config, client);

            return Proxy.newProxyInstance(
                    clz.getClassLoader(),
                    new Class[]{clz},
                    handler);
        }
    }
}
