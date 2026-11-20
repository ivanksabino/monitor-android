package dev.monitorcidadao.app

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** BUG-009: o app usa o adaptive icon próprio (monograma MC), não o robô padrão do sistema. */
@RunWith(RobolectricTestRunner::class)
class IconeDoAppTest {
    @Test
    fun `manifest aponta para o icone do app`() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals(R.mipmap.ic_launcher, app.applicationInfo.icon)
        // Recurso existe e é resolvível (adaptive icon em mipmap-anydpi-v26).
        assertEquals("ic_launcher", app.resources.getResourceEntryName(app.applicationInfo.icon))
        assertEquals("mipmap", app.resources.getResourceTypeName(R.mipmap.ic_launcher_round))
    }
}
