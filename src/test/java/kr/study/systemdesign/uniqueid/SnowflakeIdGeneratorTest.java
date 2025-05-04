package kr.study.systemdesign.uniqueid;

import kr.study.systemdesign.uniqueid.config.UniqueIdProperty;
import kr.study.systemdesign.uniqueid.service.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SnowflakeIdGeneratorTest {

    @Autowired
    private SnowflakeIdGenerator idGenerator;

    @Autowired
    private UniqueIdProperty property;

    @Test
    void shouldHaveCorrectProperties() {
        // 설정 값이 제대로 주입되었는지 확인
        assertNotNull(property);
        assertTrue(property.getWorkerId() >= 0, "워커 ID는 0 이상이어야 합니다");
        assertTrue(property.getDatacenterId() >= 0, "데이터센터 ID는 0 이상이어야 합니다");
    }

    @Test
    void shouldGenerateUniqueIds() {
        // 여러 ID를 생성하고 중복이 없는지 확인
        int count = 1000;
        Set<Long> ids = new HashSet<>();

        for(int i = 0; i < count; i++) {
            long id = idGenerator.nextId();
            assertFalse(ids.contains(id), "ID가 중복되었습니다: " + id);
            ids.add(id);
        }

        assertEquals(count, ids.size(), "생성된 ID 개수가 요청 개수와 일치해야 합니다");
    }

    @Test
    void shouldGenerateSequentialIds() {
        // 생성된 ID가 순차적으로 증가하는지 확인
        long id1 = idGenerator.nextId();
        long id2 = idGenerator.nextId();

        assertTrue(id2 > id1, "나중에 생성된 ID가 더 커야 합니다");
    }
}