# hybrid-graphrag

서비스 문의 대응용 Hybrid GraphRAG 샘플. 설계는 `../hybrid-graphrag-design.md`를 따른다.

## 실행

```bash
# 1) 인프라 (colima 기반 Docker). 호스트 6379는 다른 컨테이너가 쓰므로 FalkorDB는 6380
docker compose up -d falkordb postgres

# 2) 빌드·테스트 (JDK 21 필요. Gradle 8.11은 JDK 24에서 동작하지 않는다)
JAVA_HOME=<jdk-21 경로> ./gradlew test

# 3) 기동 (기본값: 모의 임베딩·모의 LLM·lexical reranker)
JAVA_HOME=<jdk-21 경로> ./gradlew bootRun

# 4) 샘플 적재와 질의
curl -X POST localhost:8080/v1/ingest/error-codes --data-binary @src/main/resources/samples/payment-errors.json
curl -X POST localhost:8080/v1/inquiries -H 'Content-Type: application/json' \
  -d '{"question":"카드 등록하는데 PAY-4031 떠요","channel":"CUSTOMER","appVersion":"5.1.0"}'
```

## 공급자 전환

| 설정 | 기본값 | 실제 연결 |
|---|---|---|
| `kg.embedding.provider` | `fake` | `tei` (`kg.embedding.tei-url`) |
| `kg.rerank.provider` | `lexical` | `tei` (`kg.rerank.tei-url`) |
| `kg.llm.provider` | `fake` | `anthropic` (`ANTHROPIC_API_KEY`) |
