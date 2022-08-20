package com.ulyp.ui.looknfeel

import com.ulyp.ui.elements.controls.ErrorPopupView
import com.ulyp.ui.elements.misc.ExceptionAsTextView
import javafx.scene.Scene
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

@Component
class FontSizeChanger(private val applicationContext: ApplicationContext) {

    companion object {
        private const val STYLE_PREFIX = "call-tree-font-style"
    }

    fun refresh(scene: Scene, fontSize: Double, fontName: String) {
        try {

            val path = Files.createTempFile(STYLE_PREFIX, null)
            path.toFile().deleteOnExit()
            Files.write(
                    path,
                    """
                .ulyp-call-tree {
                    -fx-font-family: ${fontName};
                    -fx-font-size: ${fontSize}em;
                }
                .ulyp-tree-view {
                    -fx-fixed-cell-size: ${fontSize * 2.0}em;
                }
                .ulyp-call-tree-identity-hash-code {
                    -fx-font-size: ${fontSize * 0.8}em;
                }
                """.toByteArray(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE
            )
            var index: Int? = null
            for (i in scene.stylesheets.indices) {
                if (scene.stylesheets[i].contains(STYLE_PREFIX)) {
                    index = i
                    break
                }
            }
            if (index != null) {
                scene.stylesheets[index] = path.toFile().toURI().toString()
            } else {
                scene.stylesheets.add(path.toFile().toURI().toString())
            }
        } catch (e: IOException) {

            val errorPopup = applicationContext.getBean(
                    ErrorPopupView::class.java,
                    "Could not change font size: " + e.message,
                    ExceptionAsTextView(e)
            )
            errorPopup.show()
        }
    }
}