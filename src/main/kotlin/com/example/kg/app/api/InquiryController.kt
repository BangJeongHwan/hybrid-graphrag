package com.example.kg.app.api

import com.example.kg.domain.model.InquiryRequest
import com.example.kg.domain.model.InquiryResponse
import com.example.kg.retrieval.HybridRagService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/inquiries")
class InquiryController(private val service: HybridRagService) {

    @PostMapping
    suspend fun answer(@RequestBody request: InquiryRequest): InquiryResponse = service.answer(request)
}
