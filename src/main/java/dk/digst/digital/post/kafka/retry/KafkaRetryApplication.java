package dk.digst.digital.post.kafka.retry;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultAfterRollbackProcessor;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

@SpringBootApplication(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableKafka
@EnableRetry
public class KafkaRetryApplication {

  public static void main(String[] args) {
    SpringApplication.run(KafkaRetryApplication.class, args);
  }

  @Bean
  public ProducerFactory<byte[], byte[]> producerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

    DefaultKafkaProducerFactory<byte[], byte[]> factory = new DefaultKafkaProducerFactory<>(props);
    factory.setTransactionIdPrefix("txPrefix.");

    return factory;
  }

  @Bean
  public KafkaTemplate<byte[], byte[]> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  @Bean
  public ProducerFactory<byte[], byte[]> retryProducerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

    DefaultKafkaProducerFactory<byte[], byte[]> factory = new DefaultKafkaProducerFactory<>(props);
    factory.setTransactionIdPrefix("txRetryRecoverer.");

    return factory;
  }

  @Bean
  public KafkaTemplate<byte[], byte[]> retryKafkaTemplate() {
    return new KafkaTemplate<>(retryProducerFactory());
  }

  @Bean
  public ConsumerFactory<byte[], byte[]> inputConsumerFactory() {
    return new DefaultKafkaConsumerFactory<>(
        SetupHelper.createConsumerFactoryBaseProps(GroupNames.INPUT));
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<byte[], byte[]> inputKafkaListenerContainerFactory(
      KafkaTransactionManager<byte[], byte[]> kafkaTransactionManager,
      ConsumerFactory<byte[], byte[]> inputConsumerFactory,
      KafkaTemplate<byte[], byte[]> retryKafkaTemplate, TopicSelector topicSelector) {

    ConcurrentKafkaListenerContainerFactory<byte[], byte[]> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    SetupHelper.configureFactory(factory, kafkaTransactionManager, inputConsumerFactory,
        retryKafkaTemplate, topicSelector, new FixedBackOff(100L, 1L));

    return factory;
  }

  @Bean
  public ConsumerFactory<byte[], byte[]> retry1ConsumerFactory() {
    return new DefaultKafkaConsumerFactory<>(
        SetupHelper.createConsumerFactoryBaseProps(GroupNames.RETRY_1));
  }

  @Bean
  public ConsumerFactory<byte[], byte[]> retry2ConsumerFactory() {
    return new DefaultKafkaConsumerFactory<>(
        SetupHelper.createConsumerFactoryBaseProps(GroupNames.RETRY_2));
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<byte[], byte[]> retry1KafkaListenerContainerFactory(
      KafkaTransactionManager<byte[], byte[]> kafkaTransactionManager,
      ConsumerFactory<byte[], byte[]> retry1ConsumerFactory,
      KafkaTemplate<byte[], byte[]> retryKafkaTemplate, TopicSelector topicSelector) {

    ConcurrentKafkaListenerContainerFactory<byte[], byte[]> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    SetupHelper.configureFactory(factory, kafkaTransactionManager, retry1ConsumerFactory,
        retryKafkaTemplate, topicSelector, new FixedBackOff(100L, 1L));

    return factory;
  }


  @Bean
  public ConcurrentKafkaListenerContainerFactory<byte[], byte[]> retry2KafkaListenerContainerFactory(
      KafkaTransactionManager<byte[], byte[]> kafkaTransactionManager,
      ConsumerFactory<byte[], byte[]> retry2ConsumerFactory,
      KafkaTemplate<byte[], byte[]> retryKafkaTemplate, TopicSelector topicSelector) {

    ConcurrentKafkaListenerContainerFactory<byte[], byte[]> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    SetupHelper.configureFactory(factory, kafkaTransactionManager, retry2ConsumerFactory,
        retryKafkaTemplate, topicSelector, new FixedBackOff(100L, 1L));

    return factory;
  }

  public static class SetupHelper {

    public static <K, V> void setContainerProps(ContainerProperties containerProperties,
        KafkaTransactionManager<K, V> kafkaTransactionManager) {
      containerProperties.setIdleEventInterval(60000L);
      containerProperties.setTransactionManager(kafkaTransactionManager);
      containerProperties.setAckMode(AckMode.RECORD);
      containerProperties.setDeliveryAttemptHeader(true);
      containerProperties.getKafkaConsumerProperties().put("backOffInitialInterval", 500L);
      containerProperties.getKafkaConsumerProperties().put("backOffMaxInterval", 5000L);
    }

    public static <K, V> void configureFactory(
        ConcurrentKafkaListenerContainerFactory<K, V> factory,
        KafkaTransactionManager<K, V> kafkaTransactionManager,
        ConsumerFactory<K, V> consumerFactory, KafkaOperations<K, V> kafkaTemplate,
        TopicSelector topicSelector, BackOff backOff) {
      setContainerProps(factory.getContainerProperties(), kafkaTransactionManager);
      factory.setConsumerFactory(consumerFactory);
      DefaultAfterRollbackProcessor<K, V> afterRollbackProcessor =
          new DefaultAfterRollbackProcessor<>(
              new DeadLetterPublishingRecoverer(kafkaTemplate, topicSelector), backOff);
      factory.setAfterRollbackProcessor(afterRollbackProcessor);
    }

    public static Map<String, Object> createConsumerFactoryBaseProps(String groupId) {
      Map<String, Object> props = new HashMap<>();
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
      props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
      return props;
    }

  }

  public static class TopicNames {

    public static final String INPUT = "kafka_retry_store";

    public static final String OUTPUT = "kafka_retry_index";

    public static final String RETRY_1 = "kafka_retry_index_retry_1";

    public static final String RETRY_2 = "kafka_retry_index_retry_2";

    public static final String DLQ = "kafka_retry_index_dlq";
  }

  public static class GroupNames {

    public static final String INPUT = "input";

    public static final String RETRY_1 = "retry1";

    public static final String RETRY_2 = "retry2";

  }

}
