package com.example.processor.annotationprocessors

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class DataStoreAnnotationProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return DataStoreAnnotationProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger
        )
    }
}
