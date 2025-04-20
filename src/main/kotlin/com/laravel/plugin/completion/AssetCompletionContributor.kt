package com.laravel.plugin.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.laravel.plugin.icons.LaravelIcons
import com.laravel.plugin.services.AssetService
import com.laravel.plugin.util.AssetPattern

class AssetCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            assetPattern(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.position.project
                    val element = parameters.position.parent as? StringLiteralExpression ?: return

                    // Use the AssetPattern utility to properly check if this is an asset reference
                    if (!AssetPattern.isAssetReference(element)) {
                        return
                    }

                    val assetService = project.getService(AssetService::class.java)
                    val assets = assetService.getAllAssets()

                    val prefix = result.prefixMatcher.prefix
                    val filteredAssets = assets.filter { it.startsWith(prefix) }

                    filteredAssets.forEach { asset ->
                        val icon = when (assetService.getAssetType(asset)) {
                            AssetService.AssetType.CSS -> com.intellij.icons.AllIcons.FileTypes.Css
                            AssetService.AssetType.JS -> com.intellij.icons.AllIcons.FileTypes.JavaScript
                            AssetService.AssetType.IMAGE -> com.intellij.icons.AllIcons.FileTypes.Image
                            AssetService.AssetType.FONT -> com.intellij.icons.AllIcons.FileTypes.Font
                            else -> com.intellij.icons.AllIcons.FileTypes.Text
                        }

                        result.addElement(
                            LookupElementBuilder.create(asset)
                                .withTypeText("Asset")
                                .withIcon(icon)
                                .withTailText(" (${assetService.getAssetPath(asset)})", true)
                                .withInsertHandler { context, _ ->
                                    // Add quotes if needed
                                    val text = context.document.text
                                    if (context.startOffset > 0 && text[context.startOffset - 1] != '\'') {
                                        context.document.insertString(context.startOffset, "'")
                                        context.document.insertString(context.tailOffset, "'")
                                    }
                                }
                        )
                    }
                }
            }
        )
    }

    private fun assetPattern(): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement()
            .withParent(StringLiteralExpression::class.java)
    }
}