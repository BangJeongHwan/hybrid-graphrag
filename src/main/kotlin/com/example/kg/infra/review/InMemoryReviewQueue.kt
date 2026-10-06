package com.example.kg.infra.review

import com.example.kg.domain.port.LearningItem
import com.example.kg.domain.port.ReviewQueuePort
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentLinkedQueue

/** 샘플용 메모리 큐. 실제 구현은 PostgreSQL 테이블 + 관리자 승인 API로 바꾼다. */
@Component
class InMemoryReviewQueue : ReviewQueuePort {
    private val items = ConcurrentLinkedQueue<LearningItem>()

    override fun enqueue(item: LearningItem) {
        items += item
    }

    fun snapshot(): List<LearningItem> = items.toList()
}
