package com.example.kg.app.api

import com.example.kg.ingestion.extract.TripleExtractor
import com.example.kg.ingestion.parse.ErrorEnumParser
import com.example.kg.ingestion.project.DocIndexer
import com.example.kg.ingestion.project.DocumentInput
import com.example.kg.ingestion.project.GraphProjector
import com.example.kg.domain.ontology.NodeLabel
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ErrorEnumIngestResult(val nodes: Int, val edges: Int)

data class DocumentIngestRequest(
    val docId: String,
    val docType: String = "FAQ",
    val title: String,
    val sectionPath: List<String> = emptyList(),
    val serviceHint: String = "",
    val audience: String = "CUSTOMER",
    val content: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class DocumentIngestResult(
    val chunkId: String,
    val acceptedNodes: Int,
    val acceptedEdges: Int,
    val rejected: List<String>,
)

@RestController
@RequestMapping("/v1/ingest")
class IngestionController(
    private val errorEnumParser: ErrorEnumParser,
    private val projector: GraphProjector,
    private val extractor: TripleExtractor,
    private val indexer: DocIndexer,
) {
    /** L0: 오류 코드 Enum JSON을 APPROVED 지식으로 투영한다. */
    @PostMapping("/error-codes")
    fun ingestErrorCodes(@RequestBody json: String): ErrorEnumIngestResult {
        val batch = errorEnumParser.parse(json)
        projector.project(batch)
        return ErrorEnumIngestResult(batch.nodes.size, batch.edges.size)
    }

    /** L2: 문서를 색인하고, LLM으로 트리플을 추출해 DRAFT로 투영한다. */
    @PostMapping("/documents")
    fun ingestDocument(@RequestBody req: DocumentIngestRequest): DocumentIngestResult {
        val chunkId = indexer.index(
            DocumentInput(
                docId = req.docId, docType = req.docType, title = req.title, sectionPath = req.sectionPath,
                serviceIds = listOfNotNull(req.serviceHint.takeIf { it.isNotBlank() }), entityIds = emptyList(),
                audience = req.audience, content = req.content, updatedAt = req.updatedAt,
            ),
        )
        val outcome = extractor.extract(req.title, req.sectionPath.joinToString(" > "), req.serviceHint, req.content)
        projector.project(outcome.accepted)
        return DocumentIngestResult(
            chunkId = chunkId,
            acceptedNodes = outcome.accepted.nodes.size,
            acceptedEdges = outcome.accepted.edges.size,
            rejected = outcome.rejected.map { "${it.item}: ${it.reason}" },
        )
    }
}
