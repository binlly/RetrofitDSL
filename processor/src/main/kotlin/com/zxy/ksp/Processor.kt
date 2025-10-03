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
 * Created by y on 2025/10/3-20:35
 *
 * @author y >_
 * .     _                                           ____  _       _ _
 * .    / \__      _____  ___  ___  _ __ ___   ___  | __ )(_)_ __ | | |_   _
 * .   / _ \ \ /\ / / _ \/ __|/ _ \| '_ ` _ \ / _ \ |  _ \| | '_ \| | | | | |
 * .  / ___ \ V  V /  __/\__ \ (_) | | | | | |  __/ | |_) | | | | | | | |_| |
 * . /_/   \_\_/\_/ \___||___/\___/|_| |_| |_|\___| |____/|_|_| |_|_|_|\__, |
 * .                                                                   |___/
 */

class DslRetrofitSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    companion object {
        // 自定义注解的类名
        const val RETROFIT_SERVICE_ANNOTATION = "com.zxy.ksp.RetrofitService"
    }

    // 用于跟踪已经处理过的服务接口，避免重复处理
    private val processedServices = mutableSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("DslRetrofitSymbolProcessor: process() called")

        // 查找所有带有 @RetrofitService 注解的类（接口）
        val serviceDeclarations = resolver.getSymbolsWithAnnotation(RETROFIT_SERVICE_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE } // 确保是接口

        val newServicesToProcess = serviceDeclarations.filter { service ->
            val qualifiedName = service.qualifiedName?.asString()
            // 检查接口是否已经被处理过
            qualifiedName != null && !processedServices.contains(qualifiedName)
        }

        logger.info("Found ${newServicesToProcess.count()} new @RetrofitService annotated interfaces to process")

        val unprocessedSymbols = mutableListOf<KSAnnotated>()

        newServicesToProcess.forEach { service ->
            val qualifiedName = service.qualifiedName?.asString()
            if (qualifiedName != null) {
                processedServices.add(qualifiedName)
            }

            // 尝试处理服务接口
            val isValid = service.validate()
            if (isValid) {
                logger.info("Processing service: $qualifiedName")
                generateDslExtensions(service)
            } else {
                logger.warn("Service $qualifiedName failed validation, adding back to unprocessed list.")
                // 如果验证失败，将其添加回未处理列表，以便下一轮再尝试
                unprocessedSymbols.add(service)
            }
        }

        // 返回未处理或验证失败的符号
        return unprocessedSymbols
    }

    private fun generateDslExtensions(service: KSClassDeclaration) {
        val packageName = service.packageName.asString()
        val serviceName = service.simpleName.asString()
        val dslExtensionClassName = "${serviceName}DslExtensions"

        val fileBuilder = FileSpec.builder(packageName, dslExtensionClassName)

        // 遍历接口中的所有函数（这些函数现在都隐含是 Retrofit 方法）
        service.getAllFunctions().forEach { function ->
            val extensionFunSpec = generateExtensionFunction(packageName, function, service)
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

    private fun generateExtensionFunction(
        packageName: String,
        function: KSFunctionDeclaration,
        service: KSClassDeclaration
    ): FunSpec {
        val functionName = function.simpleName.asString()
        val returnType = function.returnType?.resolve()
            ?: throw IllegalArgumentException("Function $functionName must have a return type")

        // 解析返回类型 T (对于 suspend T, Call<T>, Response<T>)
        val isCallType = returnType.declaration.qualifiedName?.asString() == "retrofit2.Call"
        val isResponseType =
            returnType.declaration.qualifiedName?.asString() == "retrofit2.Response"
        val isSuspend = function.modifiers.contains(Modifier.SUSPEND)

        var dslResultType: TypeName = returnType.toTypeName() // 默认为整个返回类型
        if (isCallType && returnType.arguments.isNotEmpty()) {
            dslResultType = returnType.arguments[0].type!!.resolve().toTypeName()
        } else if (isResponseType && returnType.arguments.isNotEmpty()) {
            dslResultType = returnType.arguments[0].type!!.resolve().toTypeName()
        }
        // 对于 suspend 函数，我们假设其直接返回 T 或 Response<T>，这里 dslResultType 已经是 T

        // 创建扩展函数
        val funBuilder = FunSpec.builder(functionName)
            .receiver(
                ClassName(
                    packageName,
                    "RetrofitServiceDsl"
                )
            ) // 扩展函数接收者是 RetrofitServiceDsl

        // 添加原始函数的参数
        function.parameters.forEach { param ->
            val paramType = param.type.resolve().toTypeName()
            val paramName = param.name?.asString() ?: "param"
            funBuilder.addParameter(paramName, paramType)
        }

        // 添加 DSL Result 配置块参数
        val dslResultTypeName =
            ClassName(packageName, "DslResult").parameterizedBy(dslResultType)
        funBuilder.addParameter(
            ParameterSpec.builder(
                "block", LambdaTypeName.get(
                    receiver = dslResultTypeName,
                    returnType = UNIT
                )
            ).build()
        )

        // 生成函数体
        // 1. 获取服务实例
        funBuilder.addStatement("val service = this.retrofitService as %T", service.toClassName())
        // 2. 调用 loading
        funBuilder.addStatement("showLoading(true)")
        funBuilder.addStatement("try {")
        funBuilder.addStatement(
            "    val result = service.%N(%L)",
            functionName,
            function.parameters.joinToString(", ") { it.name?.asString() ?: "param" })

        if (isSuspend) {
            // 对于 suspend 函数，需要在协程中调用并处理
            // 假设有一个 suspend 版本的 DslResult 或者将 suspend 转换为 Call
            // 为简化，这里假设 suspend 函数直接返回数据 T
            // **注意：这里需要一个协程作用域，生成的代码无法直接运行，需要调用方提供**
            funBuilder.addStatement("    // Suspend function handling requires a CoroutineScope") // 添加注释说明
            funBuilder.addStatement("    // This requires a different DSL structure or a wrapper to convert to Call") // 添加注释说明
            funBuilder.addStatement("    val dslResult = DslResult<%T>()", dslResultType)
            funBuilder.addStatement("    block(dslResult)")
            // 由于 suspend 不能直接同步调用，这里只是一个占位符
            funBuilder.addStatement("    // Placeholder for suspend call: dslResult.onSuccessBlock?.invoke(/* suspend result */)")
        } else {
            // 对于非 suspend (Call/Response) 函数
            if (isCallType) {
                funBuilder.addStatement("    val dslResult = DslResult<%T>()", dslResultType)
                funBuilder.addStatement("    block(dslResult)")
                funBuilder.beginControlFlow(
                    "    result.enqueue(object : retrofit2.Callback<%T> {",
                    dslResultType
                )
                funBuilder.beginControlFlow(
                    "        override fun onResponse(call: retrofit2.Call<%T>, response: retrofit2.Response<%T>)",
                    dslResultType,
                    dslResultType
                )
                funBuilder.beginControlFlow("            if (response.isSuccessful)")
                funBuilder.addStatement("                dslResult.onSuccessBlock?.invoke(response.body()!!)")
                funBuilder.nextControlFlow("            else")
                funBuilder.addStatement("                dslResult.onFailBlock?.invoke(Exception(\"HTTP Error: \${response.code()} - \${response.message()}\"))")
                funBuilder.endControlFlow() // if
                funBuilder.endControlFlow() // onResponse
                funBuilder.beginControlFlow(
                    "        override fun onFailure(call: retrofit2.Call<%T>, t: Throwable)",
                    dslResultType
                )
                funBuilder.addStatement("            dslResult.onFailBlock?.invoke(t)")
                funBuilder.endControlFlow() // onFailure
                funBuilder.endControlFlow() // Callback
                funBuilder.addStatement(")")
            } else if (isResponseType) {
                // 对于 Response<T> 类型（非 suspend）
                funBuilder.addStatement("    val dslResult = DslResult<%T>()", dslResultType)
                funBuilder.addStatement("    block(dslResult)")
                funBuilder.beginControlFlow("    if (result.isSuccessful)")
                funBuilder.addStatement("        dslResult.onSuccessBlock?.invoke(result.body()!!)")
                funBuilder.nextControlFlow("    else")
                funBuilder.addStatement("        dslResult.onFailBlock?.invoke(Exception(\"HTTP Error: \${result.code()} - \${result.message()}\"))")
                funBuilder.endControlFlow() // if
            } else {
                // 对于原始类型 T (非 Call, 非 Response, 非 suspend - 这种情况在 Retrofit 中不常见)
                funBuilder.addStatement("    val dslResult = DslResult<%T>()", dslResultType)
                funBuilder.addStatement("    block(dslResult)")
                funBuilder.addStatement("    dslResult.onSuccessBlock?.invoke(result)")
            }
        }

        funBuilder.addStatement("} catch (e: Exception) {")
        funBuilder.addStatement(
            "    val dslResult = DslResult<%T>()",
            dslResultType
        )
        funBuilder.addStatement("    block(dslResult)")
        funBuilder.addStatement("    dslResult.onFailBlock?.invoke(e)")
        funBuilder.addStatement("} finally {")
        funBuilder.addStatement("    showLoading(false)")
        funBuilder.addStatement("}")

        return funBuilder.build()
    }
}

class DslRetrofitProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return DslRetrofitSymbolProcessor(
            environment.codeGenerator,
            environment.logger
        )
    }
}