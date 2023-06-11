package com.ulyp.ui.elements.recording.tree

import com.ulyp.core.ProcessMetadata
import com.ulyp.storage.CallRecord
import com.ulyp.storage.Recording
import com.ulyp.ui.RenderSettings
import com.ulyp.ui.code.SourceCode
import com.ulyp.ui.code.SourceCodeView
import com.ulyp.ui.code.find.SourceCodeFinder
import com.ulyp.ui.looknfeel.FontSizeUpdater
import com.ulyp.ui.settings.Settings
import javafx.application.Platform
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

/**
 * A tab which contains a particular recording (i.e. particular recorded method call including
 * all its nested calls)
 */
@Component
@Scope(value = "prototype")
class RecordingTab(
        private val parent: Region,
        private val processMetadata: ProcessMetadata,
        private val recording: Recording
) : VBox() {

    val recordingId = recording.id
    private var root: CallRecord? = null
    private var treeView: TreeView<RecordedCallNodeContent>? = null

    @Autowired
    private lateinit var sourceCodeView: SourceCodeView
    @Autowired
    private lateinit var renderSettings: RenderSettings
    @Autowired
    private lateinit var settings: Settings
    @Autowired
    private lateinit var fontSizeUpdater: FontSizeUpdater

    private var initialized = false

    @Synchronized
    fun init() {
        if (initialized) {
            return
        }

        treeView = TreeView(RecordedCallTreeItem(recording, root!!.id, renderSettings))
        treeView!!.styleClass += "ulyp-call-tree-view"

        treeView!!.prefHeightProperty().bind(parent.heightProperty())
        treeView!!.prefWidthProperty().bind(parent.widthProperty())

        val sourceCodeFinder = SourceCodeFinder(processMetadata.classPathFiles)
        treeView!!.selectionModel.selectedItemProperty()
                .addListener { observable: ObservableValue<out TreeItem<RecordedCallNodeContent>?>?, oldValue: TreeItem<RecordedCallNodeContent>?, newValue: TreeItem<RecordedCallNodeContent>? ->
                    val selectedNode = newValue as RecordedCallTreeItem?
                    if (selectedNode?.callRecord != null) {
                        val sourceCodeFuture = sourceCodeFinder.find(
                                selectedNode.callRecord.method.declaringType.name
                        )
                        sourceCodeFuture.thenAccept { sourceCode: SourceCode? ->
                            Platform.runLater {
                                val currentlySelected = treeView!!.selectionModel.selectedItem
                                val currentlySelectedNode = currentlySelected as RecordedCallTreeItem
                                if (selectedNode.callRecord.id == currentlySelectedNode.callRecord.id) {
                                    sourceCodeView.setText(sourceCode, currentlySelectedNode.callRecord.method.name)
                                }
                            }
                        }
                    }
                }
        treeView!!.onKeyPressed = EventHandler { key: KeyEvent ->
            if (key.code == KeyCode.EQUALS) {
                settings.recordingTreeFontSize.value += 1
            }
            if (key.code == KeyCode.MINUS) {
                settings.recordingTreeFontSize.value -= 1
            }
        }

        children.add(treeView)
        // TODO tooltip = tooltipText
        initialized = true
    }

    fun getSelected(): RecordedCallTreeItem {
        return treeView!!.selectionModel.selectedItem as RecordedCallTreeItem
    }

    fun dispose() {
    }

    @Synchronized
    fun refreshTreeView() {
        init()
        val root = treeView!!.root as RecordedCallTreeItem
        root.refresh()
    }

    @Synchronized
    fun update(recording: Recording) {
        if (root == null) {
            root = recording.root
        }
    }
}