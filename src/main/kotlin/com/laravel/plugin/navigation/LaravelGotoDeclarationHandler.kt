package com.laravel.plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.laravel.plugin.services.AssetService
import com.laravel.plugin.services.RouteService
import com.laravel.plugin.services.TranslationService
import com.laravel.plugin.services.ViewService
import com.laravel.plugin.util.LaravelPattern

class LaravelGotoDeclarationHandler : GotoDeclarationHandler {

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

        val literalValue = stringLiteral.contents

        // Skip empty strings
        if (literalValue.isBlank()) return null

        val project = sourceElement.project

        return when {
            LaravelPattern.isRouteReference(stringLiteral) -> {
                // Check if we're already in a route file - if so, skip navigation to prevent issues
                if (isRouteFile(sourceElement)) {
                    null
                } else {
                    val routeService = project.getService(RouteService::class.java)
                    routeService.findRouteDeclaration(literalValue).toTypedArray()
                }
            }

            LaravelPattern.isTranslationReference(stringLiteral) -> {
                val translationService = project.getService(TranslationService::class.java)
                val targets = translationService.findTranslationDeclaration(project, literalValue)

                // If we have multiple targets, show a popup to choose
                if (targets.size > 1 && editor != null) {
                    // Create a Map for translationService.showTranslationsPopup
                    val translations = mutableMapOf<String, TranslationService.TranslationInfo>()

                    // For each target, add a TranslationInfo
                    for (target in targets) {
                        val content = (target as? StringLiteralExpression)?.contents ?: ""
                        val file = target.containingFile?.virtualFile

                        if (file != null) {
                            // Use the companion object to make it clear we're calling the constructor
                            translations[content] = TranslationService.TranslationInfo(
                                key = literalValue,
                                locale = "en", // Default locale
                                file = file,
                                value = content,
                                element = target
                            )
                        }
                    }

                    translationService.showTranslationsPopup(project, sourceElement, translations)
                }

                targets.toTypedArray()
            }

            LaravelPattern.isViewReference(stringLiteral) -> {
                val viewService = project.getService(ViewService::class.java)
                viewService.findViewDeclaration(literalValue).toTypedArray()
            }

            LaravelPattern.isAssetReference(stringLiteral) -> {
                val assetService = project.getService(AssetService::class.java)
                assetService.findAssetDeclaration(literalValue).toTypedArray()
            }

            else -> null
        }
    }

    /* ---------- Helper: is the current file a route file? --------- */
    private fun isRouteFile(element: PsiElement): Boolean {
        val file = element.containingFile?.virtualFile ?: return false
        val path = file.path.toLowerCase()

        // Check if the file is in the routes directory
        return path.contains("/routes/") && path.endsWith(".php")
    }
}