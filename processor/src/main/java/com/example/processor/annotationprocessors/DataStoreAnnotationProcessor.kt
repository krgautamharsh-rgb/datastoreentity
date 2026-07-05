package com.example.processor.annotationprocessors

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

data class KeyInfo(
    val propertyName : String,
    val keyName : String,
    val keyConstName : String,
    val typeName : String,
    val keyFunction : String,
    val defaultValue : String,
    val isCustomType : Boolean = false,
    val qualifiedTypeName : String = ""
)

class DataStoreAnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {

        // extracting all the elements annotated with this annotation , it can be class, function,property
        val symbols = resolver.getSymbolsWithAnnotation("com.example.datastoreentity.DataStore.DataStoreEntity")

        // filtering only the classes
        val classSymbols = symbols.filterIsInstance<KSClassDeclaration>()


        for(classSymbol in classSymbols){
            generateCodeForClass(classSymbol)
        }

        return emptyList()
    }


    fun generateCodeForClass(classDeclaration: KSClassDeclaration){
        val packageName = classDeclaration.packageName.asString()
        val className = classDeclaration.simpleName.asString()
        val generatedClassName = "DataStore_$className"
        val lowerClassName = className.replaceFirstChar { it.lowercase() }

        val entityAnnotation = classDeclaration.annotations.first { it.shortName.asString() == "DataStoreEntity" }

        val dataStoreName =
            entityAnnotation.arguments.first { it.name?.asString() == "name" }.value as? String ?: className.lowercase()

        val dynamicKeys =
            entityAnnotation.arguments.firstOrNull { it.name?.asString() == "dynamicKeys" }?.value as? Boolean ?: false

        val keys = classDeclaration.getAllProperties()
            .filter { prop ->
                prop.annotations.any { it.shortName.asString() == "DataStoreKey" }
            }
            .map { prop -> extractKeyInfo(prop) }
            .toList()

        val hasCustomTypes = keys.any { it.isCustomType }

        val file = codeGenerator.createNewFile(
            Dependencies(false, classDeclaration.containingFile!!),
            packageName, generatedClassName
        )

        file.bufferedWriter().use { writer ->
            // Package
            writer.write("package $packageName\n\n")

            // Imports
            writer.write("import android.content.Context\n")
            writer.write("import androidx.datastore.core.DataStore\n")
            writer.write("import androidx.datastore.core.IOException\n")
            writer.write("import androidx.datastore.preferences.core.Preferences\n")
            writer.write("import androidx.datastore.preferences.core.edit\n")
            writer.write("import androidx.datastore.preferences.core.emptyPreferences\n")
            writer.write("import androidx.datastore.preferences.core.intPreferencesKey\n")
            writer.write("import androidx.datastore.preferences.core.stringPreferencesKey\n")
            writer.write("import androidx.datastore.preferences.core.booleanPreferencesKey\n")
            writer.write("import androidx.datastore.preferences.core.floatPreferencesKey\n")
            writer.write("import androidx.datastore.preferences.core.longPreferencesKey\n")
            writer.write("import androidx.datastore.preferences.core.doublePreferencesKey\n")
            writer.write("import androidx.datastore.preferences.core.stringSetPreferencesKey\n")
            writer.write("import androidx.datastore.preferences.preferencesDataStore\n")
            writer.write("import kotlinx.coroutines.Dispatchers\n")
            writer.write("import kotlinx.coroutines.flow.Flow\n")
            writer.write("import kotlinx.coroutines.flow.catch\n")
            writer.write("import kotlinx.coroutines.flow.first\n")
            writer.write("import kotlinx.coroutines.flow.map\n")
            writer.write("import kotlinx.coroutines.withContext\n")
            writer.write("import dagger.hilt.android.qualifiers.ApplicationContext\n")
            writer.write("import javax.inject.Inject\n")
            writer.write("import javax.inject.Singleton\n")
            if (hasCustomTypes) {
                writer.write("import kotlinx.serialization.json.Json\n")
                writer.write("import kotlinx.serialization.encodeToString\n")
                writer.write("import kotlinx.serialization.decodeFromString\n")
            }
            writer.write("\n")

            // Top-level DataStore extension property
            writer.write("val Context.${lowerClassName}DataStore: DataStore<Preferences> by preferencesDataStore(name = \"$dataStoreName\")\n\n")

            // Class declaration
            writer.write("@Singleton\n")
            writer.write("class $generatedClassName @Inject constructor(\n")
            writer.write("    @ApplicationContext private val context: Context\n")
            writer.write(") {\n\n")

            // PreferenceKeys object
            writer.write("    object PreferenceKeys {\n")
            for (key in keys) {
                writer.write("        val ${key.keyConstName} = ${key.keyFunction}(\"${key.keyName}\")\n")
            }
            writer.write("    }\n\n")

            // Generate save/readFlow/read for each key
            for (key in keys) {
                val capitalizedName = key.propertyName.replaceFirstChar { it.uppercase() }

                if (key.isCustomType) {
                    // Custom type: serialize/deserialize via JSON
                    val fullType = key.qualifiedTypeName

                    // save function (serialize to JSON)
                    writer.write("    suspend fun save$capitalizedName(value: $fullType) {\n")
                    writer.write("        val json = Json.encodeToString(value)\n")
                    writer.write("        withContext(Dispatchers.IO) {\n")
                    writer.write("            context.${lowerClassName}DataStore.edit { preferences ->\n")
                    writer.write("                preferences[PreferenceKeys.${key.keyConstName}] = json\n")
                    writer.write("            }\n")
                    writer.write("        }\n")
                    writer.write("    }\n\n")

                    // readFlow function (deserialize from JSON, nullable)
                    writer.write("    fun read${capitalizedName}Flow(): Flow<${fullType}?> {\n")
                    writer.write("        return context.${lowerClassName}DataStore.data.catch { exception ->\n")
                    writer.write("            if (exception is IOException) {\n")
                    writer.write("                emit(emptyPreferences())\n")
                    writer.write("            } else {\n")
                    writer.write("                throw exception\n")
                    writer.write("            }\n")
                    writer.write("        }.map { preferences ->\n")
                    writer.write("            val json = preferences[PreferenceKeys.${key.keyConstName}]\n")
                    writer.write("            if (json != null) Json.decodeFromString<${fullType}>(json) else null\n")
                    writer.write("        }\n")
                    writer.write("    }\n\n")

                    // read function (one-shot, nullable)
                    writer.write("    suspend fun read$capitalizedName(): ${fullType}? {\n")
                    writer.write("        val preference = context.${lowerClassName}DataStore.data.first()\n")
                    writer.write("        val json = preference[PreferenceKeys.${key.keyConstName}] ?: return null\n")
                    writer.write("        return Json.decodeFromString<${fullType}>(json)\n")
                    writer.write("    }\n\n")

                } else {
                    // Primitive type: direct preference access

                    // save function
                    writer.write("    suspend fun save$capitalizedName(value: ${key.typeName}) {\n")
                    writer.write("        withContext(Dispatchers.IO) {\n")
                    writer.write("            context.${lowerClassName}DataStore.edit { preferences ->\n")
                    writer.write("                preferences[PreferenceKeys.${key.keyConstName}] = value\n")
                    writer.write("            }\n")
                    writer.write("        }\n")
                    writer.write("    }\n\n")

                    // readFlow function
                    writer.write("    fun read${capitalizedName}Flow(): Flow<${key.typeName}> {\n")
                    writer.write("        return context.${lowerClassName}DataStore.data.catch { exception ->\n")
                    writer.write("            if (exception is IOException) {\n")
                    writer.write("                emit(emptyPreferences())\n")
                    writer.write("            } else {\n")
                    writer.write("                throw exception\n")
                    writer.write("            }\n")
                    writer.write("        }.map { preferences ->\n")
                    writer.write("            preferences[PreferenceKeys.${key.keyConstName}] ?: ${key.defaultValue}\n")
                    writer.write("        }\n")
                    writer.write("    }\n\n")

                    // read function (one-shot)
                    writer.write("    suspend fun read$capitalizedName(): ${key.typeName} {\n")
                    writer.write("        val preference = context.${lowerClassName}DataStore.data.first()\n")
                    writer.write("        return preference[PreferenceKeys.${key.keyConstName}] ?: ${key.defaultValue}\n")
                    writer.write("    }\n\n")
                }

                // contains function (same for both)
                writer.write("    suspend fun contains$capitalizedName(): Boolean {\n")
                writer.write("        val preference = context.${lowerClassName}DataStore.data.first()\n")
                writer.write("        return preference.contains(PreferenceKeys.${key.keyConstName})\n")
                writer.write("    }\n\n")
            }

            // Dynamic keys: generate typed helpers for runtime key names
            if (dynamicKeys) {
                val dynamicTypes = listOf(
                    Triple("Int", "intPreferencesKey", "0"),
                    Triple("String", "stringPreferencesKey", "\"\""),
                    Triple("Boolean", "booleanPreferencesKey", "false"),
                    Triple("Float", "floatPreferencesKey", "0f"),
                    Triple("Long", "longPreferencesKey", "0L"),
                    Triple("Double", "doublePreferencesKey", "0.0"),
                )

                for ((type, keyFn, default) in dynamicTypes) {
                    // save
                    writer.write("    suspend fun saveDynamic$type(key: String, value: $type) {\n")
                    writer.write("        withContext(Dispatchers.IO) {\n")
                    writer.write("            context.${lowerClassName}DataStore.edit { preferences ->\n")
                    writer.write("                preferences[${keyFn}(key)] = value\n")
                    writer.write("            }\n")
                    writer.write("        }\n")
                    writer.write("    }\n\n")

                    // read
                    writer.write("    suspend fun readDynamic$type(key: String, default: $type = $default): $type {\n")
                    writer.write("        val preference = context.${lowerClassName}DataStore.data.first()\n")
                    writer.write("        return preference[${keyFn}(key)] ?: default\n")
                    writer.write("    }\n\n")

                    // readFlow
                    writer.write("    fun readDynamic${type}Flow(key: String, default: $type = $default): Flow<$type> {\n")
                    writer.write("        return context.${lowerClassName}DataStore.data.catch { exception ->\n")
                    writer.write("            if (exception is IOException) {\n")
                    writer.write("                emit(emptyPreferences())\n")
                    writer.write("            } else {\n")
                    writer.write("                throw exception\n")
                    writer.write("            }\n")
                    writer.write("        }.map { preferences ->\n")
                    writer.write("            preferences[${keyFn}(key)] ?: default\n")
                    writer.write("        }\n")
                    writer.write("    }\n\n")

                    // contains
                    writer.write("    suspend fun containsDynamic$type(key: String): Boolean {\n")
                    writer.write("        val preference = context.${lowerClassName}DataStore.data.first()\n")
                    writer.write("        return preference.contains(${keyFn}(key))\n")
                    writer.write("    }\n\n")
                }
            }

            // ── Entity-level methods: save/read/clear entire data class ──

            // Save entire entity to DataStore in a single edit
            writer.write("    suspend fun save(entity: $className) {\n")
            writer.write("        withContext(Dispatchers.IO) {\n")
            writer.write("            context.${lowerClassName}DataStore.edit { preferences ->\n")
            for (key in keys) {
                if (key.isCustomType) {
                    writer.write("                preferences[PreferenceKeys.${key.keyConstName}] = Json.encodeToString(entity.${key.propertyName})\n")
                } else {
                    writer.write("                preferences[PreferenceKeys.${key.keyConstName}] = entity.${key.propertyName}\n")
                }
            }
            writer.write("            }\n")
            writer.write("        }\n")
            writer.write("    }\n\n")

            // Read entire entity as a Flow
            writer.write("    fun readFlow(): Flow<${className}?> {\n")
            writer.write("        return context.${lowerClassName}DataStore.data.catch { exception ->\n")
            writer.write("            if (exception is IOException) {\n")
            writer.write("                emit(emptyPreferences())\n")
            writer.write("            } else {\n")
            writer.write("                throw exception\n")
            writer.write("            }\n")
            writer.write("        }.map { preferences ->\n")
            writer.write("            try {\n")

            for (key in keys) {
                if (key.isCustomType) {
                    writer.write("                val ${key.propertyName}Json = preferences[PreferenceKeys.${key.keyConstName}]\n")
                }
            }

            val customKeys = keys.filter { it.isCustomType }
            if (customKeys.isNotEmpty()) {
                val nullChecks = customKeys.joinToString(" || ") { "${it.propertyName}Json == null" }
                writer.write("                if ($nullChecks) return@map null\n")
            }

            writer.write("                $className(\n")
            for ((index, key) in keys.withIndex()) {
                val comma = if (index < keys.size - 1) "," else ""
                if (key.isCustomType) {
                    writer.write("                    ${key.propertyName} = Json.decodeFromString<${key.qualifiedTypeName}>(${key.propertyName}Json)$comma\n")
                } else {
                    writer.write("                    ${key.propertyName} = preferences[PreferenceKeys.${key.keyConstName}] ?: ${key.defaultValue}$comma\n")
                }
            }
            writer.write("                )\n")
            writer.write("            } catch (e: Exception) {\n")
            writer.write("                null\n")
            writer.write("            }\n")
            writer.write("        }\n")
            writer.write("    }\n\n")

            // One-shot read of entire entity
            writer.write("    suspend fun read(): ${className}? {\n")
            writer.write("        return try {\n")
            writer.write("            val preferences = context.${lowerClassName}DataStore.data.first()\n")

            for (key in keys) {
                if (key.isCustomType) {
                    writer.write("            val ${key.propertyName}Json = preferences[PreferenceKeys.${key.keyConstName}] ?: return null\n")
                }
            }

            writer.write("            $className(\n")
            for ((index, key) in keys.withIndex()) {
                val comma = if (index < keys.size - 1) "," else ""
                if (key.isCustomType) {
                    writer.write("                ${key.propertyName} = Json.decodeFromString<${key.qualifiedTypeName}>(${key.propertyName}Json)$comma\n")
                } else {
                    writer.write("                ${key.propertyName} = preferences[PreferenceKeys.${key.keyConstName}] ?: ${key.defaultValue}$comma\n")
                }
            }
            writer.write("            )\n")
            writer.write("        } catch (e: Exception) {\n")
            writer.write("            null\n")
            writer.write("        }\n")
            writer.write("    }\n\n")

            // Clear all data for this entity
            writer.write("    suspend fun clear() {\n")
            writer.write("        context.${lowerClassName}DataStore.edit { it.clear() }\n")
            writer.write("    }\n\n")

            writer.write("}\n")
        }
    }


    private fun extractKeyInfo(prop : KSPropertyDeclaration) : KeyInfo {
        val propertyName = prop.simpleName.asString()

        val keyAnnotation = prop.annotations.first{it.shortName.asString() == "DataStoreKey"}
        val keyName = keyAnnotation.arguments.first{ it.name?.asString() == "name"}.value as? String ?: propertyName

        val keyConstName = propertyName.replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

        val resolvedType = prop.type.resolve()
        val qualifiedName = resolvedType.declaration.qualifiedName?.asString() ?: ""

        val isPrimitive = isPrimitiveType(qualifiedName)

        if (isPrimitive) {
            val (keyFunction, typeName, defaultValue) = getTypeMapping(qualifiedName)
            return KeyInfo(propertyName, keyName, keyConstName, typeName, keyFunction, defaultValue)
        } else {
            // Custom type — store as JSON string
            val simpleTypeName = resolvedType.declaration.simpleName.asString()
            return KeyInfo(
                propertyName, keyName, keyConstName,
                typeName = simpleTypeName,
                keyFunction = "stringPreferencesKey",
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

    private fun getTypeMapping(qualifiedName: String): Triple<String, String, String> {
        return when (qualifiedName) {
            "kotlin.Int" -> Triple("intPreferencesKey", "Int", "0")
            "kotlin.String" -> Triple("stringPreferencesKey", "String", "\"\"")
            "kotlin.Boolean" -> Triple("booleanPreferencesKey", "Boolean", "false")
            "kotlin.Float" -> Triple("floatPreferencesKey", "Float", "0f")
            "kotlin.Long" -> Triple("longPreferencesKey", "Long", "0L")
            "kotlin.Double" -> Triple("doublePreferencesKey", "Double", "0.0")
            "kotlin.collections.Set" -> Triple("stringSetPreferencesKey", "Set<String>", "emptySet()")
            else -> {
                logger.error("Unsupported type: $qualifiedName")
                Triple("stringPreferencesKey", "String", "\"\"")
            }
        }
    }
}
