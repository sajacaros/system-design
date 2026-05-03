package kr.study.systemdesign.uniqueid.service;

import kr.study.systemdesign.uniqueid.config.UniqueIdProperty;
import org.springframework.stereotype.Service;

public interface SnowflakeIdGenerator {
    public long nextId();

    @Service
    class Default implements SnowflakeIdGenerator {
        // 시작 타임스탬프 (2024-01-01)
        private final long EPOCH = 1704067200000L;

        // 비트 할당
        private final long DATACENTER_ID_BITS = 5L;
        private final long WORKER_ID_BITS = 5L;
        private final long SEQUENCE_BITS = 12L;

        // 최대값 계산
        private final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
        private final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

        // 비트 이동 계산
        private final long WORKER_ID_SHIFT = SEQUENCE_BITS;
        private final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
        private final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

        private final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

        private long workerId;
        private long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        /**
         +---------------------------------------------------------------------+
         |                           64비트 Snowflake ID                        |
         +---------------------------------------------------------------------+
         |                 |          |           |                            |
         | 부호 비트 (1비트) | 타임스탬프 |   서버     |         시퀀스 번호          |
         |     (미사용)     | (41비트)  |   ID      |         (12비트)            |
         |                 |          |  (10비트)  |                            |
         +---------------------------------------------------------------------+
         |       0         |  41비트  | 10비트      |         12비트             |
         +---------------------------------------------------------------------+
         */
        public Default(UniqueIdProperty uniqueIdProperty) {
            if (uniqueIdProperty.getWorkerId() > MAX_WORKER_ID || uniqueIdProperty.getWorkerId() < 0) {
                throw new IllegalArgumentException(
                        String.format("Worker ID can't be greater than %d or less than 0", MAX_WORKER_ID));
            }

            if (uniqueIdProperty.getDatacenterId() > MAX_DATACENTER_ID || uniqueIdProperty.getDatacenterId() < 0) {
                throw new IllegalArgumentException(
                        String.format("Datacenter ID can't be greater than %d or less than 0", MAX_DATACENTER_ID));
            }

            this.workerId = uniqueIdProperty.getWorkerId();
            this.datacenterId = uniqueIdProperty.getDatacenterId();
        }

        @Override
        public synchronized long nextId() {
            long timestamp = timeGen();

            // 시간이 역행하는 경우 예외 발생
            if (timestamp < lastTimestamp) {
                throw new RuntimeException(
                        String.format("Clock moved backwards. Refusing to generate id for %d milliseconds",
                                lastTimestamp - timestamp));
            }

            // 같은 밀리초 내에서 sequence 증가
            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & SEQUENCE_MASK;
                if (sequence == 0) {
                    // 현재 밀리초에서 시퀀스가 모두 사용된 경우 다음 밀리초까지 대기
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;

            // ID 생성 (64비트)
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) |
                    (datacenterId << DATACENTER_ID_SHIFT) |
                    (workerId << WORKER_ID_SHIFT) |
                    sequence;
        }

        private long tilNextMillis(long lastTimestamp) {
            long timestamp = timeGen();
            while (timestamp <= lastTimestamp) {
                timestamp = timeGen();
            }
            return timestamp;
        }

        private long timeGen() {
            return System.currentTimeMillis();
        }
    }
}
