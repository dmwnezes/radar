# Radar

App Android pessoal para acompanhar promoções de jogos em real na PS Store, no Xbox e na Steam.

- **Hoje:** melhores promoções do dia.
- **Radar:** jogos salvos, com preço-alvo calculado pelo histórico e avisos no celular.
- **Jogos:** biblioteca das contas conectadas, com a etiqueta "Já na sua biblioteca".

O preço é verificado uma vez por dia no próprio aparelho. Não há servidor.
O APK sai em [Releases](../../releases/latest) e o app se atualiza sozinho.

## Segurança

- Tokens e chaves ficam só no aparelho, cifrados com AES-256-GCM. A chave fica no Android Keystore.
- Nenhuma credencial fica no repositório ou no APK, e o app não permite cópia de segurança dos dados.
- Cada credencial só vai para os domínios oficiais da própria conta (`Dominios`, em `Contas.kt`).
- A chave de assinatura do APK fica criptografada em `assinatura/`. A senha é o segredo `RADAR_CHAVE` do GitHub Actions.
- O login da PlayStation usa uma API interna da Sony, sem garantia oficial. O código npsso equivale à sua senha e vale cerca de 60 dias.

## Conectar o Xbox

1. Em [portal.azure.com](https://portal.azure.com), registre um app em **App registrations**, para contas pessoais da Microsoft.
2. Adicione a plataforma **Mobile and desktop** com o retorno `com.dmwnezes.radar://xbox`.
3. Cole o **Application (client) ID** no app.

O Xbox informa os jogos já jogados, não as compras.

## Limites

- O histórico da PS Store e da Steam começa quando o jogo entra no radar, porque as lojas não fornecem o passado.
- Sem histórico de promoção, o alvo começa 30% abaixo do preço cheio.
- A busca da PS Store usa os 2.000 jogos de PS5 mais vendidos. Com a conta conectada, ela cobre a loja inteira.

Fontes: Instrument Sans e Space Grotesk, sob a licença SIL OFL (`licencas/`).
