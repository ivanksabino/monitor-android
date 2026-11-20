# :core:designsystem

Tokens do canvas Híbrido (`contracts/design/tokens.json`), componentes Compose com a semântica de
`contracts/a11y-nativo.md` e o renderer de post (casca + blocos).

## Nomes iOS ↔ Android

Arquitetura §0: mesmos nomes de tipo nos dois lados. Os componentes já batem. Os tokens e os
formatadores ainda divergem, e o dicionário final é decisão do orquestrador para os dois apps.
Até lá, esta tabela é o mapa:

| Conceito | iOS (`DesignSystem`) | Android (`:core:designsystem`) | Situação |
|---|---|---|---|
| Renderer de post (arquitetura §7 chama de `PostRenderer`) | `PostCard`, `GenericPostCard`, `PostCardModel` | `PostCard`, `GenericPostCard`, `PostCardModel` | igual nos dois |
| Botões | `PrimaryButton`, `SecondaryButton`, `ToggleButton`, `CircleIconButton` | iguais | igual |
| Abas, chip, card de número | `SegmentedTabs`/`SectionTabs`, `Chip`, `StatCard` | iguais | igual |
| Cores | `DSColor` | `MonitorColors` (`Monitor.colors`) | divergente, pendente |
| Espaçamento | `DSSpace` | `MonitorSpacing` | divergente, pendente |
| Raios | `DSRadius` | `MonitorRadius` | divergente, pendente |
| Alvo de toque | `DSTouch.minimum` (44 pt) | `MonitorTouch.minTarget` (48 dp) | divergente, pendente; valor difere de propósito por plataforma |
| Tipografia | `DSTextStyle` | `MonitorType` | divergente, pendente |
| Fontes | `DSFonts` | `MonitorFonts` | divergente, pendente |
| Datas, dinheiro e presença | `FormatoData`, `FormatoDinheiro`, `FormatoPresenca` | `MonitorFormat` (uma classe; precisa de `Resources` para as strings localizadas) | divergente, pendente |

Diferenças de propósito, por idioma da plataforma:

- `MonitorFormat` é uma instância (lida de `Resources`, lembrada por configuração com
  `rememberMonitorFormat()`), porque as strings e os plurais vêm de `res/values`. No iOS são `enum`s estáticos.
- `Monitor.colors` é um `CompositionLocal` (claro/escuro trocam a instância inteira). No iOS, cada
  `ColorToken` resolve claro/escuro sozinho.

## Tokens fora do contrato

`avatarBg`, `avatarInk`, `avatarBorder`, `overlayButtonBg` e `overlayButtonInk` vêm do protótipo
e não estão em `contracts/design/tokens.json`. Ficam em `MonitorColors` para nenhum componente ter
cor fixa no código, mas o contraste ainda não foi medido no contrato (inclusão pedida).

## Previews

`preview/MonitorPreviews.kt` (anotação e superfície de preview) fica em `main` para as features
usarem. Os previews dos componentes, com conteúdo fictício, ficam em `src/debug` e não vão para o release.
