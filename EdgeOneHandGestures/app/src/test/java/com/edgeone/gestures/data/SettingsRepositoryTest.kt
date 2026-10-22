package com.edgeone.gestures.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.edgeone.gestures.core.ActionType
import com.edgeone.gestures.core.AppSettings
import com.edgeone.gestures.core.EdgeSide
import com.edgeone.gestures.core.GestureAction
import com.edgeone.gestures.core.GestureBinding
import com.edgeone.gestures.core.GestureType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Real DataStore on a temp file: verifies settings survive "restarting" the repository. */
class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun newRepo(file: File, scope: CoroutineScope) =
        SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test fun settingsPersistAcrossRepositoryInstances() = runBlocking {
        val file = File(tmp.root, "settings.preferences_pb")

        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo1 = newRepo(file, scope1)
        repo1.update {
            it.withEdge(
                EdgeSide.LEFT,
                it.left.copy(
                    widthDp = 28,
                    gestures = it.left.gestures + (GestureType.UP to GestureBinding(true, GestureAction(ActionType.SCREENSHOT))),
                ),
            ).copy(masterEnabled = false)
        }
        scope1.cancel() // simulate process death

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val loaded = newRepo(file, scope2).settings.first()
        scope2.cancel()

        assertEquals(28, loaded.left.widthDp)
        assertEquals(ActionType.SCREENSHOT, loaded.left.binding(GestureType.UP).action.type)
        assertEquals(ActionType.LOCK_SCREEN, loaded.right.binding(GestureType.UP).action.type)
        assertEquals(false, loaded.masterEnabled)
    }

    @Test fun freshStoreYieldsDefaults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val loaded = newRepo(File(tmp.root, "fresh.preferences_pb"), scope).settings.first()
        scope.cancel()
        assertEquals(AppSettings().sanitized(), loaded)
    }

    @Test fun resetRestoresDefaults() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = newRepo(File(tmp.root, "reset.preferences_pb"), scope)
        repo.update { it.copy(showZones = true) }
        repo.resetToDefaults()
        val loaded = repo.settings.first()
        scope.cancel()
        assertEquals(AppSettings().sanitized(), loaded)
    }
}
