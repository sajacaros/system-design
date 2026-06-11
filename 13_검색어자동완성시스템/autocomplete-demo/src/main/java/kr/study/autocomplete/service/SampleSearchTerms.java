package kr.study.autocomplete.service;

import kr.study.autocomplete.domain.SearchTerm;

import java.util.List;

public final class SampleSearchTerms {

    private SampleSearchTerms() {
    }

    public static List<SearchTerm> load() {
        return List.of(
                new SearchTerm("한국 여행", 15420),
                new SearchTerm("한국어 공부", 14850),
                new SearchTerm("한국 드라마", 12640),
                new SearchTerm("한글 입력기", 12110),
                new SearchTerm("한강 맛집", 11630),
                new SearchTerm("한글날", 9700),
                new SearchTerm("한라산 등반", 7530),
                new SearchTerm("한자 사전", 6410),
                new SearchTerm("카카오톡", 18100),
                new SearchTerm("카카오뱅크", 16550),
                new SearchTerm("카카오맵", 14420),
                new SearchTerm("카페 추천", 11020),
                new SearchTerm("강남 맛집", 17300),
                new SearchTerm("강릉 여행", 14200),
                new SearchTerm("값싼 항공권", 13750),
                new SearchTerm("검색어 자동완성", 13200),
                new SearchTerm("검색 시스템 설계", 12880),
                new SearchTerm("시스템 설계 면접", 15100),
                new SearchTerm("시스템 디자인", 9200),
                new SearchTerm("자동완성 트라이", 10950),
                new SearchTerm("자동완성 캐시", 8840),
                new SearchTerm("자동차 보험", 15800),
                new SearchTerm("자바 트라이", 10300),
                new SearchTerm("자바 문자열", 9600),
                new SearchTerm("java trie", 8400),
                new SearchTerm("java string", 7600),
                new SearchTerm("spring boot autocomplete", 7300),
                new SearchTerm("spring cache", 6900),
                new SearchTerm("system design interview", 12200),
                new SearchTerm("search autocomplete", 11950)
        );
    }
}
