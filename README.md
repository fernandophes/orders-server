# Serviço: Gerenciador de Ordens de Serviço

Neste repositório, estão contidos os servidores de localização, de proxy e de dados.

## Pré-requisitos
A máquina precisa ter:
- **Java 11** ou superior
- **Maven 3** ou superior

## Instalação e execução
Para instalar e executar o programa, abra um terminal no diretório raiz dele e escolha um dos métodos a seguir.

Você tem duas opções:

### 1ª. Executar no próprio terminal
Instale o serviço usando:
<pre>mvn clean install exec:java</pre>

Com isso, o programa será compilado e já iniciará.

### 2ª. Criar um executável <code>.jar</code> e executá-lo
Compile o programa usando:
<pre>mvn clean install assembly:single</pre>

Dessa forma, o compilador criará 2 arquivos <code>.jar</code> dentro da pasta <code>/target</code>, são eles:

- <code>orders-server-1.0-SNAPSHOT.jar</code>
- <code>orders-server-1.0-SNAPSHOT-jar-with-dependencies.jar</code> _(usar essa)_

Execute essa segunda opção, com sufixo <code>jar-with-dependencies</code>. Pode ser abrindo normalmente no explorador de arquivos, como também usando o comando:
<pre>java -jar target/orders-server-1.0-SNAPSHOT-jar-with-dependencies.jar</pre>

## Uso do programa
Ao iniciar, se abrirá uma pequena janela listando os 3 servidores, seu estado (ligado/desligado) e um botão para ligar ou desligar cada um deles.

Durante o uso, todos os logs do serviço serão registrados em um arquivo <code>server-exec.log</code>, que será criado (ou sobrescrito) automaticamente na mesma pasta do arquivo executável. Tudo em tempo real.

_Sugiro abrir esse arquivo no VS Code, porque a formatação fica melhor._
