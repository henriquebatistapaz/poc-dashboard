# Test plan — Java 27 no poc-dashboard

## Execução candidata

- Ambiente: OpenJDK Temurin 27.0.1, Maven 3.9.16; repositório Maven isolado em `/tmp/m2-poc-collector-java27-candidate`.
- Comando: `mvn -B clean verify`; para artefatos consumidores, instalar produtoras em ordem e verificar o build consumidor.
- Dados e serviços reais: não utilizados. Docker/PostgreSQL não disponíveis no host atual.

## Estado observado

Atendido em parte: JDK 27.0.1 / Maven 3.9.16, Spring Boot 4.1.2, springdoc 3.2.0 e Lombok 1.18.48. As três bibliotecas candidatas foram instaladas de `libs/`; `clean verify` passou com 39 testes. Dockerfile e CI apontam para JDK 27. O perfil PostgreSQL descartável e a construção da imagem aguardam Docker. Smoke funcional local com H2 sintético passou: `/v3/api-docs` retornou 200 e `/` sem sessão retornou 401. A interface passou por conferência visual no navegador com login sintético.

## Gates que faltam

- Repetir o perfil de integração PostgreSQL com Docker ativo.
- Concluir as verificações AC-03/AC-04 da spec, incluindo exposição de portas/binds e retorno JDK 21 quando aplicáveis.
- Para o dashboard: construir a imagem de produção e executar smoke funcional/visual em navegador.


A conferência visual foi feita com o dashboard e Vite locais; a sessão e os dados eram sintéticos. O ambiente de produção Railway não foi acessado nem alterado.
