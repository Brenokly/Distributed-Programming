# ☁️ Projeto de Simulação de Coleta de Dados Climáticos Distribuídos

## Disciplina: Programação Concorrente e Distribuída 🌐

### Curso: Ciência da Computação 💻

### Universidade: Ufersa - Universidade Federal Rural do Semi-Árido 🌱

### Ano: 2025 📅

---

## 📖 Sumário

1. [Introdução](#introducao)
2. [Objetivos](#objetivos)
3. [Prática Offline 3 - Arquitetura de Microsserviços](#pratica-offline-3)
    * [Objetivo](#objetivo)
    * [Conceitos-Chave](#conceitos-chave)
    * [Arquitetura do Sistema](#arquitetura-do-sistema)
    * [Fluxo da Simulação](#fluxo-da-simulacao)
    * [Como Executar](#como-executar)
4. [Metodologia](#metodologia)
5. [Estrutura do Projeto](#estrutura-do-projeto)
6. [Referências Bibliográficas](#referencias-bibliograficas)

---

## 📚 Introdução <a id="introducao"></a>

[cite_start]Este projeto é uma simulação de um sistema distribuído para coleta, processamento e gerenciamento de dados climáticos, desenvolvido como atividade da **Prática Offline 3** da disciplina de Programação Concorrente e Distribuída[cite: 5]. [cite_start]O sistema simula um cenário onde Drones atuam como produtores de dados, enviando informações para um Gateway central[cite: 13]. [cite_start]Este Gateway, por sua vez, roteia os dados para diferentes canais de comunicação (MQTT e RabbitMQ) [cite: 36, 66][cite_start], que alimentam uma arquitetura de microsserviços projetada para desacoplar o armazenamento e a visualização dos dados[cite: 67].

---

## 🎯 Objetivos <a id="objetivos"></a>

[cite_start]O objetivo da disciplina é capacitar os alunos na construção de sistemas distribuídos, aplicando conceitos de comunicação entre processos, concorrência, paralelismo e, nesta prática, com foco em uma arquitetura baseada em microsserviços, desacoplamento via mensageria e comunicação síncrona/assíncrona entre serviços[cite: 6, 79].

---

## 🛰️ Prática Offline 3 - Arquitetura de Microsserviços <a id="pratica-offline-3"></a>

### 🎯 Objetivo <a id="objetivo"></a>

[cite_start]Desenvolver uma simulação de um sistema distribuído para a coleta e gerenciamento de dados climáticos[cite: 6]. [cite_start]A arquitetura implementa o padrão de comunicação indireta com brokers de mensagem (MQTT e RabbitMQ) [cite: 66] [cite_start]e desacopla as responsabilidades de armazenamento e apresentação de dados em microsserviços distintos que se comunicam via API REST[cite: 36, 67]. O projeto aplica conceitos de programação reativa (WebFlux), chamadas assíncronas (`CompletableFuture`) e programação funcional.

---

### 🏷️ Conceitos-Chave <a id="conceitos-chave"></a>

* [cite_start]**Microsserviços:** A aplicação é dividida em serviços independentes e especializados (Gateway, Serviço de Armazenamento, API de Dashboard)[cite: 67].
* [cite_start]**Comunicação Indireta:** Desacoplamento espacial e temporal entre os componentes através dos brokers MQTT e RabbitMQ[cite: 66].
* [cite_start]**API REST:** O `Cliente HTTP` consome os dados do dashboard através de uma API REST exposta pelo `Serviço de Dashboard`[cite: 41, 67].
* **Programação Reativa (WebFlux):** A API de Dashboard utiliza o paradigma reativo com `Mono` e `Flux` para lidar com as requisições de forma assíncrona e não-bloqueante.
* **Programação Assíncrona (`CompletableFuture`):** O `Cliente HTTP` utiliza o `HttpClient` moderno do Java com `CompletableFuture` para realizar chamadas não-bloqueantes à API.
* [cite_start]**Programação Concorrente:** Uso de `ExecutorService` no Gateway para processamento paralelo das mensagens recebidas[cite: 61].
* [cite_start]**Programação Funcional:** Amplo uso de Lambdas e Streams para processamento e transformação de dados, principalmente nos cálculos do dashboard[cite: 65].
* [cite_start]**MQTT e RabbitMQ:** Brokers de mensagem utilizados para diferentes propósitos: MQTT para tempo real e telemetria [cite: 49][cite_start], RabbitMQ para entrega confiável de mensagens para serviços de backend[cite: 37].

---

### 🏛️ Arquitetura do Sistema <a id="arquitetura-do-sistema"></a>

O sistema é composto por cinco processos principais que rodam de forma independente.

#### [cite_start]**Drones (Produtores MQTT)** [cite: 30]

* [cite_start]4 drones (Norte, Sul, Leste e Oeste) que geram dados climáticos em formatos distintos[cite: 7, 9, 10, 11, 12].
* Publicam os dados em tópicos MQTT específicos por região: `ufersa/pw/climadata/<regiao>`.

#### [cite_start]**Gateway (Centro Distribuidor)** [cite: 14]

* **Consumidor MQTT:** Inscreve-se no tópico `ufersa/pw/climadata/#` para receber dados de todos os drones.
* **Processador:** Faz o parse dos 4 formatos de dados e os padroniza.
* **Produtor Dual:** Re-publica os dados processados em dois canais:
    * [cite_start]**RabbitMQ:** Para a exchange `climate_data_topic_exchange` com a routing key `dados.climaticos.<regiao>`[cite: 37].
    * [cite_start]**MQTT:** Para o tópico `ufersa/pw/gateway/processed_data/<regiao>`[cite: 49].

#### **Microsserviço de Armazenamento (`DataStorageService`)**

* [cite_start]**Consumidor RabbitMQ:** Ouve a exchange do Gateway para receber todos os dados climáticos[cite: 37].
* [cite_start]**Armazenamento:** Salva os dados recebidos em uma base de dados em memória[cite: 40].
* **API Interna:** Expõe um endpoint REST simples (`/data`) para que outros serviços possam consultar os dados brutos armazenados.

#### **Microsserviço de Dashboard (`DashboardApiService`)**

* **API WebFlux:** Expõe endpoints REST (`/dashboard` e `/dashboard/{region}`) para o cliente final.
* **Orquestrador:** Ao receber uma requisição, ele faz uma chamada HTTP para o `DataStorageService` para obter os dados.
* [cite_start]**Processador de Dashboard:** Com os dados em mãos, ele realiza os cálculos (totais, médias, percentuais) e formata o resultado em JSON[cite: 18, 20, 21, 22, 23, 24, 25].

#### [cite_start]**Clientes (Usuários Finais)** [cite: 56]

* **Cliente HTTP Dashboard (`ClienteHttpDashboard`):**
    * [cite_start]Simula um usuário que consome dados históricos[cite: 41].
    * [cite_start]Possui um menu para o usuário escolher a região desejada[cite: 44].
    * Usa `HttpClient` e `CompletableFuture` para fazer chamadas assíncronas à API do `DashboardApiService`.
    * Formata o JSON recebido em um dashboard de texto legível.

* **Usuário em Tempo Real (`RealTimeUser`):**
    * [cite_start]Consome dados em tempo real diretamente do tópico MQTT do Gateway[cite: 49].
    * [cite_start]Possui um menu para o usuário escolher a região a ser monitorada[cite: 53].
    * Exibe os dados no console assim que chegam e permite gerar dashboards dinâmicos.

---

### 🔄 Fluxo da Simulação <a id="fluxo-da-simulacao"></a>

1.  [cite_start]**Publicação:** Os Drones iniciam e publicam dados brutos via MQTT a cada 2-5 segundos[cite: 29].
2.  **Processamento e Roteamento:** O Gateway consome os dados brutos, os padroniza e os re-publica para o RabbitMQ e para outro tópico MQTT.
3.  **Armazenamento:** O `DataStorageService` consome as mensagens do RabbitMQ e as armazena em sua base de dados em memória.
4.  **Consumo em Tempo Real:** O `RealTimeUser` recebe os dados do Gateway via MQTT e os exibe instantaneamente.
5.  **Consumo Sob Demanda:** O `ClienteHttpDashboard` solicita um dashboard. A requisição bate no `DashboardApiService`, que por sua vez busca os dados no `DataStorageService`, calcula as métricas e retorna o dashboard formatado para o cliente.

---

### 🚀 Como Executar <a id="como-executar"></a>

1.  **Pré-requisito:** Inicie o broker RabbitMQ via Docker.
    ```bash
    docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management
    ```
2.  **Compile o Projeto:**
    ```bash
    mvn clean package
    ```
3.  **Execute os Componentes (cada um em um terminal/console separado):** A ordem é importante.
    * **1º Gateway:** `Gateway.java`
    * **2º Serviço de Armazenamento:** `DataStorageApplication.java` (o consumidor RabbitMQ com API).
    * **3º Serviço de Dashboard:** `ServicoDashboardApplication.java` (a API WebFlux).
    * **4º Drones:** Execute um ou mais drones (`DroneNorte.java`, `DroneSul.java`, etc.).
    * [cite_start]**5º Usuários (após 10s)**[cite: 57]: `ClienteHttpDashboard.java` e/ou `RealTimeUserLauncher.java`.

---

## 🏫 Metodologia <a id="metodologia"></a>

* **Técnicas:** Desenvolvimento de um sistema distribuído baseado em microsserviços com comunicação desacoplada via mensageria e comunicação síncrona via API REST.
* **Tecnologias:** Java 23, Paho MQTT, RabbitMQ AMQP Client, Spring Boot, Spring WebFlux, Java `HttpClient`, `CompletableFuture`, Threads, `ExecutorService`, Lambdas, Streams, Maven, SLF4J.
* [cite_start]**Avaliação:** Entregas práticas, demonstração em múltiplas máquinas e qualidade de código[cite: 71, 72].

---

## 📂 Estrutura do Projeto <a id="estrutura-do-projeto"></a>

````

📁 climate-data-project
├── 📁 src
│   ├── 📁 main
│   │   ├── 📁 java
│   │   │   └── 📁 com/climate/data
│   │   │       ├── 📁 client
│   │   │       │   └── 📄 ClienteHttpDashboard.java
│   │   │       ├── 📁 config
│   │   │       │   └── 📄 SharedConfig.java
│   │   │       ├── 📁 dashboard\_api
│   │   │       │   ├── 📄 DashboardController.java
│   │   │       │   └── 📄 ServicoDashboardApplication.java
│   │   │       ├── 📁 drone
│   │   │       │   ├── 📄 Drone.java
│   │   │       │   └── 📁 execute
│   │   │       │       └── 📄 DroneNorte.java, ...
│   │   │       ├── 📁 gateway
│   │   │       │   └── 📄 Gateway.java
│   │   │       ├── 📁 realtime\_user
│   │   │       │   ├── 📄 RealTimeUser.java
│   │   │       │   └── 📁 execute
│   │   │       │       └── 📄 RealTimeUserLauncher.java
│   │   │       ├── 📁 storage\_service
│   │   │       │   ├── 📄 DataQueryController.java
│   │   │       │   ├── 📄 RabbitMQConsumerService.java
│   │   │       │   └── 📄 DataStorageApplication.java
│   │   │       └── 📁 utils
│   │   │           └── 📄 ClimateData.java, ...
│   │   └── 📁 resources
│   │       └── 📄 logback.xml
├── 📄 README.md
└── 📄 pom.xml

```

---

## 📚 Referências Bibliográficas <a id="referencias-bibliograficas"></a>

### 📖 Obrigatórias:

* Coulouris, George. *Sistemas distribuídos: conceitos e projeto*. Bookman, 2013.
* Documentação oficial do RabbitMQ e do protocolo MQTT.

---
