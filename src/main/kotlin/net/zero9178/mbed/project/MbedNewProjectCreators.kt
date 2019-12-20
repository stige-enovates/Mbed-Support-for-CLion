package net.zero9178.mbed.project

import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.writeChild
import com.jetbrains.cidr.cpp.cmake.projectWizard.generators.CLionProjectGenerator
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeWorkspace
import icons.MbedIcons
import net.zero9178.mbed.MbedNotification
import net.zero9178.mbed.ModalTask
import net.zero9178.mbed.packages.changeTarget
import net.zero9178.mbed.packages.changeTargetDialog
import net.zero9178.mbed.state.MbedState
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteException
import org.apache.commons.exec.PumpStreamHandler
import java.io.File
import java.io.OutputStream
import javax.swing.Icon

/**
 * Instantiated when creating a new project with the mbed-os wizard
 */
class MbedNewProjectCreators : CLionProjectGenerator<Any>() {

    override fun generateProject(project: Project, virtualFile: VirtualFile, settings: Any, module: Module) {
        ProgressManager.getInstance().run(
            ModalTask(
                project,
                "Creating new Mbed os project",
                {
                    it.isIndeterminate = true
                    val cli = MbedState.getInstance().cliPath
                    val cl = CommandLine.parse("$cli new -vv .")
                    val exec = DefaultExecutor()
                    exec.workingDirectory = File(virtualFile.path)
                    var output = ""
                    exec.streamHandler = PumpStreamHandler(object : OutputStream() {
                        private var flush = false
                        override fun write(b: Int) {
                            output += b.toChar()
                            if (b.toChar() != '\n') {
                                if (flush) {
                                    flush = false
                                    it.text = ""
                                }
                                it.text += b.toChar()
                            } else {
                                flush = true
                            }
                        }
                    })

                    try {
                        exec.execute(cl)
                    } catch (e: ExecuteException) {
                        Notifications.Bus.notify(
                            MbedNotification.GROUP_DISPLAY_ID_INFO.createNotification(
                                "mbed failed with exit code ${e.exitValue}\n Output: $output",
                                NotificationType.ERROR
                            )
                        )
                    }
                }) {
                changeTargetDialog(project)?.let { changeTarget(it, project) }
                virtualFile.writeChild(
                    "main.cpp",
                    """#include <mbed.h>

int main()
{

}

"""
                )
                virtualFile.writeChild(
                    "project.cmake",
                    """
set(OWN_SOURCES main.cpp)
target_sources(${project.name} PUBLIC ${"$"}{OWN_SOURCES})
set_source_files_properties(${"$"}{OWN_SOURCES} PROPERTIES COMPILE_DEFINITIONS MBED_NO_GLOBAL_USING_DIRECTIVE)

"""
                )
                CMakeWorkspace.getInstance(project).selectProjectDir(project.basePath?.let { File(it) })
            })
    }

    override fun getName(): String = "mbed-os"

    override fun getLogo(): Icon? = MbedIcons.MBED_ICON_16x16

    override fun getGroupName() = "mbed"
}

