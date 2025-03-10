package com.github.mihone.redismq.redis;

import com.github.mihone.redismq.config.RedisPoolConfig;
import com.github.mihone.redismq.yaml.YmlUtils;
import com.github.mihone.redismq.config.BasicConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public final class RedisUtils {
    private RedisUtils() {
    }


    public static void returnJedis(Jedis jedis){
        if (jedis != null) {
            jedis.close();
        }
    }
    public static Jedis getJedis(){
        return RedisPool.pool.getResource();
    }

    public static String getUrl(){
        return RedisPool.CONFIG.getUrl();
    }
    public static int getPort(){
        return RedisPool.CONFIG.getPort();
    }
    public static String getPassword(){
        return RedisPool.CONFIG.getPassword();
    }
    private static final class RedisPool {
        private static final RedisPoolConfig CONFIG = new RedisPoolConfig();

        static {
            Object url = YmlUtils.getValue(BasicConfig.BASIC_PREFIX + ".url");
            if (url != null) {
                CONFIG.setUrl(url.toString());
            }
            Object port = YmlUtils.getValue(BasicConfig.BASIC_PREFIX + ".port");
            if (port != null) {
                CONFIG.setPort(Integer.parseInt(port.toString()));
            }
            Object password = YmlUtils.getValue(BasicConfig.BASIC_PREFIX + ".password");
            if (password != null) {
                CONFIG.setPassword(password.toString());
            }
            Object timeout = YmlUtils.getValue(BasicConfig.BASIC_PREFIX + ".timeout");
            if (timeout != null) {
                CONFIG.setTimeout(Integer.parseInt(timeout.toString()));
            }
            Object database = YmlUtils.getValue(BasicConfig.BASIC_PREFIX + ".database");
            if (database != null) {
                CONFIG.setDatabase(Integer.parseInt(database.toString()));
            }
            Object maxActive = YmlUtils.getValue(BasicConfig.JEDIS_POOL_PREFIX + ".max-active");
            if (maxActive != null) {
                CONFIG.setMaxTotal(Integer.parseInt(maxActive.toString()));
            }
            Object maxIdle = YmlUtils.getValue(BasicConfig.JEDIS_POOL_PREFIX + ".max-idle");
            if (maxIdle != null) {
                CONFIG.setMaxIdle(Integer.parseInt(maxIdle.toString()));
            }
            Object minIdle = YmlUtils.getValue(BasicConfig.JEDIS_POOL_PREFIX + ".min-idle");
            if (minIdle != null) {
                CONFIG.setMinIdle(Integer.parseInt(minIdle.toString()));
            }
            Object maxWait = YmlUtils.getValue(BasicConfig.JEDIS_POOL_PREFIX + ".max-wait");
            if (maxWait != null) {
                CONFIG.setMaxWaitMillis(Integer.parseInt(maxWait.toString()));
            }
        }

        private static final JedisPool pool = new JedisPool(CONFIG,CONFIG.getUrl(),CONFIG.getPort(), CONFIG.getTimeout(),CONFIG.getPassword(),CONFIG.getDatabase());

    }
}
