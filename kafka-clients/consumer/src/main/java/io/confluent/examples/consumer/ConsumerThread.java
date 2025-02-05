package io.confluent.examples.consumer;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.consumer.ConsumerTimeoutException;
import kafka.message.MessageAndMetadata;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import io.confluent.examples.consumer.ConsumerGroup;
import kafka.javaapi.consumer.ConsumerConnector;
import io.confluent.examples.consumer.ConsumerGroup;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class ConsumerThread implements Runnable {
    private KafkaStream topicStream;
    private int threadNumber;
    private int groupid;
    private CyclicBarrier barrier;
    java.util.Date date;
    private volatile int counter = 0;
    static Object lock = new Object();
    private long elapsedTime;
    private boolean wasWorkDone = false;
    private long startTime;

    public ConsumerThread(KafkaStream stream, int threadNumber, int groupid,
            CyclicBarrier barrier) {
        this.threadNumber = threadNumber;
        topicStream = stream;
        this.groupid = groupid;
        date = new java.util.Date();
        this.barrier = barrier;
    }

    public void run() {
        String topic = null;
        while (true) {
            try {
                ConsumerIterator<Object, Object> it = topicStream.iterator();
                /*
                 * hasNext() in Consumer iterator is a blocking call, so
                 * we need to use the consumer timeout parameter, to exit
                 * this call. Setting this to one second, as we are more interested
                 * in measuring no of messages per second.
                 */
                while (it.hasNext()) {
                    startTime = System.currentTimeMillis();
                    MessageAndMetadata<Object, Object> record = it.next();
                    synchronized (lock) {
                        wasWorkDone = true;
                        topic = record.topic();
                        int partition = record.partition();
                        long offset = record.offset();
                        Object key = record.key();
                        String message = (String) record.message();
                        counter++;
                    }
                }
            } catch (ConsumerTimeoutException cte) {
                long endTime = System.currentTimeMillis();
                elapsedTime = endTime - startTime;

                synchronized (lock) {
                    if (wasWorkDone && (counter > 0)) {
                        ConsumerGroup.totalMessagesConsumed += counter;
                        wasWorkDone = false;

                        System.out.println(new Timestamp(date.getTime()) +
                                " :] Thread :" + threadNumber +
                                " Took " + ((double) elapsedTime) / 1000 + "  seconds" +
                                "to consume" + counter + " messages on topic " + topic);
                        counter = 0;
                    }
                }

                try {
                    barrier.await();
                    synchronized (lock) {
                        ConsumerGroup.startTime = System.currentTimeMillis();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
            }

        }

    }
}
