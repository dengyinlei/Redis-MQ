package com.github.mihone.redismq.thread;

import com.github.mihone.redismq.mq.ConsumeHandler;
import com.github.mihone.redismq.redis.RedisUtils;
import redis.clients.jedis.Jedis;

import java.util.Objects;

public class TaskThread implements Runnable {

    private String queueName;
    private int threadNo;

    public TaskThread(String queueName, int threadNo) {
        this.queueName = queueName;
        this.threadNo = threadNo;
    }
    @Override
    public void run() {
        try(Jedis jedis = RedisUtils.getJedis()){
            jedis.subscribe(new ConsumeHandler(), queueName);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskThread that = (TaskThread) o;
        return threadNo == that.threadNo &&
                queueName.equals(that.queueName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queueName, threadNo);
    }
}
