package org.jetbrains.intellij.jbr

import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.Project
import org.jetbrains.annotations.NotNull

class DownloadActionWrapper extends DownloadAction {
    DownloadActionWrapper(@NotNull Project project, @NotNull String url, @NotNull String destination) {
        super(project)
        src(url)
        dest(destination)
    }

    @Override
    void execute() throws IOException {
        def destFile = getDest()
        if (destFile == null || !destFile.exists()) {
            def tempFile = new File(destFile.absolutePath + ".part")
            dest(tempFile)
            overwrite(true)
            onlyIfNewer(false)
            super.execute()
            dest(destFile)
            tempFile.renameTo(destFile)
        }
    }
}