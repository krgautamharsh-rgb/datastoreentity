package com.example.processor.annotationprocessors

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

// ── Type references ──
private val CONTEXT = ClassName("android.content", "Context")
private val DATA_STORE = ClassName("androidx.datastore.core", "DataStore")
private val PREFERENCES = ClassName("androidx.datastore.preferences.core", "Preferences")
private val PREFERENCES_KEY = PREFERENCES.nestedClass("Key")
private val FLOW = ClassName("kotlinx.coroutines.flow", "Flow")
private val SINGLETON = ClassName("javax.inject", "Singleton")
private val INJECT = ClassName("javax.inject", "Inject")
private val APP_CONTEXT = ClassName("dagger.hilt.android.qualifiers", "ApplicationContext")
private val IO_EXCEPTION = ClassName("androidx.datastore.core", "IOException")
private val DISPATCHERS = ClassName("kotlinx.coroutines", "Dispatchers")
private val JSON = ClassName("kotlinx.serialization.json", "Json")

// ── Member references ──
private val EDIT = MemberName("androidx.datastore.preferences.core", "edit")
private val EMPTY_PREFERENCES = MemberName("androidx.datastore.preferences.core", "emptyPreferences")
private val PREFERENCES_DATA_STORE = MemberName("androidx.datastore.preferences", "preferencesDataStore")
private val WITH_CONTEXT = MemberName("kotlinx.coroutines", "withContext")
private val FLOW_CATCH = MemberName("kotlinx.coroutines.flow", "catch")
private val FLOW_MAP = MemberName("kotlinx.coroutines.flow", "map")
private val FLOW_FIRST = MemberName("kotlinx.coroutines.flow", "first")
private val ENCODE_TO_STRING = MemberName("kotlinx.serialization", "encodeToString")
private val DECODE_FROM_STRING = MemberName("kotlinx.serialization", "decodeFromString")

// ── Preference key functions ──
private val INT_PREF_KEY = MemberName("androidx.datastore.preferences.core", "intPreferencesKey")
private val STRING_PREF_KEY = MemberName("androidx.datastore.preferences.core", "stringPreferencesKey")
private val BOOLEAN_PREF_KEY = MemberName("androidx.datastore.preferences.core", "booleanPreferencesKey")
private val FLOAT_PREF_KEY = MemberName("androidx.datastore.preferences.core", "floatPreferencesKey")
private val LONG_PREF_KEY = MemberName("androidx.datastore.preferences.core", "longPreferencesKey")
private val DOUBLE_PREF_KEY = MemberName("androidx.datastore.preferences.core", "doublePreferencesKey")
private val STRING_SET_PREF_KEY = MemberName("androidx.datastore.preferences.core", "stringSetPreferencesKey")

data class KeyInfo(
    val propertyName: String,
    val keyName: String,
    val keyConstName: String,
    val typeName: TypeName,
    val keyFunction: MemberName,
    val defaultValue: String,
    val isCustomType: Boolean = false,
    val qualifiedTypeName: String = ""
)

class DataStoreAnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(
            "com.example.datastoreentity.DataStore.DataStoreEntity"
        )
        val classSymbols = symbols.filterIsInstance<KSClassDeclaration>()

        for (classSymbol in classSymbols) {
            generateCodeForClass(classSymbol)
        }

        return emptyList()
    }

    private fun generateCodeForClass(classDeclaration: KSClassDeclaration) {
        val packageName = classDeclaration.packageName.asString()
        val className = classDeclaration.simpleName.asString()
        val generatedClassName = "DataStore_$className"
        val lowerClassName = className.replaceFirstChar { it.lowercase() }

        val entityAnnotation = classDeclaration.annotations.first {
            it.shortName.asString() == "DataStoreEntity"
        }
        val dataStoreName = entityAnnotation.arguments
            .first { it.name?.asString() == "name" }.value as? String ?: className.lowercase()
        val dynamicKeys = entityAnnotation.arguments
            .firstOrNull { it.name?.asString() == "dynamicKeys" }?.value as? Boolean ?: false

        val keys = classDeclaration.getAllProperties()
            .filter { prop -> prop.annotations.any { it.shortName.asString() == "DataStoreKey" } }
            .map { prop -> extractKeyInfo(prop) }
            .toList()

        val entityType = ClassName(packageName, className)

        val fileSpec = FileSpec.builder(packageName, generatedClassName)
            .addProperty(buildDataStoreProperty(lowerClassName, dataStoreName))
            .addType(buildDataStoreClass(generatedClassName, lowerClassName, keys, dynamicKeys, entityType))
            .build()

        fileSpec.writeTo(codeGenerator, Dependencies(false, classDeclaration.containingFile!!))
    }

    // ── Top-level DataStore extension property ──

    private fun buildDataStoreProperty(lowerClassName: String, dataStoreName: String): PropertySpec {
        return PropertySpec.builder(
            "${lowerClassName}DataStore",
            DATA_STORE.parameterizedBy(PREFERENCES)
        )
            .receiver(CONTEXT)
            .delegate(CodeBlock.of("%M(name = %S)", PREFERENCES_DATA_STORE, dataStoreName))
            .build()
    }

    // ── Main generated class ──

    private fun buildDataStoreClass(
        generatedClassName: String,
        lowerClassName: String,
        keys: List<KeyInfo>,
        dynamicKeys: Boolean,
        entityType: ClassName
    ): TypeSpec {
        val classBuilder = TypeSpec.classBuilder(generatedClassName)
            .superclass(entityType)
            .addAnnotation(SINGLETON)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addAnnotation(INJECT)
                    .addParameter(
                        ParameterSpec.builder("context", CONTEXT)
                            .addAnnotation(APP_CONTEXT)
                            .build()
                    )
                    .build()
            )
            .addProperty(
                PropertySpec.builder("context", CONTEXT)
                    .initializer("context")
                    .addModifiers(KModifier.PRIVATE)
                    .build()
            )
            .addType(buildPreferenceKeysObject(keys))

        for (key in keys) {
            classBuilder.addFunction(buildSaveFunction(key, lowerClassName))
            classBuilder.addFunction(buildReadFlowFunction(key, lowerClassName))
            classBuilder.addFunction(buildReadFunction(key, lowerClassName))
            classBuilder.addFunction(buildContainsFunction(key, lowerClassName))
        }

        if (dynamicKeys) {
            classBuilder.addFunctions(buildDynamicMethods(lowerClassName))
        }

        return classBuilder.build()
    }

    // ── PreferenceKeys companion object ──

    private fun buildPreferenceKeysObject(keys: List<KeyInfo>): TypeSpec {
        val objectBuilder = TypeSpec.objectBuilder("PreferenceKeys")
        for (key in keys) {
            val keyValueType = if (key.isCustomType) STRING else key.typeName
            objectBuilder.addProperty(
                PropertySpec.builder(key.keyConstName, PREFERENCES_KEY.parameterizedBy(keyValueType))
                    .initializer(CodeBlock.of("%M(%S)", key.keyFunction, key.keyName))
                    .build()
            )
        }
        return objectBuilder.build()
    }

    // ── Per-key save function ──

    private fun buildSaveFunction(key: KeyInfo, lowerClassName: String): FunSpec {
        val capitalizedName = key.propertyName.replaceFirstChar { it.uppercase() }
        val builder = FunSpec.builder("save$capitalizedName")
            .addModifiers(KModifier.SUSPEND)

        val code = CodeBlock.builder()

        if (key.isCustomType) {
            builder.addParameter("value", key.typeName)
            code.addStatement("val json = %T.%M(value)", JSON, ENCODE_TO_STRING)
            code.beginControlFlow("%M(%T.IO)", WITH_CONTEXT, DISPATCHERS)
            code.add("context.${lowerClassName}DataStore.%M { preferences ->\n", EDIT)
            code.indent()
            code.addStatement("preferences[PreferenceKeys.%L] = json", key.keyConstName)
            code.unindent()
            code.add("}\n")
            code.endControlFlow()
        } else {
            builder.addParameter("value", key.typeName)
            code.beginControlFlow("%M(%T.IO)", WITH_CONTEXT, DISPATCHERS)
            code.add("context.${lowerClassName}DataStore.%M { preferences ->\n", EDIT)
            code.indent()
            code.addStatement("preferences[PreferenceKeys.%L] = value", key.keyConstName)
            code.unindent()
            code.add("}\n")
            code.endControlFlow()
        }

        return builder.addCode(code.build()).build()
    }

    // ── Per-key readFlow function ──

    private fun buildReadFlowFunction(key: KeyInfo, lowerClassName: String): FunSpec {
        val capitalizedName = key.propertyName.replaceFirstChar { it.uppercase() }
        val builder = FunSpec.builder("read${capitalizedName}Flow")

        val code = errorHandlingFlow(lowerClassName)

        if (key.isCustomType) {
            builder.returns(FLOW.parameterizedBy(key.typeName.copy(nullable = true)))
            code.add("}.%M { preferences ->\n", FLOW_MAP)
            code.indent()
            code.addStatement("val json = preferences[PreferenceKeys.%L]", key.keyConstName)
            code.addStatement(
                "if (json != null) %T.%M<%T>(json) else null",
                JSON, DECODE_FROM_STRING, key.typeName
            )
            code.unindent()
            code.add("}\n")
        } else {
            builder.returns(FLOW.parameterizedBy(key.typeName))
            code.add("}.%M { preferences ->\n", FLOW_MAP)
            code.indent()
            code.addStatement("preferences[PreferenceKeys.%L] ?: %L", key.keyConstName, key.defaultValue)
            code.unindent()
            code.add("}\n")
        }

        return builder.addCode(code.build()).build()
    }

    // ── Per-key one-shot read function ──

    private fun buildReadFunction(key: KeyInfo, lowerClassName: String): FunSpec {
        val capitalizedName = key.propertyName.replaceFirstChar { it.uppercase() }
        val builder = FunSpec.builder("read$capitalizedName")
            .addModifiers(KModifier.SUSPEND)

        val code = CodeBlock.builder()
            .addStatement("val preference = context.${lowerClassName}DataStore.data.%M()", FLOW_FIRST)

        if (key.isCustomType) {
            builder.returns(key.typeName.copy(nullable = true))
            code.addStatement("val json = preference[PreferenceKeys.%L] ?: return null", key.keyConstName)
            code.addStatement("return %T.%M<%T>(json)", JSON, DECODE_FROM_STRING, key.typeName)
        } else {
            builder.returns(key.typeName)
            code.addStatement("return preference[PreferenceKeys.%L] ?: %L", key.keyConstName, key.defaultValue)
        }

        return builder.addCode(code.build()).build()
    }

    // ── Per-key contains function ──

    private fun buildContainsFunction(key: KeyInfo, lowerClassName: String): FunSpec {
        val capitalizedName = key.propertyName.replaceFirstChar { it.uppercase() }
        return FunSpec.builder("contains$capitalizedName")
            .addModifiers(KModifier.SUSPEND)
            .returns(BOOLEAN)
            .addCode(
                CodeBlock.builder()
                    .addStatement("val preference = context.${lowerClassName}DataStore.data.%M()", FLOW_FIRST)
                    .addStatement("return preference.contains(PreferenceKeys.%L)", key.keyConstName)
                    .build()
            )
            .build()
    }

    // ── Dynamic key methods ──

    private fun buildDynamicMethods(lowerClassName: String): List<FunSpec> {
        val methods = mutableListOf<FunSpec>()

        fun addDynamicType(name: String, keyFn: MemberName, type: TypeName, defaultValue: String) {
            // save
            methods.add(
                FunSpec.builder("saveDynamic$name")
                    .addModifiers(KModifier.SUSPEND)
                    .addParameter("key", STRING)
                    .addParameter("value", type)
                    .addCode(
                        CodeBlock.builder()
                            .beginControlFlow("%M(%T.IO)", WITH_CONTEXT, DISPATCHERS)
                            .add("context.${lowerClassName}DataStore.%M { preferences ->\n", EDIT)
                            .indent()
                            .addStatement("preferences[%M(key)] = value", keyFn)
                            .unindent()
                            .add("}\n")
                            .endControlFlow()
                            .build()
                    )
                    .build()
            )

            // read
            methods.add(
                FunSpec.builder("readDynamic$name")
                    .addModifiers(KModifier.SUSPEND)
                    .addParameter("key", STRING)
                    .addParameter(
                        ParameterSpec.builder("default", type)
                            .defaultValue(defaultValue)
                            .build()
                    )
                    .returns(type)
                    .addCode(
                        CodeBlock.builder()
                            .addStatement(
                                "val preference = context.${lowerClassName}DataStore.data.%M()",
                                FLOW_FIRST
                            )
                            .addStatement("return preference[%M(key)] ?: default", keyFn)
                            .build()
                    )
                    .build()
            )

            // readFlow
            methods.add(
                FunSpec.builder("readDynamic${name}Flow")
                    .addParameter("key", STRING)
                    .addParameter(
                        ParameterSpec.builder("default", type)
                            .defaultValue(defaultValue)
                            .build()
                    )
                    .returns(FLOW.parameterizedBy(type))
                    .addCode(
                        errorHandlingFlow(lowerClassName)
                            .add("}.%M { preferences ->\n", FLOW_MAP)
                            .indent()
                            .addStatement("preferences[%M(key)] ?: default", keyFn)
                            .unindent()
                            .add("}\n")
                            .build()
                    )
                    .build()
            )

            // contains
            methods.add(
                FunSpec.builder("containsDynamic$name")
                    .addModifiers(KModifier.SUSPEND)
                    .addParameter("key", STRING)
                    .returns(BOOLEAN)
                    .addCode(
                        CodeBlock.builder()
                            .addStatement(
                                "val preference = context.${lowerClassName}DataStore.data.%M()",
                                FLOW_FIRST
                            )
                            .addStatement("return preference.contains(%M(key))", keyFn)
                            .build()
                    )
                    .build()
            )
        }

        addDynamicType("Int", INT_PREF_KEY, INT, "0")
        addDynamicType("String", STRING_PREF_KEY, STRING, "\"\"")
        addDynamicType("Boolean", BOOLEAN_PREF_KEY, BOOLEAN, "false")
        addDynamicType("Float", FLOAT_PREF_KEY, FLOAT, "0f")
        addDynamicType("Long", LONG_PREF_KEY, LONG, "0L")
        addDynamicType("Double", DOUBLE_PREF_KEY, DOUBLE, "0.0")

        return methods
    }

    // ── Shared: error-handling Flow preamble ──

    private fun errorHandlingFlow(lowerClassName: String): CodeBlock.Builder {
        return CodeBlock.builder()
            .add("return context.${lowerClassName}DataStore.data.%M { exception ->\n", FLOW_CATCH)
            .indent()
            .beginControlFlow("if (exception is %T)", IO_EXCEPTION)
            .addStatement("emit(%M())", EMPTY_PREFERENCES)
            .nextControlFlow("else")
            .addStatement("throw exception")
            .endControlFlow()
            .unindent()
    }

    // ── Key extraction & type mapping ──

    private fun extractKeyInfo(prop: KSPropertyDeclaration): KeyInfo {
        val propertyName = prop.simpleName.asString()

        val keyAnnotation = prop.annotations.first { it.shortName.asString() == "DataStoreKey" }
        val keyName = keyAnnotation.arguments
            .first { it.name?.asString() == "name" }.value as? String ?: propertyName

        val keyConstName = propertyName.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

        val resolvedType = prop.type.resolve()
        val qualifiedName = resolvedType.declaration.qualifiedName?.asString() ?: ""

        if (isPrimitiveType(qualifiedName)) {
            val (keyFunction, typeName, defaultValue) = getTypeMapping(qualifiedName)
            return KeyInfo(propertyName, keyName, keyConstName, typeName, keyFunction, defaultValue)
        } else {
            val typePackage = resolvedType.declaration.packageName.asString()
            val simpleTypeName = resolvedType.declaration.simpleName.asString()
            return KeyInfo(
                propertyName, keyName, keyConstName,
                typeName = ClassName(typePackage, simpleTypeName),
                keyFunction = STRING_PREF_KEY,
                defaultValue = "\"\"",
                isCustomType = true,
                qualifiedTypeName = qualifiedName
            )
        }
    }

    private fun isPrimitiveType(qualifiedName: String): Boolean {
        return qualifiedName in listOf(
            "kotlin.Int", "kotlin.String", "kotlin.Boolean",
            "kotlin.Float", "kotlin.Long", "kotlin.Double",
            "kotlin.collections.Set"
        )
    }

    private fun getTypeMapping(qualifiedName: String): Triple<MemberName, TypeName, String> {
        return when (qualifiedName) {
            "kotlin.Int" -> Triple(INT_PREF_KEY, INT, "0")
            "kotlin.String" -> Triple(STRING_PREF_KEY, STRING, "\"\"")
            "kotlin.Boolean" -> Triple(BOOLEAN_PREF_KEY, BOOLEAN, "false")
            "kotlin.Float" -> Triple(FLOAT_PREF_KEY, FLOAT, "0f")
            "kotlin.Long" -> Triple(LONG_PREF_KEY, LONG, "0L")
            "kotlin.Double" -> Triple(DOUBLE_PREF_KEY, DOUBLE, "0.0")
            "kotlin.collections.Set" -> Triple(STRING_SET_PREF_KEY, SET.parameterizedBy(STRING), "emptySet()")
            else -> {
                logger.error("Unsupported type: $qualifiedName")
                Triple(STRING_PREF_KEY, STRING, "\"\"")
            }
        }
    }
}
