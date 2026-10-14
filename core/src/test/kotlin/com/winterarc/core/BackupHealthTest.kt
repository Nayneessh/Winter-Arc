package com.winterarc.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupHealthTest {

    private val now = 1_760_000_000_000L
    private fun daysAgo(days: Long) = now - days * 86_400_000L

    private fun assess(
        snapshotCount: Int = 5,
        hasLocalBackupFile: Boolean = true,
        folderConfigured: Boolean = true,
        folderLastWriteMillis: Long? = daysAgo(0),
        folderConsecutiveFailures: Int = 0,
        cloudConnected: Boolean = true,
        cloudLastUploadMillis: Long? = daysAgo(0),
    ) = BackupHealth.assess(
        snapshotCount, hasLocalBackupFile, folderConfigured, folderLastWriteMillis,
        folderConsecutiveFailures, cloudConnected, cloudLastUploadMillis, now,
    )

    private fun BackupStatus.named(name: String) = protections.first { it.name == name }

    // -- the verdict ---------------------------------------------------------------------------

    @Test
    fun `nothing off the phone is the worst case and says so plainly`() {
        val status = assess(folderConfigured = false, cloudConnected = false)
        assertEquals(Verdict.AT_RISK, status.verdict)
        assertEquals(0, status.offDeviceCopies)
        assertEquals("Your training exists in one place.", status.headline)
        assertTrue(status.advice!!.contains("lost today"))
    }

    @Test
    fun `one working copy off the phone is thin, not safe`() {
        val status = assess(cloudConnected = false)
        assertEquals(Verdict.THIN, status.verdict)
        assertEquals(1, status.offDeviceCopies)
        assertEquals("Your training exists in two places.", status.headline)
    }

    @Test
    fun `two working copies off the phone is safe and asks for nothing more`() {
        val status = assess()
        assertEquals(Verdict.SAFE, status.verdict)
        assertEquals(2, status.offDeviceCopies)
        assertEquals("Your training exists in three places.", status.headline)
        assertNull(status.advice)
    }

    // -- what counts ---------------------------------------------------------------------------

    @Test
    fun `snapshots on the phone never count toward safety`() {
        // They recover a bad write. They do not survive a lost phone, and must not be allowed to
        // make the count look better than it is.
        val status = assess(
            snapshotCount = 14, folderConfigured = false, cloudConnected = false,
        )
        assertEquals(0, status.offDeviceCopies)
        assertEquals(Verdict.AT_RISK, status.verdict)
        assertFalse(status.named("On this phone").counts)
    }

    @Test
    fun `android backup is reported but not counted, because it cannot be verified`() {
        val android = assess().named("Android backup")
        assertEquals(ProtectionState.ACTIVE, android.state)
        assertFalse("An unverifiable copy must not inflate the count", android.counts)
        assertTrue(android.detail.contains("cannot read"))
    }

    // -- going quiet ---------------------------------------------------------------------------

    @Test
    fun `a folder that has stopped being written to stops counting`() {
        val status = assess(folderLastWriteMillis = daysAgo(40), cloudConnected = false)
        assertEquals(ProtectionState.STALE, status.named("Backup folder").state)
        assertEquals(0, status.offDeviceCopies)
        assertEquals(Verdict.AT_RISK, status.verdict)
        assertTrue(status.named("Backup folder").detail.contains("40 days ago"))
    }

    @Test
    fun `a folder that is failing is called failing, not merely stale`() {
        val status = assess(folderConsecutiveFailures = 3)
        assertEquals(ProtectionState.FAILING, status.named("Backup folder").state)
        assertTrue(status.advice!!.contains("failing, not merely idle"))
    }

    @Test
    fun `one failed copy is forgiven, two is a pattern`() {
        assertEquals(ProtectionState.ACTIVE, assess(folderConsecutiveFailures = 1).named("Backup folder").state)
        assertEquals(ProtectionState.FAILING, assess(folderConsecutiveFailures = 2).named("Backup folder").state)
    }

    @Test
    fun `a cloud project that has gone quiet is flagged as possibly paused`() {
        val status = assess(cloudLastUploadMillis = daysAgo(30))
        assertEquals(ProtectionState.STALE, status.named("Cloud backup").state)
        assertTrue(status.named("Cloud backup").detail.contains("paused"))
        assertTrue(status.advice!!.contains("gone quiet"))
    }

    @Test
    fun `the staleness line is exactly where it is documented`() {
        val fresh = daysAgo(BackupHealth.STALE_AFTER_DAYS)
        val stale = daysAgo(BackupHealth.STALE_AFTER_DAYS + 1)
        assertEquals(ProtectionState.ACTIVE, assess(folderLastWriteMillis = fresh).named("Backup folder").state)
        assertEquals(ProtectionState.STALE, assess(folderLastWriteMillis = stale).named("Backup folder").state)
    }

    @Test
    fun `connected but never uploaded is not treated as protection`() {
        val status = assess(cloudLastUploadMillis = null, folderConfigured = false)
        assertEquals(ProtectionState.STALE, status.named("Cloud backup").state)
        assertEquals(0, status.offDeviceCopies)
    }

    // -- the advice ----------------------------------------------------------------------------

    @Test
    fun `advice names one thing, and the folder before the cloud`() {
        // The folder is the copy that depends on nobody's server, so it is asked for first.
        val neither = assess(folderConfigured = false, cloudConnected = false)
        assertTrue(neither.advice!!.contains("backup folder"))

        val cloudOnly = assess(folderConfigured = false)
        assertTrue(cloudOnly.advice!!.contains("does not depend on anyone's server"))

        val folderOnly = assess(cloudConnected = false)
        assertTrue(folderOnly.advice!!.contains("Connect the cloud"))
    }

    @Test
    fun `a failing folder outranks every other complaint`() {
        val status = assess(folderConsecutiveFailures = 5, cloudConnected = false)
        assertTrue(status.advice!!.startsWith("Fix the backup folder"))
    }

    @Test
    fun `every layer is always reported, whatever its state`() {
        val status = assess(folderConfigured = false, cloudConnected = false, snapshotCount = 0)
        assertEquals(4, status.protections.size)
        listOf("Backup folder", "Cloud backup", "Android backup", "On this phone").forEach {
            assertNotNull(status.named(it))
        }
        status.protections.forEach { assertTrue("${it.name} has no detail", it.detail.isNotBlank()) }
    }

    @Test
    fun `a clock that runs backwards does not produce negative ages`() {
        val status = assess(folderLastWriteMillis = now + 86_400_000L)
        assertEquals(ProtectionState.ACTIVE, status.named("Backup folder").state)
        assertTrue(status.named("Backup folder").detail.contains("today"))
    }
}
