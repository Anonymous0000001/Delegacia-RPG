# Delegacia RPG

Protótipo Android 3D feito com LibGDX. A tela inicial mostra o chão da delegacia, o policial e uma viatura.

## Como jogar

- Toque no chão para mover o policial; a câmera acompanha o personagem.
- Toque na viatura para receber R$ 300 por patrulha.
- Toque no quadro Calibre 12 para obter o bônus secreto de dinheiro e sucata.
- A cena 3D usa modelos geométricos temporários, que podem ser substituídos por modelos `.g3db`.
- O HUD exibe o dinheiro e a sucata iniciais.

O protótipo 2D anterior continua no projeto em `GameView`, mas a atividade Android inicia a cena 3D `GameScreen`.

## Executar

Abra este projeto no Android Studio e aguarde a sincronizacao do Gradle. Instale o Android SDK 35 quando solicitado e execute a configuracao `app` em um emulador ou dispositivo Android (API 23 ou superior).