package org.noear.nami;

import org.noear.nami.annotation.Mapping;
import org.noear.nami.annotation.NamiClient;
import org.noear.nami.common.Constants;
import org.noear.nami.common.MethodWrap;
import org.noear.nami.common.TextUtils;
import org.noear.nami.common.UpstreamFixed;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nami - 调用处理程序
 *
 * @author noear
 * @since 1.0
 * */
public class NamiHandler implements InvocationHandler {
    private static Pattern pathKeyExpr = Pattern.compile("\\{([^\\\\}]+)\\}");

    private final NamiConfig config;

    private final Map<String, String> headers0 = new LinkedHashMap<>();
    private final Class<?> clz0;
    private final Map<String, Map> pathKeysCached = new ConcurrentHashMap<>();

    /**
     * @param config 配置
     * @param client 客户端注解
     */
    public NamiHandler(Class<?> clz, NamiConfig config, NamiClient client) {
        this.config = config;

        this.clz0 = clz;

        //1.运行配置器
        if (client != null) {
            try {
                config.setUrl(config.getUrl());

                NamiConfiguration tmp = NamiManager.getConfigurator(client.configuration());

                if (tmp != null) {
                    tmp.config(client, new Nami.Builder(config));
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            if(client.timeout() > 0) {
                config.setTimeout(client.timeout());
            }

            //>>添加接口url
            if(TextUtils.isNotEmpty(client.url())){
                config.setUrl(client.url());
            }

            //>>添加接口group
            if(TextUtils.isNotEmpty(client.group())){
                config.setGroup(client.group());
            }

            //>>添加接口name
            if(TextUtils.isNotEmpty(client.name())){
                config.setName(client.name());
            }

            //>>添加接口path
            if(TextUtils.isNotEmpty(client.path())){
                config.setPath(client.path());
            }

            //>>添加接口header
            if (client.headers().length > 0) {
                for (String h : client.headers()) {
                    String[] ss = h.split("=");
                    if (ss.length == 2) {
                        headers0.put(ss[0].trim(), ss[1].trim());
                    }
                }
            }

            //>>添加upstream
            if (client.upstream().length > 0) {
                config.setUpstream(new UpstreamFixed(Arrays.asList(client.upstream())));
            }
        }

        //2.配置初始化
        config.init();
    }


    protected MethodHandles.Lookup lookup;

    @Override
    public Object invoke(Object proxy, Method method, Object[] vals) throws Throwable {
        //优先处理附加信息（不然容易OOM）
        NamiAttachment namiAttachment = NamiAttachment.currentGet();
        if (namiAttachment != null && namiAttachment.autoRemove()) {
            NamiAttachment.currentRemove();
        }

        //检查upstream
        if (TextUtils.isEmpty(config.getUrl()) && config.getUpstream() == null) {
            throw new NamiException("NamiClient: Not found upstream: " + clz0.getName());
        }


        MethodWrap methodWrap = MethodWrap.get(method);

        //Object 函数调用
        Class caller = method.getDeclaringClass();
        if (Object.class == caller) {
            if (this.lookup == null) {
                Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, Integer.TYPE);
                constructor.setAccessible(true);
                this.lookup = constructor.newInstance(caller, MethodHandles.Lookup.PRIVATE);
            }

            return this.lookup.unreflectSpecial(method, caller).bindTo(proxy).invokeWithArguments(vals);
        }

        //构建 headers
        Map<String, String> headers = new HashMap<>(headers0);

        //构建 args
        Map<String, Object> args = new LinkedHashMap<>();
        Object body = null;
        Parameter[] names = methodWrap.getParameters();
        for (int i = 0, len = names.length; i < len; i++) {
            if (vals[i] != null) {
                args.put(names[i].getName(), vals[i]);
            }
        }

        //确定body及默认编码
        if (methodWrap.getBodyName() != null) {
            body = args.get(methodWrap.getBodyName());

            if (config.getEncoder() == null) {
                headers.putIfAbsent(Constants.HEADER_CONTENT_TYPE, methodWrap.getBodyAnno().contentType());
            }
        }

        //构建 fun
        String fun = method.getName();
        String act = null;

        //处理mapping
        Mapping mapping = methodWrap.getMappingAnno();
        if (mapping != null) {
            if (methodWrap.getAct() != null) {
                act = methodWrap.getAct();
            }

            if (methodWrap.getFun() != null) {
                fun = methodWrap.getFun();
            }

            if (methodWrap.getMappingHeaders() != null) {
                headers.putAll(methodWrap.getMappingHeaders());
            }
        }

        //处理附加信息
        if (namiAttachment != null) {
            headers.putAll(namiAttachment.headers());
        }


        //构建 url
        String url = null;
        if (TextUtils.isEmpty(config.getUrl())) {
            url = config.getUpstream().get();

            if (url == null) {
                throw new NamiException("NamiClient: Upstream not found server!");
            }

            if (url.indexOf("://") < 0) {
                url = "http://";
            }

            if (TextUtils.isNotEmpty(config.getPath())) {
                int idx = url.indexOf("/", 9);//https://a
                if (idx > 0) {
                    url = url.substring(0, idx);
                }

                if (config.getPath().endsWith("/")) {
                    fun = config.getPath() + fun;
                } else {
                    fun = config.getPath() + "/" + fun;
                }
            }

        } else {
            url = config.getUrl();
        }

        if (fun != null && fun.indexOf("{") > 0) {
            //
            //处理Path参数
            //
            Map<String, String> pathKeys = buildPathKeys(fun);

            for (Map.Entry<String, String> kv : pathKeys.entrySet()) {
                String val = (String) args.get(kv.getValue());

                if (val != null) {
                    fun = fun.replace(kv.getKey(), val);
                    args.remove(kv.getValue());
                }
            }
        }

        //确定返回类型
        Type type = method.getGenericReturnType();
        if (type == null) {
            type = method.getReturnType();
        }


        //执行调用
        return new Nami(config)
                .method(method)
                .action(act)
                .url(url, fun)
                .call(headers, args, body)
                .getObject(type);
    }

    private Map<String, String> buildPathKeys(String path) {
        Map<String, String> pathKeys = pathKeysCached.get(path);
        if (pathKeys == null) {
            synchronized (path.intern()) {
                pathKeys = pathKeysCached.get(path);
                if (pathKeys == null) {
                    pathKeys = new LinkedHashMap<>();

                    Matcher pm = pathKeyExpr.matcher(path);

                    while (pm.find()) {
                        pathKeys.put(pm.group(), pm.group(1));
                    }
                }
            }
        }

        return pathKeys;
    }
}