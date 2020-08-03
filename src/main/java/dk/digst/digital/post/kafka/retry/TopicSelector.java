package dk.digst.digital.post.kafka.retry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;
import dk.digst.digital.post.kafka.retry.KafkaRetryApplication.TopicNames;

@Component
public class TopicSelector implements DesitnationResolver {

  @Override
  public TopicPartition apply(ConsumerRecord<?, ?> t, Exception u) {
    /*
     * Default to the dead-letter topic
     */
    TopicPartition topicPartition = new TopicPartition(TopicNames.DLQ, -1);

    /*
     * Select topic based on the original target topic
     */
    switch (t.topic()) {
      case TopicNames.INPUT:
        topicPartition = new TopicPartition(TopicNames.RETRY_1, -1);
        break;
      case TopicNames.RETRY_1:
        topicPartition = new TopicPartition(TopicNames.RETRY_2, -1);
        break;
    }

    return topicPartition;
  }

}
