package kr.study.bloomfilter.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BloomFilterDemoServiceTest {

    @Test
    void checkingSeedUrlReportsProbableDuplicate() {
        BloomFilterDemoService service = new BloomFilterDemoService();

        BloomFilterDemoService.DashboardState state = service.checkUrl("https://news.example.com/world");

        assertThat(state.latest().probablyPresent()).isTrue();
        assertThat(state.latest().actuallyVisited()).isTrue();
    }

    @Test
    void addingNewUrlIncreasesVisitedUrlCount() {
        BloomFilterDemoService service = new BloomFilterDemoService();
        int before = service.dashboard().visitedUrls().size();

        BloomFilterDemoService.DashboardState state = service.addUrl("https://new.example.com/page");

        assertThat(state.visitedUrls()).hasSize(before + 1);
        assertThat(state.latest().changedAnyBit()).isTrue();
    }

    @Test
    void configuringFilterReloadsSeedUrls() {
        BloomFilterDemoService service = new BloomFilterDemoService();

        BloomFilterDemoService.DashboardState state = service.configure(32, 2);

        assertThat(state.filter().bitSize()).isEqualTo(32);
        assertThat(state.filter().hashFunctionCount()).isEqualTo(2);
        assertThat(state.visitedUrls()).contains("https://news.example.com/world");
    }
}
