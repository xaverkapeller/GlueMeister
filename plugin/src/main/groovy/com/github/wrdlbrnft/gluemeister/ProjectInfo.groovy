package com.github.wrdlbrnft.gluemeister

import org.gradle.api.ProjectConfigurationException

/**
 *  Created with IntelliJ IDEA<br>
 *  User: Xaver<br>
 *  Date: 28/01/2017
 */
class ProjectInfo {

    static ProjectInfo analyzeProject(project) {
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android") ||
                project.plugins.findPlugin("com.android.test")) {
            return new ProjectInfo("applicationVariants", false)
        }

        if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            return new ProjectInfo("libraryVariants", true)
        }

        throw new ProjectConfigurationException("The android or android-library plugin must be applied to the project", null)
    }

    final String variants;
    final boolean library;
    final String rootPackageName;

    private ProjectInfo(String variants, boolean library) {
        this.variants = variants
        this.library = library
        this.rootPackageName = rootPackageName
    }
}
