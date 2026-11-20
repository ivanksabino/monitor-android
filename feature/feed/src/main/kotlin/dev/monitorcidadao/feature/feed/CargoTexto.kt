package dev.monitorcidadao.feature.feed

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.monitorcidadao.core.designsystem.R as DsR
import dev.monitorcidadao.domain.Cargo

/**
 * Cargo tipado → texto localizado (os textos são do DesignSystem, o mapeamento é da feature: o DS
 * não conhece o Domain nem o contrato da API). `when` exaustivo: cargo novo no Domain não compila
 * sem texto. Mesmo mapeamento nas features que mostram pessoa (feature não importa feature).
 */
@StringRes
internal fun Cargo.rotulo(): Int = when (this) {
    Cargo.DeputadaFederal -> DsR.string.ds_cargo_deputada_federal
    Cargo.DeputadoFederal -> DsR.string.ds_cargo_deputado_federal
    Cargo.Senadora -> DsR.string.ds_cargo_senadora
    Cargo.Senador -> DsR.string.ds_cargo_senador
}

@Composable
internal fun Cargo.texto(): String = stringResource(rotulo())
