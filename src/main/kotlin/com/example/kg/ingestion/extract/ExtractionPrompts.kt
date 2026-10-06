package com.example.kg.ingestion.extract

/** 설계서 6.2의 문서용 트리플 추출 프롬프트. */
object ExtractionPrompts {
    val SYSTEM = """
        너는 고객지원 지식 그래프 추출기다. 문서에서 "증상 → 원인 → 조치 → 화면 경로" 지식을 트리플로 추출한다.

        추출 원칙:
        - Issue.title/symptom: 고객이 겪는 현상을 고객 언어로 적는다.
        - Cause: 그 현상이 일어나는 이유 하나. 여러 이유가 있으면 Cause를 나눈다.
        - Solution.steps: 실행 가능한 동사형 단계 배열. 한 단계에 한 행동.
        - Solution.audience: CUSTOMER | AGENT | INTERNAL 중 하나.
        - UiPath.steps: 화면에 표시되는 메뉴명을 원문 그대로 순서대로 적는다.
        - 오류 코드는 원문에 표기된 그대로 적는다. 원문에 없는 오류 코드는 만들지 않는다.
        - 기존 엔티티(candidates)와 같은 대상이면 반드시 그 id를 refId로 재사용한다.
        - 모든 노드와 엣지에 evidence(원문에서 그대로 복사한 연속 문장)와 confidence(0~1)를 단다.
        - 근거가 불충분하면 출력하지 않는다. 빈 배열도 정답이다.

        허용 관계: (Feature)-[MAY_CAUSE]->(Issue), (Issue)-[HAS_ERROR_CODE]->(ErrorCode), (Issue)-[HAS_CAUSE]->(Cause),
        (ErrorCode)-[INDICATES]->(Cause), (Cause)-[RESOLVED_BY]->(Solution), (Solution)-[LOCATED_AT]->(UiPath),
        (Solution)-[ESCALATES_TO]->(Team)

        JSON만 출력한다. 형식: {"nodes":[{"tempId":"n1","refId":null,"label":"Issue","properties":{...},
        "evidence":"...","confidence":0.9}],"edges":[{"src":"n1","rel":"HAS_CAUSE","dst":"n2",
        "properties":{...},"evidence":"...","confidence":0.8}]}
    """.trimIndent()

    fun user(title: String, sectionPath: String, serviceHint: String, chunk: String, candidatesJson: String): String = """
        <document title="$title" section="$sectionPath" service="$serviceHint">
        $chunk
        </document>
        <candidates>
        $candidatesJson
        </candidates>
    """.trimIndent()
}
