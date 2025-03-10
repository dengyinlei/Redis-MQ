package com.github.mihone.redismq.mq;

import com.github.mihone.redismq.cache.Cache;
import com.github.mihone.redismq.config.BasicConfig;
import com.github.mihone.redismq.exception.BeanAcquiredException;
import com.github.mihone.redismq.json.JsonUtils;
import com.github.mihone.redismq.log.Log;
import com.github.mihone.redismq.redis.RedisUtils;
import com.github.mihone.redismq.reflect.ClassUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class MethodInvocationHandler {

    private static final Log log = Log.getLogger(MethodInvocationHandler.class);

    static void handler(Method method, String channel, String messageId) {
        try(Jedis jedis = RedisUtils.getJedis()){
            Class<?> clazz = method.getDeclaringClass();
            Object apply = RedisMQ.getBeanProvider().apply(clazz);
            if (clazz.isInstance(apply)) {
                String result = jedis.set(channel + ":" + messageId, BasicConfig.MESSAGE_LOCK_EXPIRE_TIME + "", SetParams.setParams().nx().px(BasicConfig.MESSAGE_LOCK_EXPIRE_TIME));
                if (result != null) {
                    doHandler(jedis, method, channel, apply, true);
                }
            } else {
                log.warn("can not get the bean instance by the given bean provider.Class:{},Cause:{}", clazz, new BeanAcquiredException().getStackMessage());
            }
        }
    }

    @Deprecated
    static void handler(String channel) {
        Method method = Cache.getFromMethodCache(channel);
        Class<?> clazz = method.getDeclaringClass();
        Object apply = RedisMQ.getBeanProvider().apply(clazz);
        if (clazz.isInstance(apply)) {
            try(Jedis jedis = RedisUtils.getJedis()){
                while (jedis.lrange((channel + BasicConfig.DEAD_QUEUE_SUFFIX).getBytes(), 0, -1).size() > 0) {
                    doHandler(jedis, method, channel, apply, false);
                }
            }
        } else {
            log.warn("can not get the bean instance by the given bean provider.Class:{},Cause:{}", clazz, new BeanAcquiredException().getStackMessage());
        }
    }

    private static void doHandler(Jedis jedis, Method method, String channel, Object apply, boolean present) {
        byte[] msgBytes;
        if (present) {
            msgBytes = jedis.rpoplpush(channel.getBytes(), (channel + BasicConfig.BACK_QUEUE_SUFFIX).getBytes());
        } else {
            msgBytes = jedis.rpoplpush((channel + BasicConfig.DEAD_QUEUE_SUFFIX).getBytes(), (channel + BasicConfig.BACK_QUEUE_SUFFIX).getBytes());
        }
        if(msgBytes==null){
            return ;
        }
        Class<?> clazz = method.getDeclaringClass();
        Message message = JsonUtils.convertObjectFromBytes(msgBytes, Message.class);
        if (message == null) {
            log.debug("null when read byte[] to ObjectNode.byte[]:{}", msgBytes);
            return;
        }
        String className = message.getClassName();
        Parameter[] parameters = method.getParameters();
        Object[] args = ClassUtils.getDefaultArgs(parameters);
        Class<?>[] argsClasses = ClassUtils.getArgClasses(parameters);
        try {
            Class<?> msgClass = Class.forName(className);
            Parameter param = parameters[0];
            Object body = JsonUtils.convertObjectFromBytes(JsonUtils.convertToBytes(message.getBody()),Class.forName(className));
            if (body == null) {
                log.debug("null when convert body.body:{},Class:{}", message.getBody(), msgClass);
                return;
            }
            if (param.getType().equals(Message.class)) {
                args[0] = message;
            } else if (param.getType().equals(clazz)) {
                args[0] = body;
            }
            Method method0 = getMethod0(clazz, method.getName(), argsClasses);
            if (method0 != null) {
                int result = execute(method0, apply, args);
                if (result == 0) {
                    log.debug("Method invoke failed.method:{},insatnce:{},args:{}", method0, apply.toString(), Arrays.stream(args).collect(Collectors.toList()));
                    return;
                }
            } else {
                log.debug("Get Method failed.Class:{},methodName:{},argsClasses:{}", clazz, method.getName(), Arrays.stream(argsClasses).collect(Collectors.toList()));
                return;
            }

        } catch (ClassNotFoundException e) {
            log.error("can not found the class.Class:{},Cause:{}", className, e);
            Parameter param = parameters[0];
            if (param.getType().equals(Message.class)) {
                message.setBody(JsonUtils.convertToBytes(message.getBody()));
                //only if ClassNotFoundException happend ,body will be transfered to bytes.
                args[0] = message;
            }
            Method method0 = getMethod0(clazz, method.getName(), argsClasses);
            if (method0 != null) {
                log.info("method0开始执行....");
                int result = execute(method0, apply, args);
                log.info("执行结果：" + result);
                if (result == 0) {
                    log.debug("Method invoke failed.method:{},insatnce:{},args:{}", method0, apply.toString(), Arrays.stream(args).collect(Collectors.toList()));
                    return;
                }
            } else {
                log.debug("Get Method failed.Class:{},methodName:{},argsClasses:{}", clazz, method.getName(), Arrays.stream(argsClasses).collect(Collectors.toList()));
                return;
            }
        }
        jedis.lrem((channel + BasicConfig.BACK_QUEUE_SUFFIX).getBytes(), 0, msgBytes);
    }

    private static int execute(Method method0, Object apply, Object[] args) {
        try {
            method0.invoke(apply, args);
            return 1;
        } catch (IllegalAccessException e) {
            log.error("the target method is not a accessible method,please check the method.Method:{},Cause: {}", method0, e);
            return 0;
        } catch (InvocationTargetException e) {
            log.error("an exception happened when target method invoked,please check the method.Method:{},insatnce:{},args:{},Cause:{} ", method0, apply, args, e);
            return 0;
        }
    }

    private static Method getMethod0(Class<?> clazz, String methodName, Class<?>[] argsClasses) {
        try {
            return clazz.getMethod(methodName, argsClasses);
        } catch (NoSuchMethodException e) {
            log.error("there is no such method please check it Method:{}.Cause:{} ", methodName, e);
            return null;
        }
    }


    private MethodInvocationHandler() {
    }
}
