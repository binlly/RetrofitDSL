package com.zxy.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Created by y on 2025/10/3-23:53
 *
 * @author y >_
 * .     _                                           ____  _       _ _
 * .    / \__      _____  ___  ___  _ __ ___   ___  | __ )(_)_ __ | | |_   _
 * .   / _ \ \ /\ / / _ \/ __|/ _ \| '_ ` _ \ / _ \ |  _ \| | '_ \| | | | | |
 * .  / ___ \ V  V /  __/\__ \ (_) | | | | | |  __/ | |_) | | | | | | | |_| |
 * . /_/   \_\_/\_/ \___||___/\___/|_| |_| |_|\___| |____/|_|_| |_|_|_|\__, |
 * .                                                                   |___/
 */

class RetrofitServiceDslProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    companion object {
        const val RETROFIT_SERVICE_ANNOTATION = "com.zxy.ksp.RetrofitService"
        const val RETROFIT_SERVICE_DSL_CLASS_NAME = "RetrofitServiceDsl"
        const val DSL_RESULT_CLASS_NAME = "DslResult"
        const val SHOW_LOADING_VAR_NAME = "showLoading"
        const val RETROFIT_SERVICE_PROPERTY_NAME = "retrofitService"
        const val HTTP_ERROR_MESSAGE_TEMPLATE =
            $$"HTTP Error: ${response.code()} - ${response.message()}"
        const val CALL_CLASS_NAME = "retrofit2.Call"
        const val RESPONSE_CLASS_NAME = "retrofit2.Response"

