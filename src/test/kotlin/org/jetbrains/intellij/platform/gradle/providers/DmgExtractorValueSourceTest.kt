// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import com.dd.plist.PropertyListParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DmgExtractorValueSourceTest {

    @Test
    fun `select mounted device instead of partition type GUID`() {
        val plist = plist(
            """
            <dict>
                <key>autodiskmount</key>
                <true/>
                <key>blockcount</key>
                <integer>123456</integer>
                <key>image-path</key>
                <string>/downloads/idea.dmg</string>
                <key>system-entities</key>
                <array>
                    <dict>
                        <key>dev-entry</key>
                        <string>/dev/disk5</string>
                        <key>content-hint</key>
                        <string>GUID_partition_scheme</string>
                    </dict>
                    <dict>
                        <key>dev-entry</key>
                        <string>/dev/disk5s1</string>
                        <key>content-hint</key>
                        <string>EFI</string>
                    </dict>
                    <dict>
                        <key>dev-entry</key>
                        <string>/dev/disk5s2</string>
                        <key>content-hint</key>
                        <string>48465300-0000-11AA-AA11-00306543ECAC</string>
                        <key>mount-point</key>
                        <string>/private/var/folders/idea</string>
                    </dict>
                </array>
            </dict>
            """.trimIndent(),
        )

        assertEquals("/dev/disk5s2", plist.findDetachTarget("/downloads/idea.dmg"))
    }

    @Test
    fun `match the requested image path exactly`() {
        val plist = plist(
            image("/downloads/other.dmg", "/dev/disk4s1", "/Volumes/Other"),
            image("/downloads/idea.dmg", "/dev/disk5s2", "/Volumes/IDEA"),
        )

        assertEquals("/dev/disk5s2", plist.findDetachTarget("/downloads/idea.dmg"))
        assertNull(plist.findDetachTarget("/downloads/missing.dmg"))
    }

    @Test
    fun `fall back to the first device for an unmounted image`() {
        val plist = plist(
            """
            <dict>
                <key>image-path</key>
                <string>/downloads/idea.dmg</string>
                <key>system-entities</key>
                <array>
                    <dict>
                        <key>dev-entry</key>
                        <string>/dev/disk7</string>
                        <key>content-hint</key>
                        <string>GUID_partition_scheme</string>
                    </dict>
                    <dict>
                        <key>dev-entry</key>
                        <string>/dev/disk7s1</string>
                        <key>content-hint</key>
                        <string>EFI</string>
                    </dict>
                </array>
            </dict>
            """.trimIndent(),
        )

        assertEquals("/dev/disk7", plist.findDetachTarget("/downloads/idea.dmg"))
    }

    @Test
    fun `fall back to the mount point when its device is absent`() {
        val plist = plist(
            """
            <dict>
                <key>image-path</key>
                <string>/downloads/idea.dmg</string>
                <key>system-entities</key>
                <array>
                    <dict>
                        <key>mount-point</key>
                        <string>/Volumes/IDEA</string>
                    </dict>
                </array>
            </dict>
            """.trimIndent(),
        )

        assertEquals("/Volumes/IDEA", plist.findDetachTarget("/downloads/idea.dmg"))
    }

    @Test
    fun `ignore empty and incomplete image entries`() {
        val emptyPlist = plist()
        val incompletePlist = plist(
            """
            <dict>
                <key>image-path</key>
                <string>/downloads/idea.dmg</string>
                <key>system-entities</key>
                <array>
                    <dict>
                        <key>content-hint</key>
                        <string>48465300-0000-11AA-AA11-00306543ECAC</string>
                    </dict>
                </array>
            </dict>
            """.trimIndent(),
        )

        assertNull(emptyPlist.findDetachTarget("/downloads/idea.dmg"))
        assertNull(incompletePlist.findDetachTarget("/downloads/idea.dmg"))
    }

    private fun String.findDetachTarget(imagePath: String) =
        PropertyListParser.parse(byteInputStream()).findDetachTarget(imagePath)

    private fun plist(vararg images: String) =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        <plist version="1.0">
            <dict>
                <key>framework</key>
                <string>683.160.3</string>
                <key>images</key>
                <array>
                    ${images.joinToString("\n")}
                </array>
                <key>revision</key>
                <string>683.160.3</string>
            </dict>
        </plist>
        """.trimIndent().trimStart()

    private fun image(path: String, device: String, mountPoint: String) =
        """
        <dict>
            <key>image-path</key>
            <string>$path</string>
            <key>system-entities</key>
            <array>
                <dict>
                    <key>dev-entry</key>
                    <string>$device</string>
                    <key>mount-point</key>
                    <string>$mountPoint</string>
                </dict>
            </array>
        </dict>
        """.trimIndent()
}
