# Event-Driven Architecture Specification

## Objective
Design and implement event-driven architecture for asynchronous transaction processing, replacing synchronous on-demand PnL calculations with near-realtime position tracking.

## Current State

### Issues
- PnL calculated on-demand (slow)
- Synchronous processing blocks requests
- Heavy computation during API calls
- No audit trail of changes
- Cannot scale horizontally easily
- No real-time position updates

## Target State

- Event-driven architecture with message queue
- Asynchronous transaction ingestion
- Pre-calculated positions stored in database
- Fast query response times (<50ms)
- Horizontal scalability
- Event sourcing for audit trail
- Real-time position updates via WebSocket

## Architecture Overview

```
Client → REST API → Command Service → Message Queue (Kafka/RabbitMQ)
                                             ↓
                                    Event Processor
                                             ↓
                                    Position Store (DB)
                                             ↓
                                    Query Service → Client
                                             ↓
                                    WebSocket (real-time updates)
```

## Implementation Plan (Design Phase)

### Step 1: Technology Selection

**Recommended: Apache Kafka**
- High throughput
- Event replay capability
- Distributed by design
- Strong ordering guarantees
- Industry standard

**Alternative: RabbitMQ**
- Simpler setup
- Lower operational overhead
- Sufficient for moderate scale

### Step 2: Event Design

**File: `src/main/java/com/companyx/equity/events/TransactionEvent.java`**

```java
package com.companyx.equity.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private String eventId;          // Unique event ID
    private String eventType;        // TRANSACTION_CREATED, TRANSACTION_UPDATED, etc.
    private Instant eventTime;       // When event occurred
    private String aggregateId;      // Transaction ID
    private Long version;            // Event version for ordering
    
    // Transaction data
    private String userId;
    private String transactionType;  // BUY, SELL, DEPOSIT, WITHDRAWAL
    private String symbol;
    private BigInteger quantity;
    private BigDecimal value;
    private Instant transactionTime;
    
    // Metadata
    private String correlationId;    // For tracing
    private String causationId;      // What caused this event
}
```

**File: `src/main/java/com/companyx/equity/events/PositionEvent.java`**

```java
package com.companyx.equity.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionEvent {
    private String eventId;
    private String eventType;        // POSITION_UPDATED, POSITION_CLOSED
    private Instant eventTime;
    private String aggregateId;      // Position ID (userId-symbol)
    
    // Position data
    private String userId;
    private String symbol;
    private BigInteger quantity;
    private BigDecimal value;
    private BigDecimal realizedPnL;
    private BigDecimal unrealizedPnL;
    private BigDecimal currentPrice;
    
    // Metadata
    private String correlationId;
    private String causedByTransactionId;
}
```

### Step 3: Kafka Configuration

**File: `pom.xml`**

```xml
<dependencies>
    <!-- Spring Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    
    <!-- Kafka Streams -->
    <dependency>
        <groupId>org.apache.kafka</groupId>
        <artifactId>kafka-streams</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**File: `src/main/resources/application.properties`**

```properties
# Kafka Configuration
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
spring.kafka.consumer.group-id=equity-pnl-consumer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.companyx.equity.events

spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.acks=all
spring.kafka.producer.retries=3

# Topics
kafka.topics.transactions=equity.transactions
kafka.topics.positions=equity.positions
kafka.topics.market-data=equity.market-data
```

### Step 4: Command Service (Write Side)

**File: `src/main/java/com/companyx/equity/service/TransactionCommandService.java`**

```java
package com.companyx.equity.service;

import com.companyx.equity.dto.CreateTransactionRequest;
import com.companyx.equity.events.TransactionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionCommandService {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;

    @Value("${kafka.topics.transactions}")
    private String transactionTopic;

    public CompletableFuture<SendResult<String, TransactionEvent>> createTransaction(
            String userId,
            CreateTransactionRequest request
    ) {
        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        TransactionEvent event = TransactionEvent.builder()
                .eventId(eventId)
                .eventType("TRANSACTION_CREATED")
                .eventTime(Instant.now())
                .aggregateId(eventId) // Will be replaced with DB ID after persistence
                .version(1L)
                .userId(userId)
                .transactionType(request.getTransactionType())
                .symbol(request.getSymbol())
                .quantity(request.getQuantity())
                .value(request.getValue())
                .transactionTime(request.getTransactionTime())
                .correlationId(correlationId)
                .build();

        log.info("Publishing transaction event: {} for user: {}", eventId, userId);

        return kafkaTemplate.send(transactionTopic, userId, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Transaction event published successfully: {}", eventId);
                    } else {
                        log.error("Failed to publish transaction event: {}", eventId, ex);
                    }
                });
    }
}
```

### Step 5: Event Processor (Position Calculator)

**File: `src/main/java/com/companyx/equity/processor/TransactionEventProcessor.java`**

```java
package com.companyx.equity.processor;

