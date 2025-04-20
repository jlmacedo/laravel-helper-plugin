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
import com.laravel.plugin.services.TranslationService
import com.laravel.plugin.util.LaravelPattern

class TranslationCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            translationPattern(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.position.project
                    val element = parameters.position.parent as? StringLiteralExpression ?: return
                    
                    // Only provide completion if we're in a translation context
                    if (!LaravelPattern.isTranslationReference(element)) {
                        return
                    }
                    
                    val translationService = project.getService(TranslationService::class.java)
                    
                    // Ensure translations are scanned
                    if (translationService.getAllTranslations().isEmpty()) {
                        translationService.scanTranslations()
                    }
                    
                    val translations = translationService.getAllTranslations()
                    
                    // Get the current input text and create a prefix matcher
                    val currentInput = element.contents
                    val prefixMatcher = result.prefixMatcher.cloneWithPrefix(currentInput)
                    
                    // Create a filtered result set with the prefix
                    val filteredResult = result.withPrefixMatcher(prefixMatcher)
                    
                    for (translation in translations) {
                        if (prefixMatcher.prefixMatches(translation)) {
                            val value = translationService.getTranslationValue(translation)
                            val locale = translationService.getTranslationLocale(translation)
                            
                            filteredResult.addElement(
                                LookupElementBuilder.create(translation)
                                    .withIcon(LaravelIcons.TRANSLATION)
                                    .withTypeText("Translation [$locale]")
                                    .withTailText(" ($value)", true)
                                    .withPresentableText(translation)
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
            }
        )
    }

    private fun translationPattern(): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement()
            .withParent(StringLiteralExpression::class.java)
            .with(object : PatternCondition<PsiElement>("translationReference") {
                override fun accepts(element: PsiElement, context: ProcessingContext): Boolean {
                    val parent = element.parent
                    if (parent is StringLiteralExpression) {
                        return LaravelPattern.isTranslationReference(parent)
                    }
                    return false
                }
            })
    }
}