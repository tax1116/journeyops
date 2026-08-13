package kr.co.taek.dev.build

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the

const val JDK_VERSION = "21"

inline val Project.libs: LibrariesForLibs
    get() = the<LibrariesForLibs>()
