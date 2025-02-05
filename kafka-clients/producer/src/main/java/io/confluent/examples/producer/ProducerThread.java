package io.confluent.examples.producer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import io.confluent.examples.producer.ProducerGroup;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;

public class ProducerThread implements Runnable {

    private String topicName;
    private String key;
    private String datatoSend;
    private Properties properties;
    private Long threadno;
    private int numMessages;
    private static Map<String, Long> topicCount = new HashMap<String, Long>();
    private static Producer<String, String> producer = null;

    static Object lock = new Object();
    java.util.Date date;

    ProducerThread(String topicName, String key, String datatoSend, int numMessages, Properties properties) {
        this.topicName = topicName;
        this.key = key;
        this.datatoSend = datatoSend;
        this.properties = properties;
        this.numMessages = numMessages;
        date = new java.util.Date();

        synchronized (lock) {
            if (topicCount.get(topicName) == null) {
                topicCount.put(topicName, (long) 0);
            }
        }
    }

    public void run() {
        producer = new KafkaProducer<String, String>(properties);
        sendRecords(topicName, key, datatoSend, numMessages, producer);
    }

    public static long getTopicCount(String topic) {
        synchronized (lock) {
            return topicCount.get(topic);
        }
    }

    public void sendRecords(String topicName, String key, String dataJSONString, int numMessages,
            Producer<String, String> producer) {

        boolean returnStatus = true;
        try {

            Long startThreadTime = System.currentTimeMillis();

            for (int i = 0; i < numMessages; i++) {
                ProducerRecord<String, String> data = new ProducerRecord<String, String>(
                        topicName, key, dataJSONString);
                producer.send(data);
                synchronized (lock) {
                    topicCount.put(topicName, topicCount.get(topicName) + 1);
                }
            }
            long produceThreadTime = System.currentTimeMillis();
            long elapsedTime = produceThreadTime - startThreadTime;
            ProducerGroup.totalTimeProducing += elapsedTime;
        } catch (Exception e) {
            e.printStackTrace();
            returnStatus = false;
        } finally {
            producer.flush();
        }
    }

}
