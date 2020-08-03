package dk.digst.digital.post.kafka.retry;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import dk.digst.digital.post.kafka.retry.KafkaRetryApplication.TopicNames;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EventListenerService {

  @KafkaListener(topics = TopicNames.INPUT, groupId = "input",
      containerFactory = "inputKafkaListenerContainerFactory")
  public void inputHandler(@Payload byte[] record,
      @Header(KafkaHeaders.DELIVERY_ATTEMPT) int delivery) {
    log.info("Recieved (delivery: {}) in INPUT: {}", delivery, record);

    throw new RuntimeException("Create fault! on input");
  }

  @KafkaListener(topics = TopicNames.RETRY_1, groupId = "retry1",
      containerFactory = "retry1KafkaListenerContainerFactory")
  public void retry1Handler(@Payload byte[] record,
      @Header(KafkaHeaders.DELIVERY_ATTEMPT) int delivery) {
    log.info("Recieved (delivery: {}) in RETRY1: {}", delivery, record);

    throw new RuntimeException("Create fault! on retry1");

  }

  @KafkaListener(topics = TopicNames.RETRY_2, groupId = "retry2",
      containerFactory = "retry2KafkaListenerContainerFactory")
  public void retry2Handler(@Payload byte[] record,
      @Header(KafkaHeaders.DELIVERY_ATTEMPT) int delivery) {
    log.info("Recieved (delivery: {}) in RETRY2: {}", delivery, record);

    throw new RuntimeException("Create fault! on retry2");

  }

}