import com.companyx.equity.events.PositionEvent;
import com.companyx.equity.events.TransactionEvent;
import com.companyx.equity.model.Position;
import com.companyx.equity.repository.PositionRepository;
import com.companyx.equity.service.PositionCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventProcessor {

    private final PositionCalculationService positionCalculationService;
    private final PositionRepository positionRepository;
    private final KafkaTemplate<String, PositionEvent> kafkaTemplate;

    @Value("${kafka.topics.positions}")
    private String positionTopic;

    @KafkaListener(
            topics = "${kafka.topics.transactions}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void processTransaction(
            @Payload TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment
    ) {
        try {
            log.info("Processing transaction event: {} for user: {}", 
                    event.getEventId(), event.getUserId());

            // Calculate new position
            Position updatedPosition = positionCalculationService.calculatePosition(
                    event.getUserId(),
                    event.getSymbol(),
                    event
            );

            // Persist position
            positionRepository.save(updatedPosition);

            // Publish position update event
            PositionEvent positionEvent = PositionEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("POSITION_UPDATED")
                    .eventTime(Instant.now())
                    .aggregateId(event.getUserId() + "-" + event.getSymbol())
                    .userId(event.getUserId())
                    .symbol(event.getSymbol())
                    .quantity(updatedPosition.getQuantity())
                    .value(updatedPosition.getValue())
                    .realizedPnL(updatedPosition.getRealized())
                    .unrealizedPnL(updatedPosition.getUnrealized())
                    .currentPrice(updatedPosition.getPrice())
                    .correlationId(event.getCorrelationId())
                    .causedByTransactionId(event.getEventId())
                    .build();

            kafkaTemplate.send(positionTopic, event.getUserId(), positionEvent);

            // Acknowledge message
            acknowledgment.acknowledge();

            log.info("Transaction event processed successfully: {}", event.getEventId());

        } catch (Exception e) {
            log.error("Error processing transaction event: {}", event.getEventId(), e);
            // Don't acknowledge - message will be reprocessed
            throw new RuntimeException("Event processing failed", e);
        }
    }
}
```

### Step 6: Query Service (Read Side)

**File: `src/main/java/com/companyx/equity/service/PositionQueryService.java`**

```java
package com.companyx.equity.service;

import com.companyx.equity.model.Position;
import com.companyx.equity.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionQueryService {

    private final PositionRepository positionRepository;

    /**
     * Fast position lookup - data is pre-calculated
     */
    @Cacheable(value = "user-positions", key = "#userId")
    public Map<String, Position> getCurrentPositions(String userId) {
        log.debug("Querying current positions for user: {}", userId);
        
        List<Position> positions = positionRepository.findByUserId(userId);
        
        return positions.stream()
                .collect(Collectors.toMap(Position::getSymbol, p -> p));
    }

    /**
     * Get positions as of a specific date
     */
    public Map<String, Position> getPositionsAsOf(String userId, LocalDate date) {
        log.debug("Querying positions for user: {} as of {}", userId, date);
        
        List<Position> positions = positionRepository.findByUserIdAndDateBefore(userId, date);
        
        return positions.stream()
                .collect(Collectors.toMap(Position::getSymbol, p -> p));
    }

    /**
     * Get position for specific symbol
     */
    public Position getPosition(String userId, String symbol) {
        log.debug("Querying position for user: {} symbol: {}", userId, symbol);
        
        return positionRepository.findByUserIdAndSymbol(userId, symbol)
                .orElse(null);
    }
}
```

### Step 7: WebSocket Real-Time Updates

**File: `src/main/java/com/companyx/equity/websocket/PositionWebSocketHandler.java`**

```java
package com.companyx.equity.websocket;

