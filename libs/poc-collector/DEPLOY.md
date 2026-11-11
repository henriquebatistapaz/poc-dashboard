# Deploy e execução local

CLI Java para coletar links de fontes públicas e da API do Reddit quando autorizada.

## Validação local

Use JDK 27.0.x e Maven 3.9.16. O JAR mantém bytecode Java 21 até a atualização dos consumidores runner e dashboard. Execute os comandos a partir da raiz do repositório.

```sh
mvn clean verify
java -jar target/poc-collector-0.1.0-SNAPSHOT.jar google-news "open data Brazil"
```

## Pendências de publicação

Para distribuição, definir onde publicar o JAR. Para execução agendada, configurar o ambiente e o destino da saída JSON. O [README](README.md) descreve as credenciais e aprovações exigidas por cada fonte.
