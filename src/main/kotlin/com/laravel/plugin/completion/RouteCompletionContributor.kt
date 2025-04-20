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
import com.laravel.plugin.services.RouteService
import com.laravel.plugin.util.RoutePattern

class RouteCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            routeNamePattern(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.position.project
                    val routeService = project.getService(RouteService::class.java)

                    val routes = routeService.getAllRoutes()
                    for (route in routes) {
                        result.addElement(
                            LookupElementBuilder.create(route)
                                .withIcon(LaravelIcons.ROUTE)
                                .withTypeText("Route name")
                                .withTailText(" (${routeService.getRoutePath(route)})", true)
                        )
                    }
                }
            }
        )

        extend(
            CompletionType.BASIC,
            routePathPattern(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val project = parameters.position.project
                    val routeService = project.getService(RouteService::class.java)

                    val routes = routeService.getAllRoutes()
                    for (route in routes) {
                        result.addElement(
                            LookupElementBuilder.create(route)
                                .withIcon(LaravelIcons.ROUTE)
                                .withTypeText("Route path")
                                .withTailText(" (${routeService.getRoutePath(route)})", true)
                        )
                    }
                }
            }
        )
    }

    private fun routeNamePattern(): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement()
            .withParent(StringLiteralExpression::class.java)
            .with(object : PatternCondition<PsiElement>("routeReference") {
                override fun accepts(element: PsiElement, context: ProcessingContext): Boolean {
                    val parent = element.parent
                    if (parent is StringLiteralExpression) {
                        return RoutePattern.isRouteReference(parent)
                    }
                    return false
                }
            })
    }

    private fun routePathPattern(): PsiElementPattern.Capture<PsiElement> {
        return PlatformPatterns.psiElement()
            .withParent(StringLiteralExpression::class.java)
            .with(object : PatternCondition<PsiElement>("routePath") {
                override fun accepts(element: PsiElement, context: ProcessingContext): Boolean {
                    val parent = element.parent
                    if (parent is StringLiteralExpression) {
                        return RoutePattern.isRouteReference(parent)
                    }
                    return false
                }
            })
    }
}