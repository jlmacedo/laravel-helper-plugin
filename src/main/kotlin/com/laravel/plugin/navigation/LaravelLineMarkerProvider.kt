package com.laravel.plugin.navigation

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.table.JBTable
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.laravel.plugin.icons.LaravelIcons
import com.laravel.plugin.services.AssetService
import com.laravel.plugin.services.RouteService
import com.laravel.plugin.services.TranslationService
import com.laravel.plugin.services.ViewService
import com.laravel.plugin.util.LaravelPattern
import java.awt.Point
import javax.swing.table.AbstractTableModel

class LaravelLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is StringLiteralExpression) return null

        val project = element.project
        val literalValue = element.contents

        return when {
            LaravelPattern.isRouteReference(element) -> {
                val routeService = project.getService(RouteService::class.java)
                val usages = routeService.getRouteUsages(literalValue)

                if (usages.isNotEmpty()) {
                    object : LineMarkerInfo<PsiElement>(
                        element,
                        element.textRange,
                        LaravelIcons.ROUTE,
                        { "Show route usages" },
                        { _, elt ->
                            showUsagesPopup(elt, usages)
                        },
                        GutterIconRenderer.Alignment.CENTER,
                        { "Route usages" }
                    ) {}
                } else null
            }
            LaravelPattern.isTranslationReference(element) -> {
                val translationService = project.getService(TranslationService::class.java)
                val targets = translationService.findTranslationDeclaration(project, literalValue)

                if (targets.size > 1) {
                    object : LineMarkerInfo<PsiElement>(
                        element,
                        element.textRange,
                        LaravelIcons.TRANSLATION,
                        { "View all translations" },
                        { _, _ ->
                            val translations = mutableMapOf<String, TranslationService.TranslationInfo>()
                            for (target in targets) {
                                val content = (target as? StringLiteralExpression)?.contents ?: ""
                                val file = target.containingFile?.virtualFile
                                if (file != null) {
                                    translations[content] = TranslationService.TranslationInfo(
                                        key = literalValue,
                                        locale = "en",
                                        file = file,
                                        value = content,
                                        element = target
                                    )
                                }
                            }
                            translationService.showTranslationsPopup(project, element, translations)
                        },
                        GutterIconRenderer.Alignment.CENTER,
                        { "Translation definitions" }
                    ) {}
                } else null
            }
            else -> null
        }
    }

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        for (element in elements) {
            if (element !is StringLiteralExpression) continue

            val project = element.project
            val literalValue = element.contents

            when {
                LaravelPattern.isTranslationReference(element) -> {
                    val translationService = project.getService(TranslationService::class.java)
                    val targets = translationService.findTranslationDeclaration(project, literalValue)
                    if (targets.size == 1) {
                        val builder = NavigationGutterIconBuilder.create(LaravelIcons.TRANSLATION)
                            .setTargets(targets.toList())
                            .setTooltipText("Go to translation definition")
                        result.add(builder.createLineMarkerInfo(element))
                    }
                }
                LaravelPattern.isViewReference(element) -> {
                    val viewService = project.getService(ViewService::class.java)
                    val targets = viewService.findViewDeclaration(literalValue)
                    if (targets.isNotEmpty()) {
                        val builder = NavigationGutterIconBuilder.create(LaravelIcons.VIEW)
                            .setTargets(targets.toList())
                            .setTooltipText("Go to view file")
                        result.add(builder.createLineMarkerInfo(element))
                    }
                }
                LaravelPattern.isAssetReference(element) -> {
                    val assetService = project.getService(AssetService::class.java)
                    val targets = assetService.findAssetDeclaration(literalValue)
                    if (targets.isNotEmpty()) {
                        val builder = NavigationGutterIconBuilder.create(LaravelIcons.ASSET)
                            .setTargets(targets.toList())
                            .setTooltipText("Go to asset file")
                        result.add(builder.createLineMarkerInfo(element))
                    }
                }
            }
        }
    }

    private fun showUsagesPopup(element: PsiElement, usages: List<RouteService.RouteUsage>) {
        val model = UsageTableModel(usages)
        val table = JBTable(model)

        table.setShowColumns(true)
        table.columnModel.getColumn(0).preferredWidth = 200
        table.columnModel.getColumn(1).preferredWidth = 50
        table.columnModel.getColumn(2).preferredWidth = 100

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(table)
            .setTitle("Route Usages")
            // disambiguate Consumer vs Runnable
            .setItemChosenCallback { _: Any ->
                val selectedRow = table.selectedRow
                if (selectedRow >= 0) {
                    val usage = usages[selectedRow]
                    val project = element.project
                    val descriptor = OpenFileDescriptor(project, usage.file, usage.lineNumber, 0)
                    FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                }
            }
            .createPopup()
            .show(RelativePoint(Point(0, 0)))
    }

    private class UsageTableModel(private val usages: List<RouteService.RouteUsage>) :
        AbstractTableModel() {

        private val columnNames = arrayOf("File", "Line", "Type")

        override fun getRowCount(): Int = usages.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(row: Int, col: Int): Any {
            val usage = usages[row]
            return when (col) {
                0 -> usage.file.name
                1 -> (usage.lineNumber + 1).toString()
                2 -> if (usage.usageType == RouteService.UsageType.ROUTE_NAME) "name" else "path"
                else -> ""
            }
        }
    }
}
