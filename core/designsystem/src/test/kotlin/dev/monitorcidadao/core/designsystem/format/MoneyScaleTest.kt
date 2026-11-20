package dev.monitorcidadao.core.designsystem.format

import dev.monitorcidadao.core.designsystem.format.MoneyScale.Unidade
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

internal class MoneyScaleTest {
    @ParameterizedTest(name = "{0} centavos → {1} {2}")
    @CsvSource(
        "0, 0, Reais",
        "100, 1, Reais",
        "95049, 950, Reais",
        "99960, 1, Mil", // 999,60 arredonda para 1.000 reais e sobe de unidade
        "850000, 8.5, Mil",
        "11234018, 112, Mil",
        "99999999, 1, Milhao", // 999.999,99 → 1.000 mil → 1 milhão
        "100000000, 1, Milhao",
        "120000000, 1.2, Milhao",
        "310000000, 3.1, Milhao",
        "840000000, 8.4, Milhao",
        "12340000000, 123, Milhao",
        "250000000000, 2.5, Bilhao",
        "-11234018, -112, Mil",
    )
    internal fun `escala com o mesmo arredondamento para visual e fala`(centavos: Long, valor: String, unidade: Unidade) {
        val escalado = MoneyScale.of(centavos)
        assertEquals(unidade, escalado.unidade)
        assertEquals(0, BigDecimal(valor).compareTo(escalado.valor), "valor ${escalado.valor}")
    }
}
