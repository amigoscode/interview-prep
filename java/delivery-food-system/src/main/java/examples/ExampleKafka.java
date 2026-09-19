package examples;

import examples.event.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ExampleKafka {

    private static final String TOPIC = "kafka-topic";

    @Autowired
    private KafkaTemplate<String, Event> kafkaTemplate;

    public void produceMessage() {
        kafkaTemplate.send(TOPIC, new Event("message", Instant.now()));
    }

    @KafkaListener(topics = TOPIC)
    public void consumeMessage(Event content) {
        System.out.println("Received from Kafka: " + content);
    }

}


