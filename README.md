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

Este projeto é uma simulação de um sistema distribuído para coleta, processamento e gerenciamento de dados climáticos, desenvolvido como atividade da **Prática Offline 3** da disciplina de Programação Concorrente e Distribuída. O sistema simula um cenário onde Drones atuam como produtores de dados, enviando informações para um Gateway central. Este Gateway, por sua vez, roteia os dados para diferentes canais de comunicação (MQTT e RabbitMQ), que alimentam uma arquitetura de microsserviços projetada para desacoplar o armazenamento e a visualização dos dados.

---

## 🎯 Objetivos <a id="objetivos"></a>

O objetivo da disciplina é capacitar os alunos na construção de sistemas distribuídos, aplicando conceitos de comunicação entre processos, concorrência, paralelismo e, nesta prática, com foco em uma arquitetura baseada em microsserviços, desacoplamento via mensageria e comunicação síncrona/assíncrona entre serviços.

---

## 🛰️ Prática Offline 3 - Arquitetura de Microsserviços <a id="pratica-offline-3"></a>

### 🎯 Objetivo <a id="objetivo"></a>

Desenvolver uma simulação de um sistema distribuído para a coleta e gerenciamento de dados climáticos. A arquitetura implementa o padrão de comunicação indireta com brokers de mensagem (MQTT e RabbitMQ) e desacopla as responsabilidades de armazenamento e apresentação de dados em microsserviços distintos que se comunicam via API REST. O projeto aplica conceitos de programação reativa (WebFlux), chamadas assíncronas (`CompletableFuture`) e programação funcional.

---

### 🏷️ Conceitos-Chave <a id="conceitos-chave"></a>

* **Microsserviços:** A aplicação é dividida em serviços independentes e especializados (Gateway, Serviço de Armazenamento, API de Dashboard).
* **Comunicação Indireta:** Desacoplamento espacial e temporal entre os componentes através dos brokers MQTT e RabbitMQ.
* **API REST:** O `Cliente HTTP` consome os dados do dashboard através de uma API REST exposta pelo `Serviço de Dashboard`.
* **Programação Reativa (WebFlux):** A API de Dashboard utiliza o paradigma reativo com `Mono` e `Flux` para lidar com as requisições de forma assíncrona e não-bloqueante.
* **Programação Assíncrona (`CompletableFuture`):** O `Cliente HTTP` utiliza o `HttpClient` moderno do Java com `CompletableFuture` para realizar chamadas não-bloqueantes à API.
* **Programação Concorrente:** Uso de `ExecutorService` no Gateway para processamento paralelo das mensagens recebidas.
* **Programação Funcional:** Amplo uso de Lambdas e Streams para processamento e transformação de dados, principalmente nos cálculos do dashboard.
* **MQTT e RabbitMQ:** Brokers de mensagem utilizados para diferentes propósitos: MQTT para tempo real e telemetria , RabbitMQ para entrega confiável de mensagens para serviços de backend.

---

### 🏛️ Arquitetura do Sistema <a id="arquitetura-do-sistema"></a>

O sistema é composto por cinco processos principais que rodam de forma independente.

#### **Drones (Produtores MQTT)** 

* 4 drones (Norte, Sul, Leste e Oeste) que geram dados climáticos em formatos distintos.
* Publicam os dados em tópicos MQTT específicos por região: `ufersa/pw/climadata/<regiao>`.

#### **Gateway (Centro Distribuidor)** 

* **Consumidor MQTT:** Inscreve-se no tópico `ufersa/pw/climadata/#` para receber dados de todos os drones.
* **Processador:** Faz o parse dos 4 formatos de dados e os padroniza.
* **Produtor Dual:** Re-publica os dados processados em dois canais:
    * **RabbitMQ:** Para a exchange `climate_data_topic_exchange` com a routing key `dados.climaticos.<regiao>`.
    * **MQTT:** Para o tópico `ufersa/pw/gateway/processed_data/<regiao>`.

#### **Microsserviço de Armazenamento (`DataStorageService`)**

* **Consumidor RabbitMQ:** Ouve a exchange do Gateway para receber todos os dados climáticos.
* **Armazenamento:** Salva os dados recebidos em uma base de dados em memória.
* **API Interna:** Expõe um endpoint REST simples (`/data`) para que outros serviços possam consultar os dados brutos armazenados.

#### **Microsserviço de Dashboard (`DashboardApiService`)**

* **API WebFlux:** Expõe endpoints REST (`/dashboard` e `/dashboard/{region}`) para o cliente final.
* **Orquestrador:** Ao receber uma requisição, ele faz uma chamada HTTP para o `DataStorageService` para obter os dados.
* **Processador de Dashboard:** Com os dados em mãos, ele realiza os cálculos (totais, médias, percentuais) e formata o resultado em JSON.

#### **Clientes (Usuários Finais)** 

* **Cliente HTTP Dashboard (`ClienteHttpDashboard`):**
    * Simula um usuário que consome dados históricos.
    * Possui um menu para o usuário escolher a região desejada.
    * Usa `HttpClient` e `CompletableFuture` para fazer chamadas assíncronas à API do `DashboardApiService`.
    * Formata o JSON recebido em um dashboard de texto legível.

* **Usuário em Tempo Real (`RealTimeUser`):**
    * Consome dados em tempo real diretamente do tópico MQTT do Gateway.
    * Possui um menu para o usuário escolher a região a ser monitorada.
    * Exibe os dados no console assim que chegam e permite gerar dashboards dinâmicos.

---

### 🔄 Fluxo da Simulação <a id="fluxo-da-simulacao"></a>

1.  **Publicação:** Os Drones iniciam e publicam dados brutos via MQTT a cada 2-5 segundos.
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
    * **5º Usuários (após 10s)**: `ClienteHttpDashboard.java` e/ou `RealTimeUserLauncher.java`.

---

## 🏫 Metodologia <a id="metodologia"></a>

* **Técnicas:** Desenvolvimento de um sistema distribuído baseado em microsserviços com comunicação desacoplada via mensageria e comunicação síncrona via API REST.
* **Tecnologias:** Java 23, Paho MQTT, RabbitMQ AMQP Client, Spring Boot, Spring WebFlux, Java `HttpClient`, `CompletableFuture`, Threads, `ExecutorService`, Lambdas, Streams, Maven, SLF4J.
* **Avaliação:** Entregas práticas, demonstração em múltiplas máquinas e qualidade de código.

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