import com.companyx.equity.events.PositionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class PositionWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        sessions.put(userId, session);
        log.info("WebSocket connection established for user: {}", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = extractUserId(session);
        sessions.remove(userId);
        log.info("WebSocket connection closed for user: {}", userId);
    }

    @KafkaListener(
            topics = "${kafka.topics.positions}",
            groupId = "websocket-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePositionUpdate(PositionEvent event) {
        String userId = event.getUserId();
        WebSocketSession session = sessions.get(userId);

        if (session != null && session.isOpen()) {
            try {
                String message = objectMapper.writeValueAsString(event);
                session.sendMessage(new TextMessage(message));
                log.debug("Sent position update to user: {}", userId);
            } catch (IOException e) {
                log.error("Error sending WebSocket message to user: {}", userId, e);
            }
        }
    }

    private String extractUserId(WebSocketSession session) {
        // Extract from session attributes or query parameters
        return (String) session.getAttributes().get("userId");
    }
}
```

### Step 8: Position Repository (Read Model)

**File: `src/main/java/com/companyx/equity/repository/PositionRepository.java`**

```java
package com.companyx.equity.repository;

import com.companyx.equity.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    
    List<Position> findByUserId(String userId);
    
    Optional<Position> findByUserIdAndSymbol(String userId, String symbol);
    
    @Query("SELECT p FROM Position p WHERE p.userId = :userId AND p.timestamp < :date")
    List<Position> findByUserIdAndDateBefore(String userId, LocalDate date);
    
    @Query("SELECT p FROM Position p WHERE p.userId = :userId AND p.symbol = :symbol ORDER BY p.timestamp DESC")
    List<Position> findPositionHistory(String userId, String symbol);
}
```

### Step 9: Docker Compose with Kafka

**File: `docker-compose.kafka.yml`**

```yaml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
    networks:
      - equity-network

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    networks:
      - equity-network

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    depends_on:
      - kafka
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    networks:
      - equity-network

networks:
  equity-network:
    driver: bridge
```

### Step 10: Migration Strategy

**Phase 1: Dual Write**
- Write to DB synchronously (existing)
- Write to Kafka asynchronously (new)
- Queries still use DB
- Build confidence in event processing

**Phase 2: Validate**
- Compare event-sourced positions with calculated positions
- Monitor for discrepancies
- Fix any issues

**Phase 3: Dual Read**
- Read from both old and new systems
- Log differences
- Gradually shift traffic

**Phase 4: Complete Migration**
- All writes go to Kafka
- All reads from position store
- Decommission old synchronous calculation

## Benefits

### Performance
- Query response: 2000ms → <50ms (40x faster)
- No blocking during calculations
- Horizontal scalability

### Features
- Real-time position updates via WebSocket
- Complete audit trail (event log)
- Event replay for debugging
- Time-travel queries (positions as of date)
- Analytics on event stream

### Scalability
- Can add more processors
- Independent scaling of read/write
- Queue handles traffic spikes

## Challenges

### Complexity
- More moving parts
- Eventual consistency
- Event ordering
- Error handling

### Operations
- Kafka cluster management
- Monitoring required
- Debugging more complex

### Data Consistency
- Need idempotency
- Duplicate event handling
- Out-of-order events

## Acceptance Criteria

- [ ] Event schema defined
- [ ] Kafka infrastructure deployed
- [ ] Command service implemented
- [ ] Event processor functional
- [ ] Query service providing fast reads
- [ ] WebSocket real-time updates working
- [ ] Migration strategy documented
- [ ] Performance benchmarks met
- [ ] Monitoring and alerting in place
- [ ] Rollback plan tested

## Performance Benchmarks

### Before (Synchronous)
- PnL query: ~2000ms
- Cannot handle concurrent users well
- Scales vertically only

### After (Event-Driven)
- Position query: <50ms (40x faster)
- Handles 10,000+ concurrent users
- Scales horizontally
- Real-time updates

## Dependencies

- All previous phases complete
- Kafka expertise in team
- Operational readiness for distributed systems

## Estimated Effort

- Event design and architecture: 3 days
- Kafka setup and configuration: 2 days
- Command service implementation: 3 days
- Event processor implementation: 5 days
- Query service refactoring: 2 days
- WebSocket implementation: 2 days
- Testing and validation: 5 days
- Documentation: 2 days
- **Total: 24 days (5 weeks)**

## References

- [Event Sourcing](https://martinfowler.com/eaaDev/EventSourcing.html)
- [CQRS Pattern](https://martinfowler.com/bliki/CQRS.html)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka](https://spring.io/projects/spring-kafka)
