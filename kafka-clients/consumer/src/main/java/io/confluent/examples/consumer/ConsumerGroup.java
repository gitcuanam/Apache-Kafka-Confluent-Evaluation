package io.confluent.examples.consumer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kafka.serializer.StringDecoder;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.utils.VerifiableProperties;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;

public class ConsumerGroup {
    private final ConsumerConnector consumer;
    private final String topic;
    private ExecutorService executor;
    private String zookeeper;
    private String bootStrapServer;
    private String groupId;
    private String url;
    private int DEFAULT_CONSUMER_TIMEOUT = 1000;
    private int DEFAULT_COMMIT_INTERVAL = 100;
    public static long totalTimeConsuming = 0;
    public static long totalMessagesConsumed = 0;
    public static boolean workDone = false;
    int consumeGroup;
    public static int barriercount = 0;
    public static double totalTime;
    static Object lock = new Object();
    public static long startTime;
    public static long endTime;

    public ConsumerGroup(String zookeeper, String groupId, String topic,
            int consumeGroup) {
        consumer = kafka.consumer.Consumer.createJavaConsumerConnector(
                new ConsumerConfig(createConsumerConfig(zookeeper, groupId)));
        this.topic = topic;
        this.zookeeper = zookeeper;
        this.groupId = groupId;
        this.consumeGroup = consumeGroup;
    }

    private Properties createConsumerConfig(String zookeeper, String groupId) {
        Properties props = new Properties();
        props.put("zookeeper.connect", zookeeper);
        props.put("consumer.timeout.ms", "DEFAULT_CONSUMER_TIMEOUT");
        props.put("group.id", groupId);
        props.put("auto.commit.interval.ms", "DEFAULT_COMMIT_INTERVAL");
        props.put("auto.offset.reset", "smallest");
        return props;
    }

    public void run(int numThreads, CyclicBarrier cb) {
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, numThreads);

        Properties props = createConsumerConfig(zookeeper, groupId);
        VerifiableProperties vProps = new VerifiableProperties(props);
        StringDecoder keyDecoder = new StringDecoder(vProps);
        StringDecoder valueDecoder = new StringDecoder(vProps);

        Map<String, List<KafkaStream<String, String>>> consumerMap = consumer.createMessageStreams(topicCountMap,
                keyDecoder, valueDecoder);
        List<KafkaStream<String, String>> streams = consumerMap.get(topic);

        long totalNoOfMessagesPerTopic = streams.size();
        // Launch all the threads
        executor = Executors.newFixedThreadPool(numThreads);
        // Create ConsumerThread objects and bind them to threads
        int threadNumber = 0;
        for (final KafkaStream stream : streams) {
            executor.submit(new ConsumerThread(stream, threadNumber, consumeGroup, cb));
            threadNumber++;
        }
    }

    public static void main(String[] args) {

        String zooKeeper = "localhost:2181";
        String groupId = "group";
        String topics = args[0];
        String[] topicList = topics.split(",");
        String bootStrapServer = "http://localhost:9092";

        // No of threads per topic
        int noOfthreads = 10;
        List<ConsumerGroup> cg = new ArrayList<ConsumerGroup>();
        ConsumerGroup cga = null;

        // Launching consumer group for each topic -- with 10
        // consumer threads in a group

        startTime = System.currentTimeMillis();
        CyclicBarrier cb = new CyclicBarrier(topics.length * noOfthreads, new Runnable() {
            @Override
            public void run() {
                // This task will be executed once
                // all thread reaches barrier
                if (barriercount == Integer.MAX_VALUE) {
                    return;
                }
                barriercount++;
                endTime = System.currentTimeMillis();
                totalTime = endTime - startTime;
                double totalTimeInSecs = ((double) totalTime) / 1000;

                if (totalMessagesConsumed > 0) {
                    System.out.println("Total Number of Messages" + totalMessagesConsumed +
                            "Total Time spent" + totalTime);
                    System.out.println(
                            " Consumption Throughput :: " + ((double) totalMessagesConsumed) / totalTimeInSecs +
                                    "Messages per second");
                }
                synchronized (lock) {
                    totalMessagesConsumed = 0;
                }
            }
        });

        for (int i = 0; i < topicList.length; i++) {
            cga = new ConsumerGroup(zooKeeper, groupId, topicList[i], i);
            cg.add(cga);
            cga.run(noOfthreads, cb);
        }

    }
}
