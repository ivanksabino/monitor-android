package dev.monitorcidadao.core.featureflags

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalFeatureFlagsTest {
    @Test
    fun `usa padrao, aceita sobrescrita e volta ao padrao`() = runTest {
        val flags = LocalFeatureFlags()
        flags.observe(FeatureFlag.CompartilharPost).test {
            assertFalse(awaitItem())
            flags.set(FeatureFlag.CompartilharPost, true)
            assertTrue(awaitItem())
            assertTrue(flags.isEnabled(FeatureFlag.CompartilharPost))
            flags.set(FeatureFlag.AbaExplorar, true) // outra flag não emite aqui
            flags.reset(FeatureFlag.CompartilharPost)
            assertFalse(awaitItem())
        }
    }
}
