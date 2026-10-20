# Leitor Uber (Android)

Lê a tela do **Uber Motorista** e mostra um painel no topo do celular quando chega uma corrida:

- Ganho por km (já contando a ida até o passageiro)
- Ganho por hora
- Nota do passageiro
- Endereço de partida
- Endereço de chegada

O painel fica **verde** (bom nos dois), **amarelo** (bom em um) ou **vermelho** (ruim nos dois), conforme os mínimos que você definir. O app só lê a tela: não clica, não aceita e não recusa nada, e não envia nada para a internet.

---

## Como instalar — Opção A: Android Studio (no computador)

1. Instale o Android Studio: https://developer.android.com/studio (next, next, finish).
2. Abra o Android Studio → **Open** → escolha a pasta `uber-leitor` (a que contém este arquivo).
3. Espere a barra azul/verde de baixo terminar (a primeira vez demora uns minutos baixando coisas). Se aparecer aviso pedindo para **atualizar** Gradle ou plugin, clique em **Don't remind me again** / ignore.
4. No celular: **Configurações → Sobre o telefone → toque 7 vezes em "Número da versão"**. Volte, entre em **Opções do desenvolvedor** e ligue **Depuração USB**.
5. Ligue o celular no computador pelo cabo e autorize a depuração quando perguntar.
6. No Android Studio, clique no botão verde ▶ (**Run**). O app "Leitor Uber" é instalado no celular.

**Sem cabo:** menu **Build → Build Bundle(s) / APK(s) → Build APK(s)**. Quando terminar, clique em **locate**, pegue o arquivo `app-debug.apk`, mande para o celular (WhatsApp, Drive, e-mail) e toque nele para instalar.

## Opção B: sem instalar nada (GitHub)

1. Crie uma conta grátis em github.com e um repositório novo (**New repository**, qualquer nome).
2. Clique em **uploading an existing file** e arraste **todo o conteúdo** da pasta `uber-leitor` (inclusive a pasta escondida `.github`).
3. Clique em **Commit changes**. Vá na aba **Actions** → **Build APK** → espere ficar verde (uns 3–5 min).
4. Abra a execução e, no final da página, baixe **UberLeitor-apk**. Descompacte e instale o `app-debug.apk` no celular.

---

## Como usar

1. Abra o app **Leitor Uber** → botão **1. Ativar leitura da tela**.
2. Na lista que abrir, toque em **Leitor Uber** e ligue a chave.
   - Se aparecer *"Configuração restrita"* (Android 13 ou mais novo): **Configurações → Apps → Leitor Uber → ⋮ (três pontinhos, canto de cima) → Permitir configurações restritas**. Depois volte e ligue de novo.
3. Volte ao app e coloque seus **mínimos** de R$/km e R$/h → **Salvar mínimos**.
4. Aperte **Testar com corrida de exemplo** para ver o formato do resultado.
5. Abra o **Uber Motorista**, fique online. Quando chegar uma chamada, o painel aparece no topo.

Dica: em celulares Xiaomi, Samsung, Motorola etc., desligue a **economia de bateria** para o Leitor Uber, senão o Android pode desligar a leitura sozinho.

## Se algum campo vier errado ou vazio

Eu montei a leitura sem ter acesso à tela real do seu Uber, então o texto pode vir em outra ordem. Para ajustar:

1. Deixe chegar uma chamada com o Leitor Uber ativado.
2. Abra o Leitor Uber → **Ver texto da última corrida detectada** (ou **Ver último texto lido do Uber**) → **Copiar**.
3. Cole o texto na conversa com o Claude e peça o ajuste.

## Observações

- Só funciona no app **Uber Motorista** (`com.ubercab.driver`).
- Ganho por km e por hora consideram **ida até o passageiro + viagem**. Não descontam combustível, pedágio nem taxas.
- Se o Uber mudar o layout da tela, pode ser preciso ajustar o arquivo `OfferParser.java`.
- Apps de terceiros que leem a tela do aplicativo podem não ser bem vistos pelos termos de uso do Uber; use por sua conta e risco.
