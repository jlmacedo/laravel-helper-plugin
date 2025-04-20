package com.laravel.plugin.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.laravel.plugin.icons.LaravelIcons
import com.laravel.plugin.services.ViewService
import com.laravel.plugin.util.ViewPattern

class ViewCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            viewPattern(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.position.project
                    val element = parameters.position.parent as? StringLiteralExpression ?: return

                    // Use the ViewPattern utility to properly check if this is a view reference
                    if (!ViewPattern.isViewReference(element)) {
                        return
                    }

                    val viewService = project.getService(ViewService::class.java)
                    val views = viewService.getAllViews()

                    val prefix = result.prefixMatcher.prefix
                    val filteredViews = views.filter { it.startsWith(prefix) }

                    filteredViews.forEach { view ->
                        result.addElement(
                            LookupElementBuilder.create(view)
                                .withIcon(LaravelIcons.VIEW)
                                .withTypeText("View")
                                .withTailText(" (${viewService.getViewPath(view)})", true)
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

    private fun viewPattern(): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement()
            .withParent(StringLiteralExpression::class.java)
            .with(object : com.intellij.patterns.PatternCondition<PsiElement>("viewReference") {
                override fun accepts(element: PsiElement, context: ProcessingContext): Boolean {
                    val parent = element.parent
                    if (parent is StringLiteralExpression) {
                        return ViewPattern.isViewReference(parent)
                    }
                    return false
                }
            })
    }
}