        // 定义 Retrofit HTTP 注解的类名集合
        val RETROFIT_HTTP_ANNOTATIONS = setOf(
            "retrofit2.http.GET",
            "retrofit2.http.POST",
            "retrofit2.http.PUT",
            "retrofit2.http.DELETE",
            "retrofit2.http.PATCH",
            "retrofit2.http.HEAD",
            "retrofit2.http.OPTIONS"
        )
    }

    private val processedServices = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("RetrofitServiceDslSymbolProcessor: process() called")

        val serviceDeclarations = resolver.getSymbolsWithAnnotation(RETROFIT_SERVICE_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE }

        val newServicesToProcess = serviceDeclarations.filter { service ->
            val qualifiedName = service.qualifiedName?.asString()
            qualifiedName != null && !processedServices.contains(qualifiedName)
        }

        logger.info("Found ${newServicesToProcess.count()} new @RetrofitService annotated interfaces to process")

        val unprocessedSymbols = mutableListOf<KSAnnotated>()

        newServicesToProcess.forEach { service ->
            val qualifiedName = service.qualifiedName?.asString()
            if (qualifiedName != null) {
                processedServices.add(qualifiedName)
            }

            val isValid = service.validate()
            if (isValid) {
                logger.info("Processing service: $qualifiedName")
                generateDslExtensions(service)
            } else {
                logger.warn("Service $qualifiedName failed validation, adding back to unprocessed list.")
                unprocessedSymbols.add(service)
            }
        }

        return unprocessedSymbols
    }

    // 生成文件
    private fun generateDslExtensions(service: KSClassDeclaration) {
        val packageName = service.packageName.asString()
        val serviceName = service.simpleName.asString()
        val dslExtensionClassName = "${serviceName}DslExtensions"

        val fileBuilder = FileSpec.builder(packageName, dslExtensionClassName)

        service.getAllFunctions().filter(::hasRetrofitHttpAnnotation).forEach { function ->
            val extensionFunSpec = generateExtensionFunction(function, service)
            fileBuilder.addFunction(extensionFunSpec)
        }

        val fileSpec = fileBuilder.build()

        try {
            val originatingKSFiles = service.containingFile?.let { setOf(it) } ?: emptySet()
            codeGenerator.createNewFile(
                Dependencies(false, *originatingKSFiles.toTypedArray()),
                packageName,
                dslExtensionClassName
            ).use { output ->
                OutputStreamWriter(output, StandardCharsets.UTF_8).use { writer ->
                    fileSpec.writeTo(writer)
                }
            }
            logger.info("Generated DSL extensions for $serviceName in $packageName.$dslExtensionClassName")
        } catch (e: Exception) {
            logger.error("Failed to generate file for $serviceName: ${e.message}")
            e.printStackTrace()
        }
    }

    // 检查方法是否包含 Retrofit HTTP 注解
    private fun hasRetrofitHttpAnnotation(function: KSFunctionDeclaration): Boolean {
        return function.annotations.any { annotation ->
            val annotationName = annotation.shortName.asString()
            val annotationQualifiedName =
                annotation.annotationType.resolve().declaration.qualifiedName?.asString()
            RETROFIT_HTTP_ANNOTATIONS.contains(annotationName) || RETROFIT_HTTP_ANNOTATIONS.contains(
                annotationQualifiedName
            )
        }
    }

    // 根据Service内放入retrofit注解方法，生成DSL的扩展方法
    private fun generateExtensionFunction(
        function: KSFunctionDeclaration,
        service: KSClassDeclaration
    ): FunSpec {
        val packageName = service.packageName.asString()
        val functionName = function.simpleName.asString()
        val returnType = function.returnType?.resolve()
            ?: throw IllegalArgumentException("Function $functionName must have a return type")

        val isCallType = returnType.declaration.qualifiedName?.asString() == CALL_CLASS_NAME
        val isResponseType = returnType.declaration.qualifiedName?.asString() == RESPONSE_CLASS_NAME
        val isSuspend = function.modifiers.contains(Modifier.SUSPEND)

        var dslResultType: TypeName = returnType.toTypeName()
        if (isCallType && returnType.arguments.isNotEmpty()) {
            dslResultType = returnType.arguments[0].type!!.resolve().toTypeName()
        } else if (isResponseType && returnType.arguments.isNotEmpty()) {
            dslResultType = returnType.arguments[0].type!!.resolve().toTypeName()
        }

        val retrofitServiceDslClassName =
            ClassName(packageName, RETROFIT_SERVICE_DSL_CLASS_NAME)
        val dslResultClassName = ClassName(packageName, DSL_RESULT_CLASS_NAME)
        val callClassName = ClassName("retrofit2", "Call")
        val callbackClassName = ClassName("retrofit2", "Callback")
        val responseClassName = ClassName("retrofit2", "Response")
        val exceptionClassName = ClassName("java.lang", "Exception")

        val funBuilder = FunSpec.builder(functionName)
            .receiver(retrofitServiceDslClassName)

        function.parameters.forEach { param ->
            val paramType = param.type.resolve().toTypeName()
            val paramName = param.name?.asString() ?: "param"
            funBuilder.addParameter(paramName, paramType)
        }

        val dslResultTypeName = dslResultClassName.parameterizedBy(dslResultType)
        funBuilder.addParameter(
            ParameterSpec.builder(
                "block", LambdaTypeName.get(
                    receiver = dslResultTypeName,
                    returnType = UNIT
                )
            ).build()
        )

        // 构建函数体
        val functionBody = CodeBlock.builder()
            .addStatement(
                "val service = this.%N as %T",
                RETROFIT_SERVICE_PROPERTY_NAME,
                service.toClassName()
            )
            .addStatement("%N(true)", SHOW_LOADING_VAR_NAME)
            .beginControlFlow("try {")
            .addStatement(
                "val result = service.%N(%L)",
                functionName,
                function.parameters.joinToString(", ") { it.name?.asString() ?: "param" }
            )

        if (isSuspend) {
            functionBody
                .addStatement("// Suspend function handling requires a CoroutineScope")
                .addStatement("// This requires a different DSL structure or a wrapper to convert to Call")
                .addStatement("val dslResult = %T<%T>()", dslResultClassName, dslResultType)
                .addStatement("block(dslResult)")
                .addStatement("// Placeholder for suspend call: dslResult.onSuccessBlock?.invoke(/* suspend result */)")
        } else {
            if (isCallType) {
                functionBody
                    .addStatement("val dslResult = %T<%T>()", dslResultClassName, dslResultType)
                    .addStatement("block(dslResult)")
                    .beginControlFlow(
                        "result.enqueue(object : %T<%T> {",
                        callbackClassName,
                        dslResultType
                    )
                    .beginControlFlow(
                        "override fun onResponse(call: %T<%T>, response: %T<%T>) {",
                        callClassName,
                        dslResultType,
                        responseClassName,
                        dslResultType
                    )
                    .beginControlFlow("if (response.isSuccessful) {")
                    .addStatement("dslResult.onSuccessBlock?.invoke(response.body()!!)").unindent()
                    .addStatement("} else {").indent()
                    .addStatement(
                        "dslResult.onFailBlock?.invoke(%T(\"$HTTP_ERROR_MESSAGE_TEMPLATE\"))",
                        exceptionClassName
                    )
                    .endControlFlow()
                    .endControlFlow()
                    .beginControlFlow(
                        "override fun onFailure(call: %T<%T>, t: Throwable) {",
                        callClassName,
                        dslResultType
                    )
                    .addStatement("dslResult.onFailBlock?.invoke(t)")
                    .endControlFlow().unindent()
                    .addStatement("})")
            } else if (isResponseType) {
                functionBody
                    .addStatement("val dslResult = %T<%T>()", dslResultClassName, dslResultType)
                    .addStatement("block(dslResult)")
                    .beginControlFlow("if (result.isSuccessful) {")
                    .addStatement("dslResult.onSuccessBlock?.invoke(result.body()!!)").unindent()
                    .addStatement("} else {").indent()
                    .addStatement(
                        "dslResult.onFailBlock?.invoke(%T(\"$HTTP_ERROR_MESSAGE_TEMPLATE\"))",
                        exceptionClassName
                    )
                    .endControlFlow()
            } else {
                functionBody
                    .addStatement("val dslResult = %T<%T>()", dslResultClassName, dslResultType)
                    .addStatement("block(dslResult)")
                    .addStatement("dslResult.onSuccessBlock?.invoke(result)")
            }
        }

        functionBody
            .unindent() // try {
            .addStatement("} catch (e: Exception) {").indent()
            .addStatement("val dslResult = %T<%T>()", dslResultClassName, dslResultType)
            .addStatement("block(dslResult)")
            .addStatement("dslResult.onFailBlock?.invoke(e)")
            .unindent()
            .addStatement("} finally {").indent()
            .addStatement("%N(false)", SHOW_LOADING_VAR_NAME)
            .endControlFlow()

        funBuilder.addCode(functionBody.build())

        return funBuilder.build()
    }
}

class RetrofitDslProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return RetrofitServiceDslProcessor(
            environment.codeGenerator,
            environment.logger
        )
    }
}