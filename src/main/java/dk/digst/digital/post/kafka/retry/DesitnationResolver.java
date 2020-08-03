package dk.digst.digital.post.kafka.retry;

import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

public interface DesitnationResolver
    extends BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> {

}
