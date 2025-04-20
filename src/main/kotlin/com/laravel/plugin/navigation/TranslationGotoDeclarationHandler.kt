package com.laravel.plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.laravel.plugin.services.TranslationService
import com.laravel.plugin.util.LaravelPattern

class TranslationGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null

        // Try to get the string literal
        var stringLiteral: StringLiteralExpression? = null

        // Check if sourceElement is a part of a string literal
        if (sourceElement.parent is StringLiteralExpression) {
            stringLiteral = sourceElement.parent as StringLiteralExpression
        }
        // Check if sourceElement is the string literal itself
        else if (sourceElement is StringLiteralExpression) {
            stringLiteral = sourceElement
        }

        if (stringLiteral == null) return null

        val translationKey = stringLiteral.contents
        if (translationKey.isBlank()) return null

        if (!LaravelPattern.isTranslationReference(stringLiteral)) {
            return null
        }

        val project = sourceElement.project
        val translationService = project.getService(TranslationService::class.java)

        // Ensure translations are scanned
        if (translationService.getAllTranslations().isEmpty()) {
            translationService.scanTranslations()
        }

        val targets = translationService.findTranslationDeclarations(project, translationKey)

        // If we have multiple targets, show a popup to choose
        if (targets.size > 1 && editor != null) {
            // Create a Map for translationService.showTranslationsPopup
            val translations = mutableMapOf<String, TranslationService.TranslationInfo>()

            // For each target, add a TranslationInfo
            for (target in targets) {
                val content = (target as? StringLiteralExpression)?.contents ?: ""
                val file = target.containingFile?.virtualFile

                if (file != null) {
                    translations[content] = TranslationService.TranslationInfo(
                        key = translationKey,
                        locale = file.parent?.name ?: "en", // Use directory name as locale
                        file = file,
                        value = content,
                        element = target
                    )
                }
            }

            translationService.showTranslationsPopup(project, sourceElement, translations)
        }

        return targets.toTypedArray()
    }
}