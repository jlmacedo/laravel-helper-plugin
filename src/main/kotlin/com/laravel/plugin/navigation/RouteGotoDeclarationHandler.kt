package com.laravel.plugin.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.table.JBTable
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression
import com.laravel.plugin.services.RouteService
import com.laravel.plugin.util.LaravelPattern
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class RouteGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null || editor == null) return null

        /* ---------------- detect the string literal under the caret ---------------- */
        val stringLiteral = when (sourceElement) {
            is StringLiteralExpression -> sourceElement
            else                       -> sourceElement.parent as? StringLiteralExpression
        } ?: return null

        val routeId = stringLiteral.contents
        if (routeId.isBlank() || !LaravelPattern.isRouteReference(stringLiteral)) return null

        // Check if we're already in a route file - if so, skip navigation to prevent issues
        if (isRouteFile(sourceElement)) {
            return null
        }

        val project      = sourceElement.project
        val routeService = project.getService(RouteService::class.java)

        val declarations = routeService.findRouteDeclaration(routeId)
        val usages       = routeService.getRouteUsages(routeId)

        /* ---------------- ONLY react to Ctrl + Left‑Click ---------------- */
        if (isCtrlLeftClick()) {
            when (usages.size) {
                0 -> {} // no usages – do nothing special
                1 -> ApplicationManager.getApplication().invokeLater {
                    // jump directly to the single usage
                    usages.first().let {
                        OpenFileDescriptor(project, it.file, it.lineNumber, 0)
                            .navigate(true)
                    }
                }
                else -> ApplicationManager.getApplication().invokeLater {
                    showUsagesPopup(project, editor, routeId, usages)
                }
            }
        }

        // still return declarations so normal navigation keeps working
        return declarations.toTypedArray()
    }

    /* ---------- Helper: is the current file a route file? --------- */
    private fun isRouteFile(element: PsiElement): Boolean {
        val file = element.containingFile?.virtualFile ?: return false
        val path = file.path.toLowerCase()

        // Check if the file is in the routes directory
        return path.contains("/routes/") && path.endsWith(".php")
    }

    /* ---------- Helper: is the current AWT event a Ctrl + left‑click ? --------- */
    private fun isCtrlLeftClick(): Boolean {
        val e = IdeEventQueue.getInstance().trueCurrentEvent
        return (e is MouseEvent
                && e.id == MouseEvent.MOUSE_PRESSED
                && e.button == MouseEvent.BUTTON1
                && e.modifiersEx and InputEvent.CTRL_DOWN_MASK != 0)
    }

    /* -------------------- Popup (shown only when usages > 1) -------------------- */
    private fun showUsagesPopup(
        project: com.intellij.openapi.project.Project,
        editor: Editor,
        routeId: String,
        usages: List<RouteService.RouteUsage>
    ) {
        currentPopup?.takeIf { it.isVisible }?.cancel()          // close previous one

        val table = JBTable(UsageTableModel(usages)).apply {
            columnModel.getColumn(0).preferredWidth = 300
            columnModel.getColumn(1).preferredWidth = 50
            columnModel.getColumn(2).preferredWidth = 100
            selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        }

        currentPopup = JBPopupFactory.getInstance()
            .createPopupChooserBuilder(table)
            .setTitle("Route usages for '$routeId'")
            // tables support ONLY the Runnable callback
            .setItemChosenCallback(Runnable {
                val row = table.selectedRow
                if (row >= 0) {
                    val usage = usages[row]
                    OpenFileDescriptor(project, usage.file, usage.lineNumber, 0).navigate(true)
                }
            })
            .createPopup()
            .also { popup ->
                popup.show(
                    RelativePoint(
                        editor.contentComponent,
                        editor.contentComponent.mousePosition
                            ?: editor.visualPositionToXY(editor.caretModel.visualPosition)
                    )
                )
            }
    }

    /* --------------------------- table model --------------------------- */
    private class UsageTableModel(private val usages: List<RouteService.RouteUsage>) :
        AbstractTableModel() {
        private val headers = arrayOf("File", "Line", "Type")

        override fun getRowCount()      = usages.size
        override fun getColumnCount()   = headers.size
        override fun getColumnName(col: Int) = headers[col]

        override fun getValueAt(row: Int, col: Int): Any = when (col) {
            0 -> usages[row].file.name
            1 -> usages[row].lineNumber + 1
            2 -> if (usages[row].usageType == RouteService.UsageType.ROUTE_NAME) "name" else "path"
            else -> ""
        }
    }

    companion object {
        /** keeps the last chooser to avoid multiple pop‑ups */
        private var currentPopup: JBPopup? = null
    }
